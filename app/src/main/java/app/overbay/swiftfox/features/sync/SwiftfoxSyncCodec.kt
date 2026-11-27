package app.overbay.swiftfox.features.sync

import app.overbay.swiftfox.data.db.EssentialEntity
import app.overbay.swiftfox.data.db.FilterListEntity
import app.overbay.swiftfox.data.db.PinnedFolderEntity
import app.overbay.swiftfox.data.db.SiteOverrideEntity
import app.overbay.swiftfox.data.db.SpaceEntity
import app.overbay.swiftfox.data.db.TabEntity
import app.overbay.swiftfox.data.db.UserscriptEntity
import app.overbay.swiftfox.data.db.ZappedRuleEntity
import app.overbay.swiftfox.data.prefs.AccentPreset
import app.overbay.swiftfox.data.prefs.AppIconBackgroundMode
import app.overbay.swiftfox.data.prefs.AppIconColorStyle
import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.data.prefs.ExternalAppLinkPolicy
import app.overbay.swiftfox.data.prefs.NightSurfaceStyle
import app.overbay.swiftfox.data.prefs.PostureProfile
import app.overbay.swiftfox.data.prefs.ReaderFontFamily
import app.overbay.swiftfox.data.prefs.ReaderSurfaceMode
import app.overbay.swiftfox.data.prefs.SearchEngineId
import app.overbay.swiftfox.data.prefs.SiteMode
import app.overbay.swiftfox.data.prefs.TabAutoClearDuration
import app.overbay.swiftfox.data.prefs.ThemeMode
import app.overbay.swiftfox.data.prefs.ToolbarPlacement
import app.overbay.swiftfox.data.prefs.WebDarkModePreference
import app.overbay.swiftfox.features.foldable.SiteModeResolver
import app.overbay.swiftfox.features.icons.CustomIconSpec
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.features.userscripts.UserscriptParser
import app.overbay.swiftfox.features.userscripts.UserscriptRunAt
import app.overbay.swiftfox.features.userscripts.UserscriptUrlMatcher
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.roundToInt

/**
 * Codec for Swiftfox-only encrypted Sync 1.5 records stored in the dedicated `swiftfox` collection.
 * Completely isolated from Firefox (`bookmarks`, `history`, `tabs`, `prefs`) and Zen (`spaces`)
 * so settings, site overrides, zapper rules, filter lists, userscripts, and local Spaces/Essentials
 * sync cleanly only across Swiftfox clients.
 */
object SwiftfoxSyncCodec {

    const val SWIFTFOX_COLLECTION = "swiftfox"
    const val SCHEMA_VERSION = 1
    const val SETTINGS_BSO_ID = "swiftfox_settings"

    const val KIND_SETTINGS = "settings"
    const val KIND_SITE_OVERRIDE = "site_override"
    const val KIND_ZAPPED_RULE = "zapped_rule"
    const val KIND_FILTER_LIST = "filter_list"
    const val KIND_USERSCRIPT = "userscript"
    const val KIND_SPACE = "space"
    const val KIND_ESSENTIAL = "essential"
    const val KIND_PINNED_FOLDER = "pinned_folder"
    const val KIND_PINNED_TAB = "pinned_tab"

    private const val GZIP_THRESHOLD_BYTES = 16 * 1024
    const val MAX_USERSCRIPT_BYTES = 2 * 1024 * 1024

    sealed class DecodedSwiftfoxRecord {
        abstract val bsoId: String

        data class UnsupportedFuture(override val bsoId: String) : DecodedSwiftfoxRecord()

        data class Tombstone(
            override val bsoId: String,
            val kind: String,
            val updatedAt: Long
        ) : DecodedSwiftfoxRecord()

        data class Settings(
            override val bsoId: String,
            val updatedAt: Long,
            val contentHash: String,
            val settings: BrowserSettings
        ) : DecodedSwiftfoxRecord()

        data class SiteOverride(
            override val bsoId: String,
            val updatedAt: Long,
            val contentHash: String,
            val entity: SiteOverrideEntity
        ) : DecodedSwiftfoxRecord()

        data class ZappedRule(
            override val bsoId: String,
            val updatedAt: Long,
            val contentHash: String,
            val entity: ZappedRuleEntity
        ) : DecodedSwiftfoxRecord()

        data class FilterList(
            override val bsoId: String,
            val updatedAt: Long,
            val contentHash: String,
            val id: String,
            val name: String,
            val url: String,
            val enabled: Boolean,
            val isBuiltIn: Boolean
        ) : DecodedSwiftfoxRecord()

        data class Userscript(
            override val bsoId: String,
            val updatedAt: Long,
            val contentHash: String,
            val entity: UserscriptEntity
        ) : DecodedSwiftfoxRecord()

        data class Space(
            override val bsoId: String,
            val updatedAt: Long,
            val contentHash: String,
            val entity: SpaceEntity,
            val localOrder: Int
        ) : DecodedSwiftfoxRecord()

        data class Essential(
            override val bsoId: String,
            val updatedAt: Long,
            val contentHash: String,
            val entity: EssentialEntity,
            val localOrder: Int
        ) : DecodedSwiftfoxRecord()

        data class PinnedFolder(
            override val bsoId: String,
            val updatedAt: Long,
            val contentHash: String,
            val entity: PinnedFolderEntity,
            val localOrder: Int
        ) : DecodedSwiftfoxRecord()

        data class PinnedTab(
            override val bsoId: String,
            val updatedAt: Long,
            val contentHash: String,
            val entity: TabEntity,
            val localOrder: Int
        ) : DecodedSwiftfoxRecord()
    }

    fun metaGlobalAllowsSwiftfoxWrite(
        metaGlobalPayloadJson: String?,
        parser: kotlinx.serialization.json.Json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    ): Boolean = Sync15Transport.metaGlobalAllowsCollectionWrite(
        metaGlobalPayloadJson = metaGlobalPayloadJson,
        collection = SWIFTFOX_COLLECTION,
        maxVersion = 1.0,
        parser = parser
    )

