package app.overbay.swiftfox.engine

import android.content.Intent
import android.webkit.PermissionRequest
import app.overbay.swiftfox.SwiftfoxApp
import app.overbay.swiftfox.data.db.SiteOverrideEntity
import app.overbay.swiftfox.data.db.UserscriptEntity
import app.overbay.swiftfox.data.db.ZappedRuleEntity
import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.features.adblock.CosmeticPayload
import app.overbay.swiftfox.features.darkreader.DarkReaderConfig
import app.overbay.swiftfox.features.darkreader.ResolvedWebDarkState
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.features.userscripts.UserscriptParser
import app.overbay.swiftfox.features.userscripts.UserscriptRunAt
import app.overbay.swiftfox.features.userscripts.UserscriptUrlMatcher

data class PageStartInjectionPayload(
    val cosmetic: CosmeticPayload,
    val zappedRules: List<ZappedRuleEntity>,
    val zappedCss: String,
    val gmShimInitJs: String,
    val docStartSnippets: List<String>,
    val docStartUserscripts: List<Pair<UserscriptEntity, Map<String, String>>>
)

data class PageFinishInjectionPayload(
    val cosmetic: CosmeticPayload,
    val zappedRules: List<ZappedRuleEntity>,
    val docEndUserscripts: List<Pair<UserscriptEntity, Map<String, String>>>,
    val docIdleUserscripts: List<Pair<UserscriptEntity, Map<String, String>>>
)

/**
 * Shared engine policy & script payload coordinator consumed by both the main browser
 * (`BrowserViewModel`) and standalone PWAs (`WebAppViewModel`) so adblock, Dark Reader,
 * Element Zapper, Userscripts, and per-site permissions share identical behavior.
 */
