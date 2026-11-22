package app.overbay.swiftfox.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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
        val BACKGROUND_MEDIA = booleanPreferencesKey("background_media")
        val BLOCK_THIRD_PARTY_COOKIES = booleanPreferencesKey("block_third_party_cookies")
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
                backgroundMediaEnabled = prefs[Keys.BACKGROUND_MEDIA] ?: true,
                blockThirdPartyCookies = prefs[Keys.BLOCK_THIRD_PARTY_COOKIES] ?: true
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

    suspend fun setBackgroundMediaEnabled(enabled: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.BACKGROUND_MEDIA] = enabled }
    }

    suspend fun setBlockThirdPartyCookies(enabled: Boolean) {
        context.swiftfoxDataStore.edit { it[Keys.BLOCK_THIRD_PARTY_COOKIES] = enabled }
    }

    private inline fun <reified T : Enum<T>> parseEnum(raw: String?, fallback: T): T {
        if (raw == null) return fallback
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
    }
}