    fun siteOverrideBsoId(host: String): String =
        "so_${sha256UrlSafe24(OmniboxEngine.normalizeHost(host))}"

    fun zappedRuleBsoId(host: String, selector: String): String {
        val normHost = OmniboxEngine.normalizeHost(host)
        return "zap_${sha256UrlSafe24("$normHost\n$selector")}"
    }

    fun filterListBsoId(entity: FilterListEntity): String = if (entity.isBuiltIn) {
        "flb_${sha256UrlSafe24(entity.id.trim())}"
    } else {
        "flc_${sha256UrlSafe24(entity.url.trim().lowercase(Locale.ROOT))}"
    }

    fun userscriptBsoId(entity: UserscriptEntity): String {
        val src = entity.sourceUrl?.trim()?.takeIf { it.isNotEmpty() }
        return if (src != null) {
            "us_${sha256UrlSafe24("url:$src")}"
        } else {
            "us_${sha256UrlSafe24("id:${entity.id}")}"
        }
    }

    fun spaceBsoId(spaceId: String): String =
        "sp_${sha256UrlSafe24(spaceId.trim())}"

    fun essentialBsoId(essentialId: String): String =
        "ess_${sha256UrlSafe24(essentialId.trim())}"

    fun pinnedFolderBsoId(folderId: String): String =
        "pf_${sha256UrlSafe24(folderId.trim())}"

    fun pinnedTabBsoId(tabId: String): String =
        "pin_${sha256UrlSafe24(tabId.trim())}"

