package app.overbay.swiftfox

import app.overbay.swiftfox.data.prefs.PostureProfile
import app.overbay.swiftfox.data.prefs.SiteMode
import app.overbay.swiftfox.data.prefs.ToolbarPlacement
import app.overbay.swiftfox.features.foldable.SiteModeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteModeResolverTest {

    private val foldedProfile = PostureProfile(ToolbarPlacement.SPLIT, SiteMode.MOBILE)
    private val unfoldedProfile = PostureProfile(ToolbarPlacement.TOP, SiteMode.DESKTOP, true)

    @Test
    fun respectsPrecedenceTabThenSiteThenPosture() {
        assertEquals(
            SiteMode.MOBILE,
            SiteModeResolver.resolve(null, null, foldedProfile)
        )
        assertEquals(
            SiteMode.DESKTOP,
            SiteModeResolver.resolve(null, null, unfoldedProfile)
        )
        assertEquals(
            SiteMode.MOBILE,
            SiteModeResolver.resolve(null, SiteMode.MOBILE, unfoldedProfile)
        )
        assertEquals(
            SiteMode.DESKTOP,
            SiteModeResolver.resolve(SiteMode.DESKTOP, SiteMode.MOBILE, foldedProfile)
        )
    }

    @Test
    fun reloadsOnlyWhenResolvedModeChangesOnWebUrl() {
        assertFalse(
            SiteModeResolver.shouldReloadOnTransition(
                previousMode = SiteMode.MOBILE,
                newMode = SiteMode.MOBILE,
                currentUrl = "https://github.com"
            )
        )
        assertTrue(
            SiteModeResolver.shouldReloadOnTransition(
                previousMode = SiteMode.MOBILE,
                newMode = SiteMode.DESKTOP,
                currentUrl = "https://github.com"
            )
        )
        assertFalse(
            SiteModeResolver.shouldReloadOnTransition(
                previousMode = SiteMode.MOBILE,
                newMode = SiteMode.DESKTOP,
                currentUrl = "about:blank"
            )
        )
    }
}