class SessionEngineCoordinator(
    private val app: SwiftfoxApp
) {
    fun resolveWebSurfaceDarkColors(darkState: ResolvedWebDarkState): Pair<Boolean, Boolean> =
        darkState.enabled to (darkState.enabled && darkState.amoled)

    fun buildGlobalPrepaintDarkConfig(
        settings: BrowserSettings,
        isChromeDark: Boolean,
        siteOverrides: Map<String, SiteOverrideEntity>,
        privateSiteOverrides: Map<String, SiteOverrideEntity> = emptyMap(),
        isPrivate: Boolean = false
    ): GlobalPrepaintDarkConfig {
        val globalDark = DarkReaderConfig.resolve(
            settings = settings,
            siteOverride = null,
            isChromeDark = isChromeDark
        )
        val effectiveOverrides = if (isPrivate) {
            siteOverrides + privateSiteOverrides
        } else {
            siteOverrides
        }
        val excluded = mutableSetOf<String>()
        val included = mutableSetOf<String>()
        effectiveOverrides.forEach { (hostKey, entity) ->
            val normalized = OmniboxEngine.normalizeHost(hostKey.ifBlank { entity.host })
            if (normalized.isNotBlank()) {
                when (entity.webDarkModeOverride) {
                    false -> excluded += normalized
                    true -> included += normalized
                    null -> Unit
                }
            }
        }
        return GlobalPrepaintDarkConfig(
            amoled = settings.darkReaderAmoled,
            brightness = settings.darkReaderBrightness,
            contrast = settings.darkReaderContrast,
            sepia = settings.darkReaderSepia,
            skipNativeDark = settings.darkReaderSkipNativeDark,
            globalEnabled = globalDark.enabled,
            excludedHosts = excluded,
            includedHosts = included
        )
    }

    fun shouldBlockNetworkRequest(
        requestUrl: String,
        pageUrl: String,
        headers: Map<String, String>,
        globalEnabled: Boolean,
        siteDisabled: Boolean
    ): Boolean {
        if (!globalEnabled || siteDisabled) return false
        val resourceType = app.adblockEngine.inferResourceType(requestUrl, headers)
        return app.adblockEngine.shouldBlock(
            requestUrl = requestUrl,
            pageUrl = pageUrl,
            resourceType = resourceType,
            globalEnabled = true,
            siteDisabled = false
        )
    }

    suspend fun preparePageStartedPayload(
        url: String,
        tokens: BridgeTokenSet,
        settings: BrowserSettings,
        siteAdblockDisabled: Boolean,
        zappedRules: List<ZappedRuleEntity>,
        mergePrivateStorage: (List<Pair<UserscriptEntity, Map<String, String>>>) -> List<Pair<UserscriptEntity, Map<String, String>>> = { it }
    ): PageStartInjectionPayload {
        val cosmetic = app.adblockEngine.cosmeticForUrl(
            url = url,
            globalEnabled = settings.adblockEnabled,
            cosmeticEnabled = settings.adblockCosmeticEnabled,
            siteDisabled = siteAdblockDisabled
        )
        val rawOriginDocStart = if (settings.userscriptsEnabled) {
            app.userscriptRepository.originCandidateDocumentStartScriptsFor(url)
        } else {
            emptyList()
        }
        val originDocStart = mergePrivateStorage(rawOriginDocStart)
        val docStartUserscripts = if (originDocStart.isEmpty()) {
            emptyList()
        } else {
            originDocStart.filter { (script, _) ->
                UserscriptUrlMatcher.matches(
                    url,
                    UserscriptParser.decodeStringList(script.matchesJson),
                    UserscriptParser.decodeStringList(script.excludesJson)
                )
            }
        }
        val gmShimInitJs = if (originDocStart.isNotEmpty()) {
            app.userscriptBridge.buildShimInitScript(tokens.gmToken, tokens.gmKey)
        } else {
            ""
        }
        val docStartSnippets = app.userscriptBridge.buildPerScriptSnippets(
            scriptsWithStorage = originDocStart,
            runAt = UserscriptRunAt.DOCUMENT_START,
            bridgeToken = tokens.gmToken,
            runtimeKey = tokens.gmKey,
            includeUrlGuard = true
        )
        val zappedCss = app.zapperController.buildCssForRules(zappedRules)
        return PageStartInjectionPayload(
            cosmetic = cosmetic,
            zappedRules = zappedRules,
            zappedCss = zappedCss,
            gmShimInitJs = gmShimInitJs,
            docStartSnippets = docStartSnippets,
            docStartUserscripts = docStartUserscripts
        )
    }

    suspend fun preparePageFinishedPayload(
        url: String,
        settings: BrowserSettings,
        siteAdblockDisabled: Boolean,
        zappedRules: List<ZappedRuleEntity>,
        mergePrivateStorage: (List<Pair<UserscriptEntity, Map<String, String>>>) -> List<Pair<UserscriptEntity, Map<String, String>>> = { it }
    ): PageFinishInjectionPayload {
        val cosmetic = app.adblockEngine.cosmeticForUrl(
            url = url,
            globalEnabled = settings.adblockEnabled,
            cosmeticEnabled = settings.adblockCosmeticEnabled,
            siteDisabled = siteAdblockDisabled
        )
        val rawDocEnd = if (settings.userscriptsEnabled) {
            app.userscriptRepository.matchingScriptsFor(url, UserscriptRunAt.DOCUMENT_END)
        } else {
            emptyList()
        }
        val rawDocIdle = if (settings.userscriptsEnabled) {
            app.userscriptRepository.matchingScriptsFor(url, UserscriptRunAt.DOCUMENT_IDLE)
        } else {
            emptyList()
        }
        return PageFinishInjectionPayload(
            cosmetic = cosmetic,
            zappedRules = zappedRules,
            docEndUserscripts = mergePrivateStorage(rawDocEnd),
            docIdleUserscripts = mergePrivateStorage(rawDocIdle)
        )
    }

    fun resolvePermissionPolicyDecision(
        prompt: SitePermissionPrompt,
        saved: SiteOverrideEntity?
    ): Boolean? {
        if (saved == null) return null
        if (prompt.isGeolocation) {
            return saved.allowLocation
        }
        val allApproved = prompt.resources.all { res ->
            when (res) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> saved.allowCamera == true
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> saved.allowMic == true
                PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> saved.allowProtectedMedia == true
                else -> false
            }
        }
        if (allApproved) return true
        val anyDenied = prompt.resources.any { res ->
            when (res) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> saved.allowCamera == false
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> saved.allowMic == false
                PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> saved.allowProtectedMedia == false
                else -> false
            }
        }
        return if (anyDenied) false else null
    }
}