    fun sha256Hex(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(Locale.ROOT, it) }
    }

    private fun sha256UrlSafe24(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).take(24)
    }

    fun encodeTombstone(bsoId: String, kind: String, updatedAt: Long): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(bsoId))
        put("schema", JsonPrimitive(SCHEMA_VERSION))
        put("kind", JsonPrimitive(kind))
        put("updatedAt", JsonPrimitive(updatedAt))
        put("deleted", JsonPrimitive(true))
    }

    fun settingsDataObject(settings: BrowserSettings): JsonObject {
        val foldedPlacement = if (settings.foldedProfile.toolbarPlacement.unfoldedOnly) {
            ToolbarPlacement.SPLIT
        } else {
            settings.foldedProfile.toolbarPlacement
        }
        val lineHeight = ((settings.readerLineHeight.coerceIn(1.3f, 2.0f) * 100f).roundToInt() / 100.0)
        return buildJsonObject {
            put("themeMode", JsonPrimitive(settings.themeMode.name))
            put("nightSurfaceStyle", JsonPrimitive(settings.nightSurfaceStyle.name))
            put("accentPreset", JsonPrimitive(settings.accentPreset.name))
            put("useDynamicColor", JsonPrimitive(settings.useDynamicColor))
            put("appIconBackgroundMode", JsonPrimitive(settings.appIconBackgroundMode.name))
            put("appIconColorStyle", JsonPrimitive(settings.appIconColorStyle.name))
            put("appIconMatchAccent", JsonPrimitive(settings.appIconMatchAccent))
            put("syncAppIconInApp", JsonPrimitive(settings.syncAppIconInApp))
            put("webDarkMode", JsonPrimitive(settings.webDarkMode.name))
            put("darkReaderAmoled", JsonPrimitive(settings.darkReaderAmoled))
            put("darkReaderBrightness", JsonPrimitive(settings.darkReaderBrightness.coerceIn(50, 150)))
            put("darkReaderContrast", JsonPrimitive(settings.darkReaderContrast.coerceIn(50, 150)))
            put("darkReaderSepia", JsonPrimitive(settings.darkReaderSepia.coerceIn(0, 100)))
            put("darkReaderSkipNativeDark", JsonPrimitive(settings.darkReaderSkipNativeDark))
            put("adblockEnabled", JsonPrimitive(settings.adblockEnabled))
            put("adblockCosmeticEnabled", JsonPrimitive(settings.adblockCosmeticEnabled))
            put("userscriptsEnabled", JsonPrimitive(settings.userscriptsEnabled))
            put("readerFontFamily", JsonPrimitive(settings.readerFontFamily.name))
            put("readerFontSizeSp", JsonPrimitive(settings.readerFontSizeSp.coerceIn(14, 28)))
            put("readerLineHeight", JsonPrimitive(lineHeight))
            put("readerSurfaceMode", JsonPrimitive(settings.readerSurfaceMode.name))
            put("foldedToolbar", JsonPrimitive(foldedPlacement.name))
            put("foldedSiteMode", JsonPrimitive(settings.foldedProfile.defaultSiteMode.name))
            put("unfoldedToolbar", JsonPrimitive(settings.unfoldedProfile.toolbarPlacement.name))
            put("unfoldedSiteMode", JsonPrimitive(settings.unfoldedProfile.defaultSiteMode.name))
            put("unfoldedSpacesSidebar", JsonPrimitive(settings.unfoldedProfile.showSpacesSidebar))
            put("searchEngine", JsonPrimitive(settings.searchEngine.name))
            put("customSearchUrlTemplate", JsonPrimitive(settings.customSearchUrlTemplate.trim()))
            put("searchSuggestionsEnabled", JsonPrimitive(settings.searchSuggestionsEnabled))
            put("httpsOnlyMode", JsonPrimitive(settings.httpsOnlyMode))
            put("externalAppLinkPolicy", JsonPrimitive(settings.externalAppLinkPolicy.name))
            put("pullToRefreshEnabled", JsonPrimitive(settings.pullToRefreshEnabled))
            put("swipeUrlBarSwitchTabs", JsonPrimitive(settings.swipeUrlBarSwitchTabs))
            put("hideToolbarsOnScroll", JsonPrimitive(settings.hideToolbarsOnScroll))
            put("confirmClosePinnedOnSecondTap", JsonPrimitive(settings.confirmClosePinnedOnSecondTap))
            put("tabAutoClearDuration", JsonPrimitive(settings.tabAutoClearDuration.name))
            put("backgroundMediaEnabled", JsonPrimitive(settings.backgroundMediaEnabled))
            put("pictureInPictureEnabled", JsonPrimitive(settings.pictureInPictureEnabled))
            put("blockThirdPartyCookies", JsonPrimitive(settings.blockThirdPartyCookies))
        }
    }

    fun settingsContentHash(settings: BrowserSettings): String =
        sha256Hex(settingsDataObject(settings).toString())

    fun encodeSettingsRecord(settings: BrowserSettings, updatedAt: Long): JsonObject =
        wrapRecord(SETTINGS_BSO_ID, KIND_SETTINGS, updatedAt, settingsDataObject(settings))

    fun siteOverrideDataObject(entity: SiteOverrideEntity): JsonObject {
        val normHost = OmniboxEngine.normalizeHost(entity.host)
        return buildJsonObject {
            put("host", JsonPrimitive(normHost))
            put("siteModeOverride", entity.siteModeOverride?.let(::JsonPrimitive) ?: JsonNull)
            put("httpsOnlyBypass", JsonPrimitive(entity.httpsOnlyBypass))
            put("externalAppPolicyOverride", entity.externalAppPolicyOverride?.let(::JsonPrimitive) ?: JsonNull)
            put("allowCamera", entity.allowCamera?.let(::JsonPrimitive) ?: JsonNull)
            put("allowMic", entity.allowMic?.let(::JsonPrimitive) ?: JsonNull)
            put("allowLocation", entity.allowLocation?.let(::JsonPrimitive) ?: JsonNull)
            put("allowNotifications", entity.allowNotifications?.let(::JsonPrimitive) ?: JsonNull)
            put("allowProtectedMedia", entity.allowProtectedMedia?.let(::JsonPrimitive) ?: JsonNull)
            put("javascriptEnabled", JsonPrimitive(entity.javascriptEnabled))
            put("webDarkModeOverride", entity.webDarkModeOverride?.let(::JsonPrimitive) ?: JsonNull)
            put("adblockDisabled", JsonPrimitive(entity.adblockDisabled))
            put("cookiesEnabled", JsonPrimitive(entity.cookiesEnabled))
            put("customUserAgent", entity.customUserAgent?.trim()?.takeIf { it.isNotEmpty() }?.let(::JsonPrimitive) ?: JsonNull)
        }
    }

    fun siteOverrideContentHash(entity: SiteOverrideEntity): String =
        sha256Hex(siteOverrideDataObject(entity).toString())

    fun encodeSiteOverrideRecord(entity: SiteOverrideEntity, updatedAt: Long): JsonObject =
        wrapRecord(
            siteOverrideBsoId(entity.host),
            KIND_SITE_OVERRIDE,
            updatedAt,
            siteOverrideDataObject(entity)
        )

    fun zappedRuleDataObject(entity: ZappedRuleEntity): JsonObject {
        val normHost = OmniboxEngine.normalizeHost(entity.host)
        return buildJsonObject {
            put("id", JsonPrimitive(entity.id))
            put("host", JsonPrimitive(normHost))
            put("selector", JsonPrimitive(entity.selector))
            put("label", JsonPrimitive(entity.label))
            put("enabled", JsonPrimitive(entity.enabled))
            put("createdAt", JsonPrimitive(entity.createdAt))
        }
    }

    fun zappedRuleContentHash(entity: ZappedRuleEntity): String {
        val normHost = OmniboxEngine.normalizeHost(entity.host)
        // Exclude local UUID & createdAt from semantic content hash so deduped rules stay stable
        val canonical = buildJsonObject {
            put("host", JsonPrimitive(normHost))
            put("selector", JsonPrimitive(entity.selector))
            put("label", JsonPrimitive(entity.label))
            put("enabled", JsonPrimitive(entity.enabled))
        }
        return sha256Hex(canonical.toString())
    }

    fun encodeZappedRuleRecord(entity: ZappedRuleEntity, updatedAt: Long): JsonObject =
        wrapRecord(
            zappedRuleBsoId(entity.host, entity.selector),
            KIND_ZAPPED_RULE,
            updatedAt,
            zappedRuleDataObject(entity)
        )

    fun filterListDataObject(entity: FilterListEntity): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(entity.id))
        put("name", JsonPrimitive(entity.name.trim()))
        put("url", JsonPrimitive(entity.url.trim()))
        put("enabled", JsonPrimitive(entity.enabled))
        put("isBuiltIn", JsonPrimitive(entity.isBuiltIn))
    }

    fun filterListContentHash(entity: FilterListEntity): String {
        val canonical = buildJsonObject {
            put("name", JsonPrimitive(entity.name.trim()))
            put("url", JsonPrimitive(entity.url.trim().lowercase(Locale.ROOT)))
            put("enabled", JsonPrimitive(entity.enabled))
            put("isBuiltIn", JsonPrimitive(entity.isBuiltIn))
            if (entity.isBuiltIn) {
                put("id", JsonPrimitive(entity.id))
            }
        }
        return sha256Hex(canonical.toString())
    }

    fun encodeFilterListRecord(entity: FilterListEntity, updatedAt: Long): JsonObject =
        wrapRecord(
            filterListBsoId(entity),
            KIND_FILTER_LIST,
            updatedAt,
            filterListDataObject(entity)
        )

    fun userscriptDataObject(entity: UserscriptEntity): JsonObject {
        val codeBytes = entity.code.toByteArray(Charsets.UTF_8)
        return buildJsonObject {
            put("id", JsonPrimitive(entity.id))
            put("name", JsonPrimitive(entity.name))
            put("namespace", JsonPrimitive(entity.namespace))
            put("version", JsonPrimitive(entity.version))
            put("description", JsonPrimitive(entity.description))
            put("sourceUrl", entity.sourceUrl?.trim()?.takeIf { it.isNotEmpty() }?.let(::JsonPrimitive) ?: JsonNull)
            if (codeBytes.size > GZIP_THRESHOLD_BYTES) {
                put("codeGzipBase64", JsonPrimitive(gzipToBase64(codeBytes)))
            } else {
                put("code", JsonPrimitive(entity.code))
            }
            put("matchesJson", JsonPrimitive(entity.matchesJson))
            put("excludesJson", JsonPrimitive(entity.excludesJson))
            put("runAt", JsonPrimitive(entity.runAt))
            put("grantsJson", JsonPrimitive(entity.grantsJson))
            put("enabled", JsonPrimitive(entity.enabled))
            put("installedAt", JsonPrimitive(entity.installedAt))
            put("updatedAt", JsonPrimitive(entity.updatedAt))
        }
    }

    fun userscriptContentHash(entity: UserscriptEntity): String {
        val canonical = buildJsonObject {
            put("name", JsonPrimitive(entity.name))
            put("namespace", JsonPrimitive(entity.namespace))
            put("version", JsonPrimitive(entity.version))
            put("description", JsonPrimitive(entity.description))
            put("sourceUrl", entity.sourceUrl?.trim()?.takeIf { it.isNotEmpty() }?.let(::JsonPrimitive) ?: JsonNull)
            put("codeHash", JsonPrimitive(sha256Hex(entity.code)))
            put("runAt", JsonPrimitive(entity.runAt))
            put("enabled", JsonPrimitive(entity.enabled))
        }
        return sha256Hex(canonical.toString())
    }

    fun encodeUserscriptRecord(entity: UserscriptEntity, updatedAt: Long): JsonObject =
        wrapRecord(
            userscriptBsoId(entity),
            KIND_USERSCRIPT,
            updatedAt,
            userscriptDataObject(entity)
        )

    fun spaceDataObject(entity: SpaceEntity, localOrder: Int): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(entity.id))
        put("name", JsonPrimitive(entity.name))
        put("iconKey", JsonPrimitive(entity.iconKey))
        put("localOrder", JsonPrimitive(localOrder))
        put("createdAt", JsonPrimitive(entity.createdAt))
    }

    fun spaceContentHash(entity: SpaceEntity, localOrder: Int): String {
        val canonical = buildJsonObject {
            put("id", JsonPrimitive(entity.id))
            put("name", JsonPrimitive(entity.name))
            put("iconKey", JsonPrimitive(entity.iconKey))
            put("localOrder", JsonPrimitive(localOrder))
        }
        return sha256Hex(canonical.toString())
    }

    fun encodeSpaceRecord(entity: SpaceEntity, localOrder: Int, updatedAt: Long): JsonObject =
        wrapRecord(
            spaceBsoId(entity.id),
            KIND_SPACE,
            updatedAt,
            spaceDataObject(entity, localOrder)
        )

    fun essentialDataObject(entity: EssentialEntity, localOrder: Int): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(entity.id))
        put("spaceId", entity.spaceId?.let(::JsonPrimitive) ?: JsonNull)
        put("title", JsonPrimitive(entity.title))
        put("url", JsonPrimitive(entity.url))
        put("domain", JsonPrimitive(entity.domain))
        put("localOrder", JsonPrimitive(localOrder))
        put("customIcon", entity.customIcon?.let(::JsonPrimitive) ?: JsonNull)
    }

    fun essentialContentHash(entity: EssentialEntity, localOrder: Int): String {
        val canonical = buildJsonObject {
            put("id", JsonPrimitive(entity.id))
            put("spaceId", entity.spaceId?.let(::JsonPrimitive) ?: JsonNull)
            put("title", JsonPrimitive(entity.title))
            put("url", JsonPrimitive(entity.url))
            put("domain", JsonPrimitive(entity.domain))
            put("localOrder", JsonPrimitive(localOrder))
            put("customIcon", entity.customIcon?.let(::JsonPrimitive) ?: JsonNull)
        }
        return sha256Hex(canonical.toString())
    }

    fun encodeEssentialRecord(entity: EssentialEntity, localOrder: Int, updatedAt: Long): JsonObject =
        wrapRecord(
            essentialBsoId(entity.id),
            KIND_ESSENTIAL,
            updatedAt,
            essentialDataObject(entity, localOrder)
        )

    fun pinnedFolderDataObject(entity: PinnedFolderEntity, localOrder: Int): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(entity.id))
        put("spaceId", JsonPrimitive(entity.spaceId))
        put("name", JsonPrimitive(entity.name.trim()))
        put("localOrder", JsonPrimitive(localOrder))
        put("customIcon", entity.customIcon?.let(::JsonPrimitive) ?: JsonNull)
        put("createdAt", JsonPrimitive(entity.createdAt))
    }

    fun pinnedFolderContentHash(entity: PinnedFolderEntity, localOrder: Int): String {
        val canonical = buildJsonObject {
            put("id", JsonPrimitive(entity.id))
            put("spaceId", JsonPrimitive(entity.spaceId))
            put("name", JsonPrimitive(entity.name.trim()))
            put("localOrder", JsonPrimitive(localOrder))
            put("customIcon", entity.customIcon?.let(::JsonPrimitive) ?: JsonNull)
        }
        return sha256Hex(canonical.toString())
    }

    fun encodePinnedFolderRecord(entity: PinnedFolderEntity, localOrder: Int, updatedAt: Long): JsonObject =
        wrapRecord(
            pinnedFolderBsoId(entity.id),
            KIND_PINNED_FOLDER,
            updatedAt,
            pinnedFolderDataObject(entity, localOrder)
        )

    fun pinnedTabHomeUrl(entity: TabEntity): String =
        entity.pinnedUrl?.trim()?.takeIf { it.isNotEmpty() } ?: entity.url.trim()

    fun pinnedTabDisplayTitle(entity: TabEntity): String =
        entity.customTitle?.trim()?.takeIf { it.isNotEmpty() } ?: entity.title.trim()

    fun pinnedTabDataObject(entity: TabEntity, localOrder: Int): JsonObject {
        val homeUrl = pinnedTabHomeUrl(entity)
        val displayTitle = pinnedTabDisplayTitle(entity).ifBlank {
            OmniboxEngine.extractHost(homeUrl).ifBlank { homeUrl }
        }
        return buildJsonObject {
            put("id", JsonPrimitive(entity.id))
            put("spaceId", JsonPrimitive(entity.spaceId))
            put("title", JsonPrimitive(displayTitle))
            put("url", JsonPrimitive(homeUrl))
            put("localOrder", JsonPrimitive(localOrder))
            put("customIcon", entity.customIcon?.let(::JsonPrimitive) ?: JsonNull)
            entity.folderId?.trim()?.takeIf { it.isNotEmpty() }?.let {
                put("folderId", JsonPrimitive(it))
            }
        }
    }

    fun pinnedTabContentHash(entity: TabEntity, localOrder: Int): String =
        sha256Hex(pinnedTabDataObject(entity, localOrder).toString())

    fun encodePinnedTabRecord(entity: TabEntity, localOrder: Int, updatedAt: Long): JsonObject =
        wrapRecord(
            pinnedTabBsoId(entity.id),
            KIND_PINNED_TAB,
            updatedAt,
            pinnedTabDataObject(entity, localOrder)
        )

    fun decodeRecord(bsoId: String, cleartext: JsonObject): DecodedSwiftfoxRecord? {
        val schema = intOrNull(cleartext["schema"]) ?: 1
        if (schema > SCHEMA_VERSION) {
            return DecodedSwiftfoxRecord.UnsupportedFuture(bsoId)
        }
        val kind = stringOrNull(cleartext["kind"]) ?: return null
        val updatedAt = longOrNull(cleartext["updatedAt"]) ?: 0L
        if (isDeletedTombstone(cleartext)) {
            return DecodedSwiftfoxRecord.Tombstone(bsoId, kind, updatedAt)
        }
        val data = cleartext["data"] as? JsonObject ?: return null

        return when (kind) {
            KIND_SETTINGS -> decodeSettings(bsoId, updatedAt, data)
            KIND_SITE_OVERRIDE -> decodeSiteOverride(bsoId, updatedAt, data)
            KIND_ZAPPED_RULE -> decodeZappedRule(bsoId, updatedAt, data)
            KIND_FILTER_LIST -> decodeFilterList(bsoId, updatedAt, data)
            KIND_USERSCRIPT -> decodeUserscript(bsoId, updatedAt, data)
            KIND_SPACE -> decodeSpace(bsoId, updatedAt, data)
            KIND_ESSENTIAL -> decodeEssential(bsoId, updatedAt, data)
            KIND_PINNED_FOLDER -> decodePinnedFolder(bsoId, updatedAt, data)
            KIND_PINNED_TAB -> decodePinnedTab(bsoId, updatedAt, data)
            else -> null
        }
    }

    private fun decodeSettings(bsoId: String, updatedAt: Long, data: JsonObject): DecodedSwiftfoxRecord.Settings {
        val defaults = BrowserSettings()
        val rawFoldedToolbar = parseEnum(stringOrNull(data["foldedToolbar"]), defaults.foldedProfile.toolbarPlacement)
        val foldedToolbar = if (rawFoldedToolbar.unfoldedOnly) ToolbarPlacement.SPLIT else rawFoldedToolbar
        val rawLineHeight = doubleOrNull(data["readerLineHeight"])?.toFloat() ?: defaults.readerLineHeight

        val settings = defaults.copy(
            themeMode = parseEnum(stringOrNull(data["themeMode"]), defaults.themeMode),
            nightSurfaceStyle = parseEnum(stringOrNull(data["nightSurfaceStyle"]), defaults.nightSurfaceStyle),
            accentPreset = parseEnum(stringOrNull(data["accentPreset"]), defaults.accentPreset),
            useDynamicColor = boolOrNull(data["useDynamicColor"]) ?: defaults.useDynamicColor,
            appIconBackgroundMode = parseEnum(
                stringOrNull(data["appIconBackgroundMode"]),
                defaults.appIconBackgroundMode
            ),
            appIconColorStyle = parseEnum(stringOrNull(data["appIconColorStyle"]), defaults.appIconColorStyle),
            appIconMatchAccent = boolOrNull(data["appIconMatchAccent"]) ?: defaults.appIconMatchAccent,
            syncAppIconInApp = boolOrNull(data["syncAppIconInApp"]) ?: defaults.syncAppIconInApp,
            webDarkMode = parseEnum(stringOrNull(data["webDarkMode"]), defaults.webDarkMode),
            darkReaderAmoled = boolOrNull(data["darkReaderAmoled"]) ?: defaults.darkReaderAmoled,
            darkReaderBrightness = (intOrNull(data["darkReaderBrightness"]) ?: defaults.darkReaderBrightness)
                .coerceIn(50, 150),
            darkReaderContrast = (intOrNull(data["darkReaderContrast"]) ?: defaults.darkReaderContrast)
                .coerceIn(50, 150),
            darkReaderSepia = (intOrNull(data["darkReaderSepia"]) ?: defaults.darkReaderSepia)
                .coerceIn(0, 100),
            darkReaderSkipNativeDark = boolOrNull(data["darkReaderSkipNativeDark"])
                ?: defaults.darkReaderSkipNativeDark,
            adblockEnabled = boolOrNull(data["adblockEnabled"]) ?: defaults.adblockEnabled,
            adblockCosmeticEnabled = boolOrNull(data["adblockCosmeticEnabled"])
                ?: defaults.adblockCosmeticEnabled,
            userscriptsEnabled = boolOrNull(data["userscriptsEnabled"]) ?: defaults.userscriptsEnabled,
            readerFontFamily = parseEnum(stringOrNull(data["readerFontFamily"]), defaults.readerFontFamily),
            readerFontSizeSp = (intOrNull(data["readerFontSizeSp"]) ?: defaults.readerFontSizeSp)
                .coerceIn(14, 28),
            readerLineHeight = ((rawLineHeight.coerceIn(1.3f, 2.0f) * 100f).roundToInt() / 100f),
            readerSurfaceMode = parseEnum(stringOrNull(data["readerSurfaceMode"]), defaults.readerSurfaceMode),
            foldedProfile = PostureProfile(
                toolbarPlacement = foldedToolbar,
                defaultSiteMode = parseEnum(
                    stringOrNull(data["foldedSiteMode"]),
                    defaults.foldedProfile.defaultSiteMode
                ),
                showSpacesSidebar = false
            ),
            unfoldedProfile = PostureProfile(
                toolbarPlacement = parseEnum(
                    stringOrNull(data["unfoldedToolbar"]),
                    defaults.unfoldedProfile.toolbarPlacement
                ),
                defaultSiteMode = parseEnum(
                    stringOrNull(data["unfoldedSiteMode"]),
                    defaults.unfoldedProfile.defaultSiteMode
                ),
                showSpacesSidebar = boolOrNull(data["unfoldedSpacesSidebar"])
                    ?: defaults.unfoldedProfile.showSpacesSidebar
            ),
            searchEngine = parseEnum(stringOrNull(data["searchEngine"]), defaults.searchEngine),
            customSearchUrlTemplate = stringOrNull(data["customSearchUrlTemplate"])?.trim()
                ?: defaults.customSearchUrlTemplate,
            searchSuggestionsEnabled = boolOrNull(data["searchSuggestionsEnabled"])
                ?: defaults.searchSuggestionsEnabled,
            httpsOnlyMode = boolOrNull(data["httpsOnlyMode"]) ?: defaults.httpsOnlyMode,
            externalAppLinkPolicy = parseEnum(
                stringOrNull(data["externalAppLinkPolicy"]),
                defaults.externalAppLinkPolicy
            ),
            pullToRefreshEnabled = boolOrNull(data["pullToRefreshEnabled"]) ?: defaults.pullToRefreshEnabled,
            swipeUrlBarSwitchTabs = boolOrNull(data["swipeUrlBarSwitchTabs"])
                ?: defaults.swipeUrlBarSwitchTabs,
            hideToolbarsOnScroll = boolOrNull(data["hideToolbarsOnScroll"]) ?: defaults.hideToolbarsOnScroll,
            confirmClosePinnedOnSecondTap = boolOrNull(data["confirmClosePinnedOnSecondTap"])
                ?: defaults.confirmClosePinnedOnSecondTap,
            tabAutoClearDuration = parseEnum(
                stringOrNull(data["tabAutoClearDuration"]),
                defaults.tabAutoClearDuration
            ),
            backgroundMediaEnabled = boolOrNull(data["backgroundMediaEnabled"])
                ?: defaults.backgroundMediaEnabled,
            pictureInPictureEnabled = boolOrNull(data["pictureInPictureEnabled"])
                ?: defaults.pictureInPictureEnabled,
            blockThirdPartyCookies = boolOrNull(data["blockThirdPartyCookies"])
                ?: defaults.blockThirdPartyCookies
        )
        return DecodedSwiftfoxRecord.Settings(
            bsoId = bsoId,
            updatedAt = updatedAt,
            contentHash = settingsContentHash(settings),
            settings = settings
        )
    }

    private fun decodeSiteOverride(
        bsoId: String,
        updatedAt: Long,
        data: JsonObject
    ): DecodedSwiftfoxRecord.SiteOverride? {
        val normHost = OmniboxEngine.normalizeHost(stringOrNull(data["host"])).takeIf { it.isNotBlank() }
            ?: return null
        val siteMode = SiteModeResolver.parseOrNull(stringOrNull(data["siteModeOverride"]))?.name
        val extPolicy = stringOrNull(data["externalAppPolicyOverride"])?.let { raw ->
            runCatching { ExternalAppLinkPolicy.valueOf(raw).name }.getOrNull()
        }
        val ua = stringOrNull(data["customUserAgent"])?.trim()
            ?.takeIf { it.isNotEmpty() && it.all { ch -> ch in '\u0020'..'\u007E' } }
        val entity = SiteOverrideEntity(
            host = normHost,
            siteModeOverride = siteMode,
            httpsOnlyBypass = boolOrNull(data["httpsOnlyBypass"]) ?: false,
            externalAppPolicyOverride = extPolicy,
            allowCamera = boolOrNull(data["allowCamera"]),
            allowMic = boolOrNull(data["allowMic"]),
            allowLocation = boolOrNull(data["allowLocation"]),
            allowNotifications = boolOrNull(data["allowNotifications"]),
            allowProtectedMedia = boolOrNull(data["allowProtectedMedia"]),
            javascriptEnabled = boolOrNull(data["javascriptEnabled"]) ?: true,
            webDarkModeOverride = boolOrNull(data["webDarkModeOverride"]),
            adblockDisabled = boolOrNull(data["adblockDisabled"]) ?: false,
            cookiesEnabled = boolOrNull(data["cookiesEnabled"]) ?: true,
            customUserAgent = ua
        )
        if (entity.isDefault()) return null
        return DecodedSwiftfoxRecord.SiteOverride(
            bsoId = siteOverrideBsoId(normHost),
            updatedAt = updatedAt,
            contentHash = siteOverrideContentHash(entity),
            entity = entity
        )
    }

    private fun decodeZappedRule(
        bsoId: String,
        updatedAt: Long,
        data: JsonObject
    ): DecodedSwiftfoxRecord.ZappedRule? {
        val selector = stringOrNull(data["selector"])?.takeIf { it.isNotBlank() } ?: return null
        val normHost = OmniboxEngine.normalizeHost(stringOrNull(data["host"]))
        val id = stringOrNull(data["id"])?.takeIf { it.isNotBlank() } ?: bsoId
        val entity = ZappedRuleEntity(
            id = id,
            host = normHost,
            selector = selector,
            label = stringOrNull(data["label"]).orEmpty(),
            enabled = boolOrNull(data["enabled"]) ?: true,
            createdAt = longOrNull(data["createdAt"]) ?: updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        )
        return DecodedSwiftfoxRecord.ZappedRule(
            bsoId = zappedRuleBsoId(normHost, selector),
            updatedAt = updatedAt,
            contentHash = zappedRuleContentHash(entity),
            entity = entity
        )
    }

    private fun decodeFilterList(
        bsoId: String,
        updatedAt: Long,
        data: JsonObject
    ): DecodedSwiftfoxRecord.FilterList? {
        val id = stringOrNull(data["id"])?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val name = stringOrNull(data["name"])?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val url = stringOrNull(data["url"])?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val parsed = url.toHttpUrlOrNull() ?: return null
        if (parsed.scheme != "http" && parsed.scheme != "https") return null
        val enabled = boolOrNull(data["enabled"]) ?: true
        val isBuiltIn = boolOrNull(data["isBuiltIn"]) ?: false
        val tempEntity = FilterListEntity(
            id = id,
            name = name,
            url = url,
            enabled = enabled,
            isBuiltIn = isBuiltIn
        )
        return DecodedSwiftfoxRecord.FilterList(
            bsoId = filterListBsoId(tempEntity),
            updatedAt = updatedAt,
            contentHash = filterListContentHash(tempEntity),
            id = id,
            name = name,
            url = url,
            enabled = enabled,
            isBuiltIn = isBuiltIn
        )
    }

    private fun decodeUserscript(
        bsoId: String,
        updatedAt: Long,
        data: JsonObject
    ): DecodedSwiftfoxRecord.Userscript? {
        val id = stringOrNull(data["id"])?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val rawCode = stringOrNull(data["code"]) ?: stringOrNull(data["codeGzipBase64"])?.let {
            gunzipFromBase64(it, MAX_USERSCRIPT_BYTES)
        } ?: return null
        if (rawCode.isBlank() || rawCode.toByteArray(Charsets.UTF_8).size > MAX_USERSCRIPT_BYTES) return null

        val sourceUrl = stringOrNull(data["sourceUrl"])?.trim()?.takeIf { it.isNotEmpty() }
        if (sourceUrl != null && (!UserscriptUrlMatcher.isHttpOrHttps(sourceUrl) || sourceUrl.toHttpUrlOrNull() == null)) {
            return null
        }

        val fallbackName = stringOrNull(data["name"])?.trim()?.takeIf { it.isNotEmpty() } ?: "Custom Userscript"
        val meta = UserscriptParser.parse(rawCode, fallbackName = fallbackName)
        val runAtRaw = stringOrNull(data["runAt"])
        val runAt = UserscriptRunAt.fromHeader(runAtRaw ?: meta.runAt.headerValue).headerValue
        val scriptUpdatedAt = longOrNull(data["updatedAt"]) ?: updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        val installedAt = longOrNull(data["installedAt"]) ?: scriptUpdatedAt

        val entity = UserscriptEntity(
            id = id,
            name = stringOrNull(data["name"])?.trim()?.takeIf { it.isNotEmpty() } ?: meta.name,
            namespace = stringOrNull(data["namespace"]) ?: meta.namespace,
            version = stringOrNull(data["version"]) ?: meta.version,
            description = stringOrNull(data["description"]) ?: meta.description,
            sourceUrl = sourceUrl,
            code = rawCode,
            matchesJson = UserscriptParser.encodeStringList(meta.matches),
            excludesJson = UserscriptParser.encodeStringList(meta.excludes),
            runAt = runAt,
            grantsJson = UserscriptParser.encodeStringList(meta.grants),
            enabled = boolOrNull(data["enabled"]) ?: true,
            installedAt = installedAt,
            updatedAt = scriptUpdatedAt
        )
        return DecodedSwiftfoxRecord.Userscript(
            bsoId = userscriptBsoId(entity),
            updatedAt = updatedAt,
            contentHash = userscriptContentHash(entity),
            entity = entity
        )
    }

    private fun decodeSpace(
        bsoId: String,
        updatedAt: Long,
        data: JsonObject
    ): DecodedSwiftfoxRecord.Space? {
        val id = stringOrNull(data["id"])?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val name = stringOrNull(data["name"])?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val rawIcon = stringOrNull(data["iconKey"])?.trim()
        val parsedIcon = CustomIconSpec.parse(rawIcon)
        val safeIconKey = when (parsedIcon) {
            CustomIconSpec.None -> "layers"
            is CustomIconSpec.MaterialIcon -> if (rawIcon?.startsWith(CustomIconSpec.ICON_PREFIX) == true) {
                parsedIcon.encodeExplicit() ?: "layers"
            } else {
                parsedIcon.key
            }
            is CustomIconSpec.Emoji,
            is CustomIconSpec.LocalImage -> parsedIcon.encodeExplicit() ?: "layers"
        }
        val localOrder = (intOrNull(data["localOrder"]) ?: 0).coerceAtLeast(0)
        val createdAt = longOrNull(data["createdAt"]) ?: updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        val entity = SpaceEntity(
            id = id,
            name = name,
            iconKey = safeIconKey,
            sortOrder = localOrder,
            createdAt = createdAt,
            syncGuid = null,
            themeColorHex = null,
            zenContainerGuid = null
        )
        return DecodedSwiftfoxRecord.Space(
            bsoId = spaceBsoId(id),
            updatedAt = updatedAt,
            contentHash = spaceContentHash(entity, localOrder),
            entity = entity,
            localOrder = localOrder
        )
    }

    private fun decodeEssential(
        bsoId: String,
        updatedAt: Long,
        data: JsonObject
    ): DecodedSwiftfoxRecord.Essential? {
        val id = stringOrNull(data["id"])?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val url = stringOrNull(data["url"])?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val parsed = url.toHttpUrlOrNull() ?: return null
        if (parsed.scheme != "http" && parsed.scheme != "https") return null
        val domain = stringOrNull(data["domain"])?.trim()?.takeIf { it.isNotEmpty() }
            ?: OmniboxEngine.extractHost(url).ifBlank { url }
        val title = stringOrNull(data["title"])?.trim()?.takeIf { it.isNotEmpty() } ?: domain
        val spaceId = stringOrNull(data["spaceId"])?.trim()?.takeIf { it.isNotEmpty() }
        val localOrder = (intOrNull(data["localOrder"]) ?: 0).coerceAtLeast(0)
        val customIcon = CustomIconSpec.parse(stringOrNull(data["customIcon"])).encodeExplicit()
        val entity = EssentialEntity(
            id = id,
            spaceId = spaceId,
            title = title,
            url = url,
            domain = domain,
            sortOrder = localOrder,
            syncGuid = null,
            zenContainerGuid = null,
            customIcon = customIcon
        )
        return DecodedSwiftfoxRecord.Essential(
            bsoId = essentialBsoId(id),
            updatedAt = updatedAt,
            contentHash = essentialContentHash(entity, localOrder),
            entity = entity,
            localOrder = localOrder
        )
    }

    private fun decodePinnedFolder(
        bsoId: String,
        updatedAt: Long,
        data: JsonObject
    ): DecodedSwiftfoxRecord.PinnedFolder? {
        val id = stringOrNull(data["id"])?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val spaceId = stringOrNull(data["spaceId"])?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val name = stringOrNull(data["name"])?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val localOrder = (intOrNull(data["localOrder"]) ?: 0).coerceAtLeast(0)
        val customIcon = CustomIconSpec.parse(stringOrNull(data["customIcon"])).encodeExplicit()
        val createdAt = longOrNull(data["createdAt"]) ?: updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        val entity = PinnedFolderEntity(
            id = id,
            spaceId = spaceId,
            name = name,
            customIcon = customIcon,
            isExpanded = true,
            sortOrder = localOrder,
            createdAt = createdAt
        )
        return DecodedSwiftfoxRecord.PinnedFolder(
            bsoId = pinnedFolderBsoId(id),
            updatedAt = updatedAt,
            contentHash = pinnedFolderContentHash(entity, localOrder),
            entity = entity,
            localOrder = localOrder
        )
    }

    private fun decodePinnedTab(
        bsoId: String,
        updatedAt: Long,
        data: JsonObject
    ): DecodedSwiftfoxRecord.PinnedTab? {
        val id = stringOrNull(data["id"])?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val spaceId = stringOrNull(data["spaceId"])?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val url = stringOrNull(data["url"])?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val parsed = url.toHttpUrlOrNull() ?: return null
        if (parsed.scheme != "http" && parsed.scheme != "https") return null
        val domain = OmniboxEngine.extractHost(url).ifBlank { url }
        val title = stringOrNull(data["title"])?.trim()?.takeIf { it.isNotEmpty() } ?: domain
        val localOrder = (intOrNull(data["localOrder"]) ?: 0).coerceAtLeast(0)
        val customIcon = CustomIconSpec.parse(stringOrNull(data["customIcon"])).encodeExplicit()
        val folderId = stringOrNull(data["folderId"])?.trim()?.takeIf { it.isNotEmpty() }
        val entity = TabEntity(
            id = id,
            spaceId = spaceId,
            title = title,
            url = url,
            isPrivate = false,
            isPinned = true,
            siteModeOverride = null,
            lastAccessedAt = updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
            sortOrder = localOrder,
            customIcon = customIcon,
            customTitle = title,
            essentialId = null,
            zenPinnedId = null,
            isUnloaded = true,
            pinnedUrl = url,
            folderId = folderId
        )
        return DecodedSwiftfoxRecord.PinnedTab(
            bsoId = pinnedTabBsoId(id),
            updatedAt = updatedAt,
            contentHash = pinnedTabContentHash(entity, localOrder),
            entity = entity,
            localOrder = localOrder
        )
    }

    private fun wrapRecord(
        bsoId: String,
        kind: String,
        updatedAt: Long,
        data: JsonObject
    ): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(bsoId))
        put("schema", JsonPrimitive(SCHEMA_VERSION))
        put("kind", JsonPrimitive(kind))
        put("updatedAt", JsonPrimitive(updatedAt))
        put("deleted", JsonPrimitive(false))
        put("data", data)
    }

    fun isDeletedTombstone(cleartext: JsonObject): Boolean {
        val deletedPrim = cleartext["deleted"] as? JsonPrimitive
        return deletedPrim != null && !deletedPrim.isString && deletedPrim.booleanOrNull == true
    }

    private fun gzipToBase64(bytes: ByteArray): String {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(bytes) }
        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }

    private fun gunzipFromBase64(encoded: String, maxBytes: Int): String? = runCatching {
        val compressed = Base64.getDecoder().decode(encoded.trim())
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        GZIPInputStream(ByteArrayInputStream(compressed)).use { gz ->
            while (true) {
                val read = gz.read(buf)
                if (read == -1) break
                total += read
                if (total > maxBytes) return null
                out.write(buf, 0, read)
            }
        }
        out.toString(Charsets.UTF_8.name())
    }.getOrNull()

    private inline fun <reified T : Enum<T>> parseEnum(raw: String?, fallback: T): T {
        if (raw.isNullOrBlank()) return fallback
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
    }

    private fun stringOrNull(el: JsonElement?): String? {
        val prim = el as? JsonPrimitive ?: return null
        return if (prim.isString) prim.content else null
    }

    private fun boolOrNull(el: JsonElement?): Boolean? {
        val prim = el as? JsonPrimitive ?: return null
        return if (!prim.isString) prim.booleanOrNull else null
    }

    private fun intOrNull(el: JsonElement?): Int? {
        val prim = el as? JsonPrimitive ?: return null
        return if (!prim.isString) prim.intOrNull else null
    }

    private fun longOrNull(el: JsonElement?): Long? {
        val prim = el as? JsonPrimitive ?: return null
        return if (!prim.isString) prim.longOrNull else null
    }

    private fun doubleOrNull(el: JsonElement?): Double? {
        val prim = el as? JsonPrimitive ?: return null
        return if (!prim.isString) prim.doubleOrNull else null
    }
}
