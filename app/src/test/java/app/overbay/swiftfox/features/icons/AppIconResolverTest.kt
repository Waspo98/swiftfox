package app.overbay.swiftfox.features.icons

import app.overbay.swiftfox.data.prefs.AccentPreset
import app.overbay.swiftfox.data.prefs.AppIconBackgroundMode
import app.overbay.swiftfox.data.prefs.AppIconColorStyle
import app.overbay.swiftfox.data.prefs.AppIconPalette
import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.data.prefs.ThemeMode
import app.overbay.swiftfox.ui.theme.SwiftfoxBrandAppearance
import app.overbay.swiftfox.ui.theme.SwiftfoxEmblemVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconResolverTest {

    @Test
    fun defaultsResolveToDarkChannelDefaultOnMainAndBeta() {
        val defaults = BrowserSettings()

        val mainResolved = defaults.resolveAppIcon(isChromeDark = false, isBetaBuild = false)
        assertEquals(AppIconPalette.FOX_AMBER, mainResolved.palette)
        assertFalse(mainResolved.isLightBackground)
        assertTrue(mainResolved.usesChannelDefaultAlias)
        assertEquals(LauncherIconManager.ALIAS_DEFAULT_DARK, LauncherIconManager.aliasNameFor(mainResolved))

        val betaResolved = defaults.resolveAppIcon(isChromeDark = false, isBetaBuild = true)
        assertEquals(AppIconPalette.BETA_VIOLET, betaResolved.palette)
        assertFalse(betaResolved.isLightBackground)
        assertTrue(betaResolved.usesChannelDefaultAlias)
        assertEquals(LauncherIconManager.ALIAS_DEFAULT_DARK, LauncherIconManager.aliasNameFor(betaResolved))
    }

    @Test
    fun lightAndFollowThemeBackgroundsResolveCorrectly() {
        val explicitLight = BrowserSettings(appIconBackgroundMode = AppIconBackgroundMode.LIGHT)
        val lightOnDarkChrome = explicitLight.resolveAppIcon(isChromeDark = true, isBetaBuild = true)
        assertTrue(lightOnDarkChrome.isLightBackground)
        assertEquals(LauncherIconManager.ALIAS_DEFAULT_LIGHT, LauncherIconManager.aliasNameFor(lightOnDarkChrome))

        val followTheme = BrowserSettings(appIconBackgroundMode = AppIconBackgroundMode.FOLLOW_THEME)
        val autoWhenDark = followTheme.resolveAppIcon(isChromeDark = true, isBetaBuild = false)
        assertFalse(autoWhenDark.isLightBackground)
        assertEquals(LauncherIconManager.ALIAS_DEFAULT_DARK, LauncherIconManager.aliasNameFor(autoWhenDark))

        val autoWhenLight = followTheme.resolveAppIcon(isChromeDark = false, isBetaBuild = false)
        assertTrue(autoWhenLight.isLightBackground)
        assertEquals(LauncherIconManager.ALIAS_DEFAULT_LIGHT, LauncherIconManager.aliasNameFor(autoWhenLight))

        assertFalse(ThemeMode.DAY.isDarkActive(isSystemDark = true))
        assertTrue(ThemeMode.NIGHT.isDarkActive(isSystemDark = false))
        assertTrue(ThemeMode.SYSTEM.isDarkActive(isSystemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDarkActive(isSystemDark = false))
    }

    @Test
    fun matchAppAccentMapsAllAccentPresetsAndDistinguishesMainVsBetaAmber() {
        val amberBeta = BrowserSettings(
            accentPreset = AccentPreset.FOX_AMBER,
            appIconColorStyle = AppIconColorStyle.CHANNEL_DEFAULT,
            appIconMatchAccent = true,
            appIconBackgroundMode = AppIconBackgroundMode.DARK
        ).resolveAppIcon(isChromeDark = true, isBetaBuild = true)
        assertEquals(AppIconPalette.FOX_AMBER, amberBeta.palette)
        assertFalse(amberBeta.usesChannelDefaultAlias)
        assertEquals(LauncherIconManager.ALIAS_AMBER_DARK, LauncherIconManager.aliasNameFor(amberBeta))

        val amberMain = BrowserSettings(
            accentPreset = AccentPreset.FOX_AMBER,
            appIconMatchAccent = true,
            appIconBackgroundMode = AppIconBackgroundMode.LIGHT
        ).resolveAppIcon(isChromeDark = false, isBetaBuild = false)
        assertEquals(AppIconPalette.FOX_AMBER, amberMain.palette)
        assertTrue(amberMain.usesChannelDefaultAlias)
        assertEquals(LauncherIconManager.ALIAS_DEFAULT_LIGHT, LauncherIconManager.aliasNameFor(amberMain))

        val indigo = BrowserSettings(
            accentPreset = AccentPreset.MIDNIGHT_INDIGO,
            appIconMatchAccent = true,
            appIconBackgroundMode = AppIconBackgroundMode.LIGHT
        ).resolveAppIcon(isChromeDark = true, isBetaBuild = true)
        assertEquals(AppIconPalette.MIDNIGHT_INDIGO, indigo.palette)
        assertEquals(LauncherIconManager.ALIAS_INDIGO_LIGHT, LauncherIconManager.aliasNameFor(indigo))

        val forest = BrowserSettings(
            accentPreset = AccentPreset.FOREST,
            appIconMatchAccent = true,
            appIconBackgroundMode = AppIconBackgroundMode.DARK
        ).resolveAppIcon(isChromeDark = false, isBetaBuild = false)
        assertEquals(AppIconPalette.FOREST, forest.palette)
        assertEquals(LauncherIconManager.ALIAS_FOREST_DARK, LauncherIconManager.aliasNameFor(forest))

        val mono = BrowserSettings(
            accentPreset = AccentPreset.MONOCHROME,
            appIconMatchAccent = true,
            appIconBackgroundMode = AppIconBackgroundMode.LIGHT
        ).resolveAppIcon(isChromeDark = false, isBetaBuild = true)
        assertEquals(AppIconPalette.MONOCHROME, mono.palette)
        assertEquals(LauncherIconManager.ALIAS_MONOCHROME_LIGHT, LauncherIconManager.aliasNameFor(mono))
    }

    @Test
    fun syncInAppControlsBrandAppearanceVariants() {
        val lightForestResolved = BrowserSettings(
            appIconBackgroundMode = AppIconBackgroundMode.LIGHT,
            appIconColorStyle = AppIconColorStyle.FOREST
        ).resolveAppIcon(isChromeDark = false, isBetaBuild = true)

        val unsynced = SwiftfoxBrandAppearance(
            syncInApp = false,
            isLightChrome = true,
            resolvedIcon = lightForestResolved
        )
        assertEquals(SwiftfoxEmblemVariant.ORANGE, unsynced.effectiveEmblemVariant)
        assertFalse(unsynced.effectiveAppIconLightBackground)

        val synced = SwiftfoxBrandAppearance(
            syncInApp = true,
            isLightChrome = true,
            resolvedIcon = lightForestResolved
        )
        assertEquals(SwiftfoxEmblemVariant.FOREST, synced.effectiveEmblemVariant)
        assertEquals(SwiftfoxEmblemVariant.FOREST, synced.effectiveAppIconVariant)
        assertTrue(synced.effectiveAppIconLightBackground)
    }

    @Test
    fun everyNonBetaAliasMappingIsDistinct() {
        val resolvedIcons = listOf(
            AppIconPalette.BETA_VIOLET to false,
            AppIconPalette.BETA_VIOLET to true,
            AppIconPalette.FOX_AMBER to false,
            AppIconPalette.FOX_AMBER to true,
            AppIconPalette.MIDNIGHT_INDIGO to false,
            AppIconPalette.MIDNIGHT_INDIGO to true,
            AppIconPalette.FOREST to false,
            AppIconPalette.FOREST to true,
            AppIconPalette.MONOCHROME to false,
            AppIconPalette.MONOCHROME to true
        ).map { (palette, isLight) ->
            LauncherIconManager.aliasNameFor(
                AppIconColorStyle.entries.let {
                    app.overbay.swiftfox.data.prefs.ResolvedAppIcon(
                        palette = palette,
                        isLightBackground = isLight,
                        usesChannelDefaultAlias = palette == AppIconPalette.BETA_VIOLET
                    )
                }
            )
        }
        assertEquals(LauncherIconManager.ALL_ALIAS_NAMES.toSet(), resolvedIcons.toSet())
    }
}
