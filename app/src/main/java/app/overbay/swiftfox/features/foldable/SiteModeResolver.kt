package app.overbay.swiftfox.features.foldable

import app.overbay.swiftfox.data.prefs.PostureProfile
import app.overbay.swiftfox.data.prefs.SiteMode

object SiteModeResolver {

    fun resolve(
        tabOverride: SiteMode?,
        siteOverride: SiteMode?,
        postureProfile: PostureProfile
    ): SiteMode = tabOverride ?: siteOverride ?: postureProfile.defaultSiteMode

    fun parseOrNull(raw: String?): SiteMode? {
        if (raw.isNullOrBlank()) return null
        return runCatching { SiteMode.valueOf(raw) }.getOrNull()
    }

    fun shouldReloadOnTransition(
        previousMode: SiteMode,
        newMode: SiteMode,
        currentUrl: String
    ): Boolean {
        if (previousMode == newMode) return false
        return currentUrl.startsWith("http://", ignoreCase = true) ||
            currentUrl.startsWith("https://", ignoreCase = true)
    }
}
