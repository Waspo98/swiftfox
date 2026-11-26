package app.overbay.swiftfox.features.webapps

import android.content.Context
import android.webkit.CookieManager
import app.overbay.swiftfox.data.db.WebAppDao
import app.overbay.swiftfox.data.db.WebAppEntity
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID

/**
 * Coordinates manifest retrieval, Room persistence, local icon storage, and launcher shortcut
 * lifecycle for installed PWAs and home-screen web shortcuts.
 */
class WebAppRepository(
    private val context: Context,
    private val webAppDao: WebAppDao,
    private val httpClientProvider: () -> OkHttpClient,
    val iconHelper: WebAppIconHelper,
    private val isCustomIconReferenced: suspend (String) -> Boolean = { false }
) {

    fun observeAll(): Flow<List<WebAppEntity>> = webAppDao.observeAll()

    suspend fun getById(id: String): WebAppEntity? = webAppDao.getById(id)

    suspend fun markLaunched(id: String) {
        webAppDao.updateLastLaunched(id, System.currentTimeMillis())
    }

    suspend fun resolveInstallCandidate(
        meta: PageWebAppMetadata,
        hasBeforeInstallPrompt: Boolean = false
    ): PageInstallCandidate {
        val manifestJson = meta.inlineManifestJson ?: meta.manifestUrl?.let { url ->
            fetchManifestText(url, meta.pageUrl)
        }
        val parsed = if (!manifestJson.isNullOrBlank()) {
            WebAppManifestParser.parseManifest(
                rawJson = manifestJson,
                manifestUrl = meta.manifestUrl,
                pageUrl = meta.pageUrl,
                fallbackTitle = meta.pageTitle
            )
        } else {
            null
        }
        val candidate = WebAppManifestParser.buildInstallCandidate(
            meta = meta,
            manifest = parsed,
            hasBeforeInstallPrompt = hasBeforeInstallPrompt
        )
        val existing = webAppDao.findByStartUrl(candidate.startUrl)
            ?: webAppDao.findByStartUrl(meta.pageUrl)
        return candidate.copy(existingInstalledAppId = existing?.id)
    }

    suspend fun installOrUpdateFromCandidate(
        candidate: PageInstallCandidate,
        customName: String,
        openAsStandalone: Boolean,
        showSlimHeader: Boolean,
        drawBehindStatusBar: Boolean = false,
        customIconSpec: String?,
        pinToHomeScreen: Boolean = true
    ): Result<Pair<WebAppEntity, Boolean>> = runCatching {
        val existing = candidate.existingInstalledAppId?.let { webAppDao.getById(it) }
            ?: webAppDao.findByStartUrl(candidate.startUrl)
        val id = existing?.id ?: "pwa_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val cleanName = customName.trim().ifBlank { candidate.suggestedName }.ifBlank { "Web App" }
        val shortName = if (cleanName == candidate.suggestedName) {
            candidate.suggestedShortName.ifBlank { cleanName }
        } else {
            cleanName
        }
        val normalizedHost = OmniboxEngine.normalizeHost(OmniboxEngine.extractHost(candidate.startUrl))
            .ifBlank { OmniboxEngine.extractHost(candidate.pageUrl) }

        val iconUrlChanged = existing != null && existing.iconUrl != candidate.bestIconUrl
        val resolvedBitmap = iconHelper.resolveDisplayBitmap(
            appId = id,
            iconPath = if (iconUrlChanged) null else existing?.iconPath,
            iconUrl = candidate.bestIconUrl,
            pageUrl = candidate.startUrl,
            customIconSpec = customIconSpec,
            fallbackLabel = cleanName,
            backgroundColorHex = candidate.backgroundColorHex ?: candidate.themeColorHex
        )
        val savedIconPath = if (customIconSpec.isNullOrBlank()) {
            iconHelper.saveAppIconBitmap(id, resolvedBitmap) ?: existing?.iconPath
        } else {
            existing?.iconPath
        }

        val now = System.currentTimeMillis()
        val normalizedCustomIcon = customIconSpec?.takeIf { it.isNotBlank() }
        val entity = WebAppEntity(
            id = id,
            name = cleanName,
            shortName = shortName,
            startUrl = candidate.startUrl,
            scopeUrl = candidate.scopeUrl,
            manifestUrl = candidate.manifestUrl,
            host = normalizedHost,
            displayMode = candidate.displayMode.wireValue,
            openAsStandalone = openAsStandalone,
            showSlimHeader = showSlimHeader,
            drawBehindStatusBar = drawBehindStatusBar,
            themeColorHex = candidate.themeColorHex,
            backgroundColorHex = candidate.backgroundColorHex,
            iconUrl = candidate.bestIconUrl,
            iconPath = savedIconPath,
            iconMaskable = candidate.bestIconMaskable,
            customIcon = normalizedCustomIcon,
            shortcutsJson = WebAppManifestParser.encodeShortcutsJson(candidate.shortcuts),
            isPwaCapable = candidate.isStandaloneCapable,
            pinnedShortcutId = existing?.pinnedShortcutId ?: "sf_webapp_$id",
            createdAt = existing?.createdAt ?: now,
            lastLaunchedAt = now
        )
        webAppDao.upsert(entity)
        if (existing?.customIcon != null && existing.customIcon != normalizedCustomIcon) {
            cleanupCustomImageIfUnused(existing.customIcon)
        }
        val pinned = if (pinToHomeScreen) {
            WebAppShortcutManager.requestPinShortcut(context, entity, resolvedBitmap, iconHelper)
        } else {
            WebAppShortcutManager.updatePinnedShortcut(context, entity, resolvedBitmap, iconHelper)
            true
        }
        entity to pinned
    }

    suspend fun updateExistingWebApp(
        existing: WebAppEntity,
        name: String,
        openAsStandalone: Boolean,
        showSlimHeader: Boolean,
        drawBehindStatusBar: Boolean = existing.drawBehindStatusBar,
        customIconSpec: String?
    ): WebAppEntity {
        val cleanName = name.trim().ifBlank { existing.name }
        val updated = existing.copy(
            name = cleanName,
            shortName = cleanName,
            openAsStandalone = openAsStandalone,
            showSlimHeader = showSlimHeader,
            drawBehindStatusBar = drawBehindStatusBar,
            customIcon = customIconSpec?.takeIf { it.isNotBlank() }
        )
        webAppDao.upsert(updated)
        if (existing.customIcon != updated.customIcon) {
            cleanupCustomImageIfUnused(existing.customIcon)
        }
        val bmp = iconHelper.resolveDisplayBitmap(
            appId = updated.id,
            iconPath = updated.iconPath,
            iconUrl = updated.iconUrl,
            pageUrl = updated.startUrl,
            customIconSpec = updated.customIcon,
            fallbackLabel = updated.name,
            backgroundColorHex = updated.backgroundColorHex ?: updated.themeColorHex
        )
        WebAppShortcutManager.updatePinnedShortcut(context, updated, bmp, iconHelper)
        return updated
    }

    suspend fun repinWebApp(webApp: WebAppEntity): Boolean {
        val bmp = iconHelper.resolveDisplayBitmap(
            appId = webApp.id,
            iconPath = webApp.iconPath,
            iconUrl = webApp.iconUrl,
            pageUrl = webApp.startUrl,
            customIconSpec = webApp.customIcon,
            fallbackLabel = webApp.name,
            backgroundColorHex = webApp.backgroundColorHex ?: webApp.themeColorHex
        )
        return WebAppShortcutManager.requestPinShortcut(context, webApp, bmp, iconHelper)
    }

    suspend fun removeWebApp(webApp: WebAppEntity) {
        webAppDao.deleteById(webApp.id)
        iconHelper.deleteCachedIconFile(webApp.id, webApp.iconPath)
        cleanupCustomImageIfUnused(webApp.customIcon)
        WebAppShortcutManager.disablePinnedShortcut(context, webApp.pinnedShortcutId)
    }

    private suspend fun cleanupCustomImageIfUnused(spec: String?) {
        if (spec.isNullOrBlank()) return
        if (!isCustomIconReferenced(spec)) {
            iconHelper.deleteCustomImageIfUnused(spec)
        }
    }

    private suspend fun fetchManifestText(manifestUrl: String, pageUrl: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val cookie = runCatching {
                    CookieManager.getInstance().getCookie(manifestUrl)
                        ?: CookieManager.getInstance().getCookie(pageUrl)
                }.getOrNull()
                val req = Request.Builder()
                    .url(manifestUrl)
                    .header("Accept", "application/manifest+json, application/json;q=0.9, */*;q=0.8")
                    .apply {
                        if (!cookie.isNullOrBlank()) {
                            header("Cookie", cookie)
                        }
                    }
                    .build()
                httpClientProvider().newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val body = resp.body
                    val bytes = body.bytes()
                    if (bytes.isEmpty() || bytes.size > MAX_MANIFEST_BYTES) return@runCatching null
                    bytes.toString(Charsets.UTF_8)
                }
            }.getOrNull()
        }

    companion object {
        private const val MAX_MANIFEST_BYTES = 256 * 1024
    }
}
