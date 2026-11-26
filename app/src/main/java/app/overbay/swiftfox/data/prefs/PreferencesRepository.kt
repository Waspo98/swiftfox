package app.overbay.swiftfox.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.swiftfoxDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "swiftfox_preferences"
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NIGHT_SURFACE = stringPreferencesKey("night_surface")
        val ACCENT_PRESET = stringPreferencesKey("accent_preset")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val APP_ICON_BACKGROUND_MODE = stringPreferencesKey("app_icon_background_mode")
        val APP_ICON_COLOR_STYLE = stringPreferencesKey("app_icon_color_style")
        val APP_ICON_MATCH_ACCENT = booleanPreferencesKey("app_icon_match_accent")
        val SYNC_APP_ICON_IN_APP = booleanPreferencesKey("sync_app_icon_in_app")
        val WEB_DARK_MODE = stringPreferencesKey("web_dark_mode")
        val DARK_READER_AMOLED = booleanPreferencesKey("dark_reader_amoled")
        val DARK_READER_BRIGHTNESS = intPreferencesKey("dark_reader_brightness")
        val DARK_READER_CONTRAST = intPreferencesKey("dark_reader_contrast")
        val DARK_READER_SEPIA = intPreferencesKey("dark_reader_sepia")
        val DARK_READER_SKIP_NATIVE = booleanPreferencesKey("dark_reader_skip_native")

        val ADBLOCK_ENABLED = booleanPreferencesKey("adblock_enabled")
        val ADBLOCK_COSMETIC = booleanPreferencesKey("adblock_cosmetic")
        val USERSCRIPTS_ENABLED = booleanPreferencesKey("userscripts_enabled")

        val READER_FONT_FAMILY = stringPreferencesKey("reader_font_family")
        val READER_FONT_SIZE_SP = intPreferencesKey("reader_font_size_sp")
        val READER_LINE_HEIGHT = floatPreferencesKey("reader_line_height")
        val READER_SURFACE_MODE = stringPreferencesKey("reader_surface_mode")

        val FOLDED_TOOLBAR = stringPreferencesKey("folded_toolbar")
        val FOLDED_SITE_MODE = stringPreferencesKey("folded_site_mode")
        val UNFOLDED_TOOLBAR = stringPreferencesKey("unfolded_toolbar")
        val UNFOLDED_SITE_MODE = stringPreferencesKey("unfolded_site_mode")
        val UNFOLDED_SPACES_SIDEBAR = booleanPreferencesKey("unfolded_spaces_sidebar")

        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val CUSTOM_SEARCH_URL = stringPreferencesKey("custom_search_url")
        val SEARCH_SUGGESTIONS = booleanPreferencesKey("search_suggestions")

        val HTTPS_ONLY = booleanPreferencesKey("https_only")
        val EXTERNAL_APP_POLICY = stringPreferencesKey("external_app_policy")
        val PULL_TO_REFRESH = booleanPreferencesKey("pull_to_refresh")
        val SWIPE_URL_BAR_TABS = booleanPreferencesKey("swipe_url_bar_tabs")
        val HIDE_TOOLBARS_ON_SCROLL = booleanPreferencesKey("hide_toolbars_on_scroll")
        val CONFIRM_CLOSE_PINNED_SECOND_TAP = booleanPreferencesKey("confirm_close_pinned_second_tap")
        val TAB_AUTO_CLEAR_DURATION = stringPreferencesKey("tab_auto_clear_duration")
        val BACKGROUND_MEDIA = booleanPreferencesKey("background_media")
        val PICTURE_IN_PICTURE = booleanPreferencesKey("picture_in_picture")
        val BLOCK_THIRD_PARTY_COOKIES = booleanPreferencesKey("block_third_party_cookies")

        val SYNC_BOOKMARKS = booleanPreferencesKey("sync_bookmarks")
        val SYNC_HISTORY = booleanPreferencesKey("sync_history")
        val SYNC_TABS = booleanPreferencesKey("sync_tabs")
        val SYNC_ZEN_SPACES = booleanPreferencesKey("sync_zen_spaces")
        val SYNC_SWIFTFOX = booleanPreferencesKey("sync_swiftfox")
        val SYNC_INTERVAL_MINUTES = intPreferencesKey("sync_interval_minutes")
        val SYNC_LAST_TIMESTAMP_MS = longPreferencesKey("sync_last_timestamp_ms")
        val SWIFTFOX_SETTINGS_UPDATED_AT = longPreferencesKey("swiftfox_settings_updated_at")
        val SYNC_LOCAL_HISTORY_SEEDED = booleanPreferencesKey("sync_local_history_seeded")
        val SYNC_ZEN_SEPARATE_ESSENTIALS = booleanPreferencesKey("sync_zen_separate_essentials")
        val SYNC_HAS_CONNECTED_ACCOUNT = booleanPreferencesKey("sync_has_connected_account")
        val HAS_SEEDED_DEFAULTS = booleanPreferencesKey("has_seeded_defaults")
        val CLEARED_LEGACY_WEB_DARK_OVERRIDES = booleanPreferencesKey("cleared_legacy_web_dark_overrides")
    }

    val settingsFlow: Flow<BrowserSettings> = context.swiftfoxDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            val foldedPlacement = parseEnum(prefs[Keys.FOLDED_TOOLBAR], ToolbarPlacement.SPLIT)
                .let { if (it.unfoldedOnly) ToolbarPlacement.SPLIT else it }
            val unfoldedPlacement = parseEnum(prefs[Keys.UNFOLDED_TOOLBAR], ToolbarPlacement.TOP)

            BrowserSettings(
                themeMode = parseEnum(prefs[Keys.THEME_MODE], ThemeMode.SYSTEM),
                nightSurfaceStyle = parseEnum(prefs[Keys.NIGHT_SURFACE], NightSurfaceStyle.DARK),
                accentPreset = parseEnum(prefs[Keys.ACCENT_PRESET], AccentPreset.FOX_AMBER),
                useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: false,
                appIconBackgroundMode = parseEnum(
                    prefs[Keys.APP_ICON_BACKGROUND_MODE],
                    AppIconBackgroundMode.DARK
                ),
                appIconColorStyle = parseEnum(
                    prefs[Keys.APP_ICON_COLOR_STYLE],
                    AppIconColorStyle.CHANNEL_DEFAULT
                ),
                appIconMatchAccent = prefs[Keys.APP_ICON_MATCH_ACCENT] ?: false,
                syncAppIconInApp = prefs[Keys.SYNC_APP_ICON_IN_APP] ?: false,
                webDarkMode = parseEnum(prefs[Keys.WEB_DARK_MODE], WebDarkModePreference.FOLLOW_APP),
                darkReaderAmoled = prefs[Keys.DARK_READER_AMOLED] ?: false,
                darkReaderBrightness = (prefs[Keys.DARK_READER_BRIGHTNESS] ?: 100).coerceIn(50, 150),
                darkReaderContrast = (prefs[Keys.DARK_READER_CONTRAST] ?: 100).coerceIn(50, 150),
                darkReaderSepia = (prefs[Keys.DARK_READER_SEPIA] ?: 0).coerceIn(0, 100),
                darkReaderSkipNativeDark = prefs[Keys.DARK_READER_SKIP_NATIVE] ?: true,
                adblockEnabled = prefs[Keys.ADBLOCK_ENABLED] ?: true,
                adblockCosmeticEnabled = prefs[Keys.ADBLOCK_COSMETIC] ?: true,
                userscriptsEnabled = prefs[Keys.USERSCRIPTS_ENABLED] ?: true,
                readerFontFamily = parseEnum(prefs[Keys.READER_FONT_FAMILY], ReaderFontFamily.SANS),
                readerFontSizeSp = (prefs[Keys.READER_FONT_SIZE_SP] ?: 18).coerceIn(14, 28),
                readerLineHeight = (prefs[Keys.READER_LINE_HEIGHT] ?: 1.6f).coerceIn(1.3f, 2.0f),
                readerSurfaceMode = parseEnum(prefs[Keys.READER_SURFACE_MODE], ReaderSurfaceMode.FOLLOW_APP),
                foldedProfile = PostureProfile(
                    toolbarPlacement = foldedPlacement,
                    defaultSiteMode = parseEnum(prefs[Keys.FOLDED_SITE_MODE], SiteMode.MOBILE),
                    showSpacesSidebar = false
                ),
                unfoldedProfile = PostureProfile(
                    toolbarPlacement = unfoldedPlacement,
                    defaultSiteMode = parseEnum(prefs[Keys.UNFOLDED_SITE_MODE], SiteMode.DESKTOP),
                    showSpacesSidebar = prefs[Keys.UNFOLDED_SPACES_SIDEBAR] ?: true
                ),
                searchEngine = parseEnum(prefs[Keys.SEARCH_ENGINE], SearchEngineId.GOOGLE),
                customSearchUrlTemplate = prefs[Keys.CUSTOM_SEARCH_URL]
                    ?: "https://www.google.com/search?q=%s",
                searchSuggestionsEnabled = prefs[Keys.SEARCH_SUGGESTIONS] ?: true,
                httpsOnlyMode = prefs[Keys.HTTPS_ONLY] ?: true,
                externalAppLinkPolicy = parseEnum(
                    prefs[Keys.EXTERNAL_APP_POLICY],
                    ExternalAppLinkPolicy.ALWAYS_IN_SWIFTFOX
                ),
                pullToRefreshEnabled = prefs[Keys.PULL_TO_REFRESH] ?: true,
                swipeUrlBarSwitchTabs = prefs[Keys.SWIPE_URL_BAR_TABS] ?: true,
                hideToolbarsOnScroll = prefs[Keys.HIDE_TOOLBARS_ON_SCROLL] ?: true,
                confirmClosePinnedOnSecondTap = prefs[Keys.CONFIRM_CLOSE_PINNED_SECOND_TAP] ?: true,
                tabAutoClearDuration = parseEnum(
                    prefs[Keys.TAB_AUTO_CLEAR_DURATION],
                    TabAutoClearDuration.WEEK_1
                ),
                backgroundMediaEnabled = prefs[Keys.BACKGROUND_MEDIA] ?: false,
                pictureInPictureEnabled = prefs[Keys.PICTURE_IN_PICTURE] ?: false,
                blockThirdPartyCookies = prefs[Keys.BLOCK_THIRD_PARTY_COOKIES] ?: false,
                syncBookmarksEnabled = prefs[Keys.SYNC_BOOKMARKS] ?: true,
                syncHistoryEnabled = prefs[Keys.SYNC_HISTORY] ?: true,
                syncTabsEnabled = prefs[Keys.SYNC_TABS] ?: true,
                syncZenSpacesEnabled = prefs[Keys.SYNC_ZEN_SPACES] ?: true,
                syncSwiftfoxEnabled = prefs[Keys.SYNC_SWIFTFOX] ?: true,
                syncPeriodicIntervalMinutes = (prefs[Keys.SYNC_INTERVAL_MINUTES] ?: 60).let {
                    if (it in listOf(0, 30, 60, 240)) it else 60
                },
                syncLastTimestampMs = prefs[Keys.SYNC_LAST_TIMESTAMP_MS] ?: 0L,
                swiftfoxSettingsUpdatedAtMs = prefs[Keys.SWIFTFOX_SETTINGS_UPDATED_AT] ?: 0L,
                syncLocalHistorySeeded = prefs[Keys.SYNC_LOCAL_HISTORY_SEEDED] ?: false,
                syncZenSeparateEssentials = prefs[Keys.SYNC_ZEN_SEPARATE_ESSENTIALS],
                syncHasConnectedAccount = prefs[Keys.SYNC_HAS_CONNECTED_ACCOUNT] ?: false,
                hasSeededDefaults = prefs[Keys.HAS_SEEDED_DEFAULTS] ?: false,
                hasClearedLegacyWebDarkOverrides = prefs[Keys.CLEARED_LEGACY_WEB_DARK_OVERRIDES] ?: false
            )
        }
        .distinctUntilChanged()

    private suspend inline fun editSyncedPref(crossinline block: (MutablePreferences) -> Unit) {
        context.swiftfoxDataStore.edit {
            block(it)
            it[Keys.SWIFTFOX_SETTINGS_UPDATED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        editSyncedPref { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setNightSurfaceStyle(style: NightSurfaceStyle) {
        editSyncedPref { it[Keys.NIGHT_SURFACE] = style.name }
    }

    suspend fun setAccentPreset(preset: AccentPreset) {
        editSyncedPref { it[Keys.ACCENT_PRESET] = preset.name }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        editSyncedPref { it[Keys.USE_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setAppIconBackgroundMode(mode: AppIconBackgroundMode) {
        editSyncedPref { it[Keys.APP_ICON_BACKGROUND_MODE] = mode.name }
    }

    suspend fun setAppIconColorStyle(style: AppIconColorStyle) {
        editSyncedPref {
            it[Keys.APP_ICON_MATCH_ACCENT] = false
            it[Keys.APP_ICON_COLOR_STYLE] = style.name
        }
    }

    suspend fun setAppIconMatchAccent(enabled: Boolean) {
        editSyncedPref { it[Keys.APP_ICON_MATCH_ACCENT] = enabled }
    }

    suspend fun setSyncAppIconInApp(enabled: Boolean) {
        editSyncedPref { it[Keys.SYNC_APP_ICON_IN_APP] = enabled }
    }

    suspend fun setWebDarkMode(preference: WebDarkModePreference) {
        editSyncedPref { it[Keys.WEB_DARK_MODE] = preference.name }
    }

    suspend fun setDarkReaderAmoled(enabled: Boolean) {
        editSyncedPref { it[Keys.DARK_READER_AMOLED] = enabled }
    }

    suspend fun setDarkReaderBrightness(value: Int) {
        editSyncedPref { it[Keys.DARK_READER_BRIGHTNESS] = value.coerceIn(50, 150) }
    }

    suspend fun setDarkReaderContrast(value: Int) {
        editSyncedPref { it[Keys.DARK_READER_CONTRAST] = value.coerceIn(50, 150) }
    }

    suspend fun setDarkReaderSepia(value: Int) {
        editSyncedPref { it[Keys.DARK_READER_SEPIA] = value.coerceIn(0, 100) }
    }

    suspend fun setDarkReaderSkipNativeDark(enabled: Boolean) {
        editSyncedPref { it[Keys.DARK_READER_SKIP_NATIVE] = enabled }
    }

    suspend fun setAdblockEnabled(enabled: Boolean) {
        editSyncedPref { it[Keys.ADBLOCK_ENABLED] = enabled }
    }

    suspend fun setAdblockCosmeticEnabled(enabled: Boolean) {
        editSyncedPref { it[Keys.ADBLOCK_COSMETIC] = enabled }
    }

    suspend fun setUserscriptsEnabled(enabled: Boolean) {
        editSyncedPref { it[Keys.USERSCRIPTS_ENABLED] = enabled }
    }

    suspend fun setReaderFontFamily(family: ReaderFontFamily) {
        editSyncedPref { it[Keys.READER_FONT_FAMILY] = family.name }
    }

    suspend fun setReaderFontSizeSp(sizeSp: Int) {
        editSyncedPref { it[Keys.READER_FONT_SIZE_SP] = sizeSp.coerceIn(14, 28) }
    }

    suspend fun setReaderLineHeight(lineHeight: Float) {
        editSyncedPref { it[Keys.READER_LINE_HEIGHT] = lineHeight.coerceIn(1.3f, 2.0f) }
    }

    suspend fun setReaderSurfaceMode(mode: ReaderSurfaceMode) {
        editSyncedPref { it[Keys.READER_SURFACE_MODE] = mode.name }
    }

    suspend fun setToolbarPlacement(posture: DevicePosture, placement: ToolbarPlacement) {
        editSyncedPref {
            when (posture) {
                DevicePosture.FOLDED -> {
                    val safe = if (placement.unfoldedOnly) ToolbarPlacement.SPLIT else placement
                    it[Keys.FOLDED_TOOLBAR] = safe.name
                }
                DevicePosture.UNFOLDED -> {
                    it[Keys.UNFOLDED_TOOLBAR] = placement.name
                }
            }
        }
    }

    suspend fun setDefaultSiteMode(posture: DevicePosture, siteMode: SiteMode) {
        editSyncedPref {
            when (posture) {
                DevicePosture.FOLDED -> it[Keys.FOLDED_SITE_MODE] = siteMode.name
                DevicePosture.UNFOLDED -> it[Keys.UNFOLDED_SITE_MODE] = siteMode.name
            }
        }
    }

    suspend fun setUnfoldedSpacesSidebar(visible: Boolean) {
        editSyncedPref { it[Keys.UNFOLDED_SPACES_SIDEBAR] = visible }
    }

    suspend fun setSearchEngine(engine: SearchEngineId) {
        editSyncedPref { it[Keys.SEARCH_ENGINE] = engine.name }
    }

    suspend fun setCustomSearchUrlTemplate(template: String) {
        editSyncedPref { it[Keys.CUSTOM_SEARCH_URL] = template.trim() }
    }

    suspend fun setSearchSuggestionsEnabled(enabled: Boolean) {
        editSyncedPref { it[Keys.SEARCH_SUGGESTIONS] = enabled }
    }

    suspend fun setHttpsOnlyMode(enabled: Boolean) {
        editSyncedPref { it[Keys.HTTPS_ONLY] = enabled }
    }

    suspend fun setExternalAppLinkPolicy(policy: ExternalAppLinkPolicy) {
        editSyncedPref { it[Keys.EXTERNAL_APP_POLICY] = policy.name }
    }

    suspend fun setPullToRefreshEnabled(enabled: Boolean) {
        editSyncedPref { it[Keys.PULL_TO_REFRESH] = enabled }
    }

    suspend fun setSwipeUrlBarSwitchTabs(enabled: Boolean) {
        editSyncedPref { it[Keys.SWIPE_URL_BAR_TABS] = enabled }
    }

    suspend fun setHideToolbarsOnScroll(enabled: Boolean) {
        editSyncedPref { it[Keys.HIDE_TOOLBARS_ON_SCROLL] = enabled }
    }

    suspend fun setConfirmClosePinnedOnSecondTap(enabled: Boolean) {
        editSyncedPref { it[Keys.CONFIRM_CLOSE_PINNED_SECOND_TAP] = enabled }
    }

    suspend fun setTabAutoClearDuration(duration: TabAutoClearDuration) {
        editSyncedPref { it[Keys.TAB_AUTO_CLEAR_DURATION] = duration.name }
    }

    suspend fun setBackgroundMediaEnabled(enabled: Boolean) {
        editSyncedPref { it[Keys.BACKGROUND_MEDIA] = enabled }
    }

    suspend fun setPictureInPictureEnabled(enabled: Boolean) {
        editSyncedPref { it[Keys.PICTURE_IN_PICTURE] = enabled }
    }

    suspend fun setBlockThirdPartyCookies(enabled: Boolean) {
        editSyncedPref { it[Keys.BLOCK_THIRD_PARTY_COOKIES] = enabled }
    }

    suspend fun applySyncedSettings(synced: BrowserSettings, updatedAtMs: Long) {
        context.swiftfoxDataStore.edit {
            it[Keys.THEME_MODE] = synced.themeMode.name
            it[Keys.NIGHT_SURFACE] = synced.nightSurfaceStyle.name
            it[Keys.ACCENT_PRESET] = synced.accentPreset.name
            it[Keys.USE_DYNAMIC_COLOR] = synced.useDynamicColor
            it[Keys.APP_ICON_BACKGROUND_MODE] = synced.appIconBackgroundMode.name
            it[Keys.APP_ICON_COLOR_STYLE] = synced.appIconColorStyle.name
            it[Keys.APP_ICON_MATCH_ACCENT] = synced.appIconMatchAccent
            it[Keys.SYNC_APP_ICON_IN_APP] = synced.syncAppIconInApp
            it[Keys.WEB_DARK_MODE] = synced.webDarkMode.name
            it[Keys.DARK_READER_AMOLED] = synced.darkReaderAmoled
            it[Keys.DARK_READER_BRIGHTNESS] = synced.darkReaderBrightness.coerceIn(50, 150)
            it[Keys.DARK_READER_CONTRAST] = synced.darkReaderContrast.coerceIn(50, 150)
            it[Keys.DARK_READER_SEPIA] = synced.darkReaderSepia.coerceIn(0, 100)
            it[Keys.DARK_READER_SKIP_NATIVE] = synced.darkReaderSkipNativeDark
            it[Keys.ADBLOCK_ENABLED] = synced.adblockEnabled
            it[Keys.ADBLOCK_COSMETIC] = synced.adblockCosmeticEnabled
            it[Keys.USERSCRIPTS_ENABLED] = synced.userscriptsEnabled
            it[Keys.READER_FONT_FAMILY] = synced.readerFontFamily.name
            it[Keys.READER_FONT_SIZE_SP] = synced.readerFontSizeSp.coerceIn(14, 28)
            it[Keys.READER_LINE_HEIGHT] = synced.readerLineHeight.coerceIn(1.3f, 2.0f)
            it[Keys.READER_SURFACE_MODE] = synced.readerSurfaceMode.name
            val foldedPlacement = if (synced.foldedProfile.toolbarPlacement.unfoldedOnly) {
                ToolbarPlacement.SPLIT
            } else {
                synced.foldedProfile.toolbarPlacement
            }
            it[Keys.FOLDED_TOOLBAR] = foldedPlacement.name
            it[Keys.FOLDED_SITE_MODE] = synced.foldedProfile.defaultSiteMode.name
            it[Keys.UNFOLDED_TOOLBAR] = synced.unfoldedProfile.toolbarPlacement.name
            it[Keys.UNFOLDED_SITE_MODE] = synced.unfoldedProfile.defaultSiteMode.name
            it[Keys.UNFOLDED_SPACES_SIDEBAR] = synced.unfoldedProfile.showSpacesSidebar
            it[Keys.SEARCH_ENGINE] = synced.searchEngine.name
            it[Keys.CUSTOM_SEARCH_URL] = synced.customSearchUrlTemplate.trim()
            it[Keys.SEARCH_SUGGESTIONS] = synced.searchSuggestionsEnabled
            it[Keys.HTTPS_ONLY] = synced.httpsOnlyMode
            it[Keys.EXTERNAL_APP_POLICY] = synced.externalAppLinkPolicy.name
            it[Keys.PULL_TO_REFRESH] = synced.pullToRefreshEnabled
            it[Keys.SWIPE_URL_BAR_TABS] = synced.swipeUrlBarSwitchTabs
            it[Keys.HIDE_TOOLBARS_ON_SCROLL] = synced.hideToolbarsOnScroll
            it[Keys.CONFIRM_CLOSE_PINNED_SECOND_TAP] = synced.confirmClosePinnedOnSecondTap
            it[Keys.TAB_AUTO_CLEAR_DURATION] = synced.tabAutoClearDuration.name
            it[Keys.BACKGROUND_MEDIA] = synced.backgroundMediaEnabled
            it[Keys.PICTURE_IN_PICTURE] = synced.pictureInPictureEnabled
            it[Keys.BLOCK_THIRD_PARTY_COOKIES] = synced.blockThirdPartyCookies
            it[Keys.SWIFTFOX_SETTINGS_UPDATED_AT] = updatedAtMs
        }
    }

    suspend fun setSyncBookmarksEnabled(enabled: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.SYNC_BOOKMARKS] = enabled }
    }

    suspend fun setSyncHistoryEnabled(enabled: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.SYNC_HISTORY] = enabled }
    }

    suspend fun setSyncTabsEnabled(enabled: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.SYNC_TABS] = enabled }
    }

    suspend fun setSyncZenSpacesEnabled(enabled: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.SYNC_ZEN_SPACES] = enabled }
    }

    suspend fun setSyncSwiftfoxEnabled(enabled: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.SYNC_SWIFTFOX] = enabled }
    }

    suspend fun setSyncPeriodicIntervalMinutes(minutes: Int) {
        context.swiftfoxDataStore.edit { it[Keys.SYNC_INTERVAL_MINUTES] = minutes }
    }

    suspend fun setSyncLastTimestampMs(timestampMs: Long) {
        context.swiftfoxDataStore.edit { it[Keys.SYNC_LAST_TIMESTAMP_MS] = timestampMs }
    }

    suspend fun setSwiftfoxSettingsUpdatedAtMs(timestampMs: Long) {
        context.swiftfoxDataStore.edit { it[Keys.SWIFTFOX_SETTINGS_UPDATED_AT] = timestampMs }
    }

    suspend fun setSyncLocalHistorySeeded(seeded: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.SYNC_LOCAL_HISTORY_SEEDED] = seeded }
    }

    suspend fun setHasSeededDefaults(seeded: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.HAS_SEEDED_DEFAULTS] = seeded }
    }

    suspend fun setHasClearedLegacyWebDarkOverrides(cleared: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.CLEARED_LEGACY_WEB_DARK_OVERRIDES] = cleared }
    }

    suspend fun setSyncZenSeparateEssentials(separate: Boolean?) {
        context.swiftfoxDataStore.edit {
            if (separate == null) {
                it.remove(Keys.SYNC_ZEN_SEPARATE_ESSENTIALS)
            } else {
                it[Keys.SYNC_ZEN_SEPARATE_ESSENTIALS] = separate
            }
        }
    }

    suspend fun setSyncHasConnectedAccount(connected: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.SYNC_HAS_CONNECTED_ACCOUNT] = connected }
    }

    private inline fun <reified T : Enum<T>> parseEnum(raw: String?, fallback: T): T {
        if (raw == null) return fallback
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
    }
}