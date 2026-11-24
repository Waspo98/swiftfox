package app.overbay.swiftfox.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
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
        val WEB_DARK_MODE = stringPreferencesKey("web_dark_mode")
        val WEB_DARK_ENGINE = stringPreferencesKey("web_dark_engine")
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
        val BACKGROUND_MEDIA = booleanPreferencesKey("background_media")
        val BLOCK_THIRD_PARTY_COOKIES = booleanPreferencesKey("block_third_party_cookies")

        val SYNC_BOOKMARKS = booleanPreferencesKey("sync_bookmarks")
        val SYNC_HISTORY = booleanPreferencesKey("sync_history")
        val SYNC_TABS = booleanPreferencesKey("sync_tabs")
        val SYNC_ZEN_SPACES = booleanPreferencesKey("sync_zen_spaces")
        val SYNC_INTERVAL_MINUTES = intPreferencesKey("sync_interval_minutes")
        val SYNC_LAST_TIMESTAMP_MS = longPreferencesKey("sync_last_timestamp_ms")
        val SYNC_LOCAL_HISTORY_SEEDED = booleanPreferencesKey("sync_local_history_seeded")
        val SYNC_ZEN_SEPARATE_ESSENTIALS = booleanPreferencesKey("sync_zen_separate_essentials")
        val HAS_SEEDED_DEFAULTS = booleanPreferencesKey("has_seeded_defaults")
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
                webDarkMode = parseEnum(prefs[Keys.WEB_DARK_MODE], WebDarkModePreference.FOLLOW_APP),
                webDarkEngine = parseEnum(prefs[Keys.WEB_DARK_ENGINE], WebDarkEngine.DARK_READER),
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
                    ExternalAppLinkPolicy.ASK
                ),
                pullToRefreshEnabled = prefs[Keys.PULL_TO_REFRESH] ?: true,
                swipeUrlBarSwitchTabs = prefs[Keys.SWIPE_URL_BAR_TABS] ?: true,
                hideToolbarsOnScroll = prefs[Keys.HIDE_TOOLBARS_ON_SCROLL] ?: true,
                confirmClosePinnedOnSecondTap = prefs[Keys.CONFIRM_CLOSE_PINNED_SECOND_TAP] ?: true,
                backgroundMediaEnabled = prefs[Keys.BACKGROUND_MEDIA] ?: true,
                blockThirdPartyCookies = prefs[Keys.BLOCK_THIRD_PARTY_COOKIES] ?: true,
                syncBookmarksEnabled = prefs[Keys.SYNC_BOOKMARKS] ?: true,
                syncHistoryEnabled = prefs[Keys.SYNC_HISTORY] ?: true,
                syncTabsEnabled = prefs[Keys.SYNC_TABS] ?: true,
                syncZenSpacesEnabled = prefs[Keys.SYNC_ZEN_SPACES] ?: true,
                syncPeriodicIntervalMinutes = (prefs[Keys.SYNC_INTERVAL_MINUTES] ?: 60).let {
                    if (it in listOf(0, 30, 60, 240)) it else 60
                },
                syncLastTimestampMs = prefs[Keys.SYNC_LAST_TIMESTAMP_MS] ?: 0L,
                syncLocalHistorySeeded = prefs[Keys.SYNC_LOCAL_HISTORY_SEEDED] ?: false,
                syncZenSeparateEssentials = prefs[Keys.SYNC_ZEN_SEPARATE_ESSENTIALS],
                hasSeededDefaults = prefs[Keys.HAS_SEEDED_DEFAULTS] ?: false
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.swiftfoxDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setNightSurfaceStyle(style: NightSurfaceStyle) {
        context.swiftfoxDataStore.edit { it[Keys.NIGHT_SURFACE] = style.name }
    }

    suspend fun setAccentPreset(preset: AccentPreset) {
        context.swiftfoxDataStore.edit { it[Keys.ACCENT_PRESET] = preset.name }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setWebDarkMode(preference: WebDarkModePreference) {
        context.swiftfoxDataStore.edit { it[Keys.WEB_DARK_MODE] = preference.name }
    }

    suspend fun setWebDarkEngine(engine: WebDarkEngine) {
        context.swiftfoxDataStore.edit { it[Keys.WEB_DARK_ENGINE] = engine.name }
    }

    suspend fun setDarkReaderAmoled(enabled: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.DARK_READER_AMOLED] = enabled }
    }

    suspend fun setDarkReaderBrightness(value: Int) {
        context.swiftfoxDataStore.edit { it[Keys.DARK_READER_BRIGHTNESS] = value.coerceIn(50, 150) }
    }

    suspend fun setDarkReaderContrast(value: Int) {
        context.swiftfoxDataStore.edit { it[Keys.DARK_READER_CONTRAST] = value.coerceIn(50, 150) }
    }

    suspend fun setDarkReaderSepia(value: Int) {
        context.swiftfoxDataStore.edit { it[Keys.DARK_READER_SEPIA] = value.coerceIn(0, 100) }
    }

    suspend fun setDarkReaderSkipNativeDark(enabled: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.DARK_READER_SKIP_NATIVE] = enabled }
    }

    suspend fun setAdblockEnabled(enabled: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.ADBLOCK_ENABLED] = enabled }
    }

    suspend fun setAdblockCosmeticEnabled(enabled: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.ADBLOCK_COSMETIC] = enabled }
    }

    suspend fun setUserscriptsEnabled(enabled: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.USERSCRIPTS_ENABLED] = enabled }
    }

    suspend fun setReaderFontFamily(family: ReaderFontFamily) {
        context.swiftfoxDataStore.edit { it[Keys.READER_FONT_FAMILY] = family.name }
    }

    suspend fun setReaderFontSizeSp(sizeSp: Int) {
        context.swiftfoxDataStore.edit { it[Keys.READER_FONT_SIZE_SP] = sizeSp.coerceIn(14, 28) }
    }

    suspend fun setReaderLineHeight(lineHeight: Float) {
        context.swiftfoxDataStore.edit { it[Keys.READER_LINE_HEIGHT] = lineHeight.coerceIn(1.3f, 2.0f) }
    }

    suspend fun setReaderSurfaceMode(mode: ReaderSurfaceMode) {
        context.swiftfoxDataStore.edit { it[Keys.READER_SURFACE_MODE] = mode.name }
    }

    suspend fun setToolbarPlacement(posture: DevicePosture, placement: ToolbarPlacement) {
        context.swiftfoxDataStore.edit {
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
        context.swiftfoxDataStore.edit {
            when (posture) {
                DevicePosture.FOLDED -> it[Keys.FOLDED_SITE_MODE] = siteMode.name
                DevicePosture.UNFOLDED -> it[Keys.UNFOLDED_SITE_MODE] = siteMode.name
            }
        }
    }

    suspend fun setUnfoldedSpacesSidebar(visible: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.UNFOLDED_SPACES_SIDEBAR] = visible }
    }

    suspend fun setSearchEngine(engine: SearchEngineId) {
        context.swiftfoxDataStore.edit { it[Keys.SEARCH_ENGINE] = engine.name }
    }

    suspend fun setCustomSearchUrlTemplate(template: String) {
        context.swiftfoxDataStore.edit { it[Keys.CUSTOM_SEARCH_URL] = template.trim() }
    }

    suspend fun setSearchSuggestionsEnabled(enabled: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.SEARCH_SUGGESTIONS] = enabled }
    }

    suspend fun setHttpsOnlyMode(enabled: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.HTTPS_ONLY] = enabled }
    }

    suspend fun setExternalAppLinkPolicy(policy: ExternalAppLinkPolicy) {
        context.swiftfoxDataStore.edit { it[Keys.EXTERNAL_APP_POLICY] = policy.name }
    }

    suspend fun setPullToRefreshEnabled(enabled: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.PULL_TO_REFRESH] = enabled }
    }

    suspend fun setSwipeUrlBarSwitchTabs(enabled: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.SWIPE_URL_BAR_TABS] = enabled }
    }

    suspend fun setHideToolbarsOnScroll(enabled: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.HIDE_TOOLBARS_ON_SCROLL] = enabled }
    }

    suspend fun setConfirmClosePinnedOnSecondTap(enabled: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.CONFIRM_CLOSE_PINNED_SECOND_TAP] = enabled }
    }

    suspend fun setBackgroundMediaEnabled(enabled: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.BACKGROUND_MEDIA] = enabled }
    }

    suspend fun setBlockThirdPartyCookies(enabled: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.BLOCK_THIRD_PARTY_COOKIES] = enabled }
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

    suspend fun setSyncPeriodicIntervalMinutes(minutes: Int) {
        context.swiftfoxDataStore.edit { it[Keys.SYNC_INTERVAL_MINUTES] = minutes }
    }

    suspend fun setSyncLastTimestampMs(timestampMs: Long) {
        context.swiftfoxDataStore.edit { it[Keys.SYNC_LAST_TIMESTAMP_MS] = timestampMs }
    }

    suspend fun setSyncLocalHistorySeeded(seeded: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.SYNC_LOCAL_HISTORY_SEEDED] = seeded }
    }

    suspend fun setHasSeededDefaults(seeded: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.HAS_SEEDED_DEFAULTS] = seeded }
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

    private inline fun <reified T : Enum<T>> parseEnum(raw: String?, fallback: T): T {
        if (raw == null) return fallback
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
    }
}