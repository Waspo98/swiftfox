package app.overbay.swiftfox

import app.overbay.swiftfox.data.db.SiteOverrideEntity
import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.data.prefs.WebDarkEngine
import app.overbay.swiftfox.data.prefs.WebDarkModePreference
import app.overbay.swiftfox.engine.GlobalPrepaintDarkConfig
import app.overbay.swiftfox.engine.PrepaintDarkMode
import app.overbay.swiftfox.engine.ScriptInjector
import app.overbay.swiftfox.features.darkreader.DarkReaderConfig
import app.overbay.swiftfox.features.darkreader.DarkReaderInjector
import app.overbay.swiftfox.features.darkreader.DarkReaderStatusEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DarkReaderConfigTest {

    @Test
    fun resolvesGlobalPreferenceAcrossOffFollowAppAndAlwaysOn() {
        val offSettings = BrowserSettings(webDarkMode = WebDarkModePreference.OFF)
        assertFalse(DarkReaderConfig.resolve(offSettings, null, isChromeDark = false).enabled)
        assertFalse(DarkReaderConfig.resolve(offSettings, null, isChromeDark = true).enabled)

        val followAppSettings = BrowserSettings(webDarkMode = WebDarkModePreference.FOLLOW_APP)
        assertFalse(DarkReaderConfig.resolve(followAppSettings, null, isChromeDark = false).enabled)
        assertTrue(DarkReaderConfig.resolve(followAppSettings, null, isChromeDark = true).enabled)

        val alwaysOnSettings = BrowserSettings(webDarkMode = WebDarkModePreference.ALWAYS_ON)
        assertTrue(DarkReaderConfig.resolve(alwaysOnSettings, null, isChromeDark = false).enabled)
        assertTrue(DarkReaderConfig.resolve(alwaysOnSettings, null, isChromeDark = true).enabled)
    }

    @Test
    fun appliesPerSiteOverridesAndTracksIsSiteOverrideFlag() {
        val alwaysOnSettings = BrowserSettings(webDarkMode = WebDarkModePreference.ALWAYS_ON)
        val forceOff = SiteOverrideEntity(host = "example.com", webDarkModeOverride = false)
        val offResolved = DarkReaderConfig.resolve(alwaysOnSettings, forceOff, isChromeDark = true)
        assertFalse(offResolved.enabled)
        assertTrue(offResolved.isSiteOverride)

        val offSettings = BrowserSettings(webDarkMode = WebDarkModePreference.OFF)
        val forceOn = SiteOverrideEntity(host = "example.com", webDarkModeOverride = true)
        val onResolved = DarkReaderConfig.resolve(offSettings, forceOn, isChromeDark = false)
        assertTrue(onResolved.enabled)
        assertTrue(onResolved.isSiteOverride)

        val neutralSite = SiteOverrideEntity(host = "example.com", webDarkModeOverride = null)
        val neutralResolved = DarkReaderConfig.resolve(alwaysOnSettings, neutralSite, isChromeDark = false)
        assertTrue(neutralResolved.enabled)
        assertFalse(neutralResolved.isSiteOverride)
    }

    @Test
    fun exposesDarkReaderVsAlgorithmicLiteEngineFlags() {
        val drSettings = BrowserSettings(
            webDarkMode = WebDarkModePreference.ALWAYS_ON,
            webDarkEngine = WebDarkEngine.DARK_READER
        )
        val drState = DarkReaderConfig.resolve(drSettings, null, isChromeDark = true)
        assertTrue(drState.useDarkReader)
        assertFalse(drState.useAlgorithmicLite)

        val liteSettings = BrowserSettings(
            webDarkMode = WebDarkModePreference.ALWAYS_ON,
            webDarkEngine = WebDarkEngine.ALGORITHMIC_LITE
        )
        val liteState = DarkReaderConfig.resolve(liteSettings, null, isChromeDark = true)
        assertFalse(liteState.useDarkReader)
        assertTrue(liteState.useAlgorithmicLite)

        val disabledLite = DarkReaderConfig.resolve(
            liteSettings.copy(webDarkMode = WebDarkModePreference.OFF),
            null,
            isChromeDark = false
        )
        assertFalse(disabledLite.useDarkReader)
        assertFalse(disabledLite.useAlgorithmicLite)
    }

    @Test
    fun serializesConfigJsonWithAmoledSlidersSkipNativeDarkAndForceOverride() {
        val settings = BrowserSettings(
            webDarkMode = WebDarkModePreference.ALWAYS_ON,
            webDarkEngine = WebDarkEngine.DARK_READER,
            darkReaderAmoled = true,
            darkReaderBrightness = 115,
            darkReaderContrast = 120,
            darkReaderSepia = 25,
            darkReaderSkipNativeDark = true
        )
        val siteOverride = SiteOverrideEntity(host = "github.com", webDarkModeOverride = true)
        val state = DarkReaderConfig.resolve(settings, siteOverride, isChromeDark = false)

        val obj = Json.parseToJsonElement(DarkReaderConfig.toConfigJson(state)).jsonObject
        assertTrue(obj["enabled"]!!.jsonPrimitive.boolean)
        assertTrue(obj["amoled"]!!.jsonPrimitive.boolean)
        assertEquals(115, obj["brightness"]!!.jsonPrimitive.int)
        assertEquals(120, obj["contrast"]!!.jsonPrimitive.int)
        assertEquals(25, obj["sepia"]!!.jsonPrimitive.int)
        assertTrue(obj["skipNativeDark"]!!.jsonPrimitive.boolean)
        assertTrue(obj["forceOverride"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun injectorBuildsScriptsAndParsesStatusJsonSafely() {
        val injector = DarkReaderInjector(null)
        val liteState = DarkReaderConfig.resolve(
            BrowserSettings(
                webDarkMode = WebDarkModePreference.ALWAYS_ON,
                webDarkEngine = WebDarkEngine.ALGORITHMIC_LITE
            ),
            null,
            isChromeDark = true
        )
        val disableScript = injector.buildApplyScript(liteState)
        assertEquals(injector.buildDisableScript(), disableScript)
        assertTrue(disableScript.contains("window[\"__swiftfoxDarkReader\"]"))
        assertTrue(disableScript.contains("rt.apply({enabled:false})"))
        assertTrue(disableScript.contains("DarkReader.disable()"))

        val drState = DarkReaderConfig.resolve(
            BrowserSettings(
                webDarkMode = WebDarkModePreference.ALWAYS_ON,
                webDarkEngine = WebDarkEngine.DARK_READER,
                darkReaderAmoled = true
            ),
            null,
            isChromeDark = true
        )
        val applyScript = injector.buildApplyScript(drState, bridgeToken = "tok123", runtimeKey = "drKey1")
        assertTrue(applyScript.startsWith("(function(){"))
        assertTrue(applyScript.contains("window[\"drKey1\"]"))
        assertTrue(applyScript.contains("rt.apply("))
        assertTrue(applyScript.contains("\"amoled\":true"))

        val fastScript = injector.buildFastApplyScript(drState, runtimeKey = "drKey1")
        assertTrue(fastScript.contains("window[\"drKey1\"]"))
        assertTrue(fastScript.contains("rt.apply("))
        assertTrue(fastScript.contains("\"amoled\":true"))
        assertTrue(fastScript.contains("return 'ok'"))
        assertTrue(fastScript.contains("return 'missing'"))
        assertTrue(fastScript.length < 400)

        assertEquals(
            DarkReaderStatusEvent(skippedNativeDark = false),
            injector.parseStatusJson("""{"active":true,"skippedNativeDark":false}""")
        )
        assertEquals(
            DarkReaderStatusEvent(skippedNativeDark = true),
            injector.parseStatusJson("""{"active":false,"skippedNativeDark":true}""")
        )
        assertNull(injector.parseStatusJson("not-json"))
        assertNull(injector.parseStatusJson(""))
    }

    @Test
    fun globalPrepaintScriptHonorsGlobalModeOverridesAmoledAndStaysTokenFree() {
        assertEquals(
            "",
            ScriptInjector.buildGlobalPrepaintDarkScript(
                GlobalPrepaintDarkConfig(
                    mode = PrepaintDarkMode.NONE,
                    globalEnabled = true
                )
            )
        )
        assertEquals(
            "",
            ScriptInjector.buildGlobalPrepaintDarkScript(
                GlobalPrepaintDarkConfig(
                    mode = PrepaintDarkMode.DARK_READER,
                    globalEnabled = false,
                    includedHosts = emptySet()
                )
            )
        )

        val darkReaderGlobal = ScriptInjector.buildGlobalPrepaintDarkScript(
            GlobalPrepaintDarkConfig(
                mode = PrepaintDarkMode.DARK_READER,
                amoled = true,
                globalEnabled = true,
                excludedHosts = setOf("WWW.Example.com", "light.org")
            )
        )
        assertTrue(darkReaderGlobal.contains("swiftfox-prepaint-dark"))
        assertTrue(darkReaderGlobal.contains("#000000!important"))
        assertTrue(darkReaderGlobal.contains("color:#ffffff!important"))
        assertFalse(darkReaderGlobal.contains("#f3eef7"))
        assertTrue(darkReaderGlobal.contains("color-scheme:dark!important"))
        assertTrue(darkReaderGlobal.contains("\"example.com\""))
        assertTrue(darkReaderGlobal.contains("\"light.org\""))
        assertTrue(darkReaderGlobal.contains("list.indexOf(host) !== -1"))
        assertTrue(darkReaderGlobal.contains("window !== window.top"))
        assertFalse(darkReaderGlobal.contains("SwiftfoxNativeChannel"))

        val allowlistOnly = ScriptInjector.buildGlobalPrepaintDarkScript(
            GlobalPrepaintDarkConfig(
                mode = PrepaintDarkMode.DARK_READER,
                amoled = false,
                globalEnabled = false,
                includedHosts = setOf("www.news.ycombinator.com")
            )
        )
        assertTrue(allowlistOnly.contains("#181818!important"))
        assertTrue(allowlistOnly.contains("color:#e8e8e8!important"))
        assertFalse(allowlistOnly.contains("#ece7f0"))
        assertTrue(allowlistOnly.contains("\"news.ycombinator.com\""))
        assertTrue(allowlistOnly.contains("list.indexOf(host) === -1"))

        val liteGlobal = ScriptInjector.buildGlobalPrepaintDarkScript(
            GlobalPrepaintDarkConfig(
                mode = PrepaintDarkMode.ALGORITHMIC_LITE,
                amoled = false,
                globalEnabled = true
            )
        )
        assertTrue(liteGlobal.contains("html{background-color:#181818;color-scheme:dark;}"))
        assertFalse(liteGlobal.contains("!important"))
        assertTrue(liteGlobal.contains("DOMContentLoaded"))
    }

    @Test
    fun darkReaderBridgeUsesNeutralColorPolesAndAmoledOverride() {
        val bridgeFile = listOf(
            File("src/main/assets/js/darkreader_bridge.js"),
            File("app/src/main/assets/js/darkreader_bridge.js")
        ).first { it.exists() }
        val js = bridgeFile.readText(Charsets.UTF_8)

        // Neutral zero-saturation poles prevent Dark Reader from tinting neutral gray text violet
        assertTrue(js.contains("darkSchemeBackgroundColor: config.amoled ? '#000000' : '#181818'"))
        assertTrue(js.contains("darkSchemeTextColor: config.amoled ? '#ffffff' : '#e8e8e8'"))
        assertFalse(js.contains("#f3eef7"))
        assertFalse(js.contains("#ece7f0"))

        // AMOLED mode overrides native dark sites instead of leaving dark-gray pages untouched
        assertTrue(js.contains("config.skipNativeDark && !config.forceOverride && !config.amoled"))

        // AMOLED CSS-variable floor snaps near-black neutral surfaces to #000000 and primary text to #ffffff
        assertTrue(js.contains("darkreader--variables"))
        assertTrue(js.contains("--darkreader-background-"))
        assertTrue(js.contains("--darkreader-text-"))
        assertTrue(js.contains("normalizeAmoledVarValue"))

        // Deferred DOMContentLoaded & mutation recheck catches JS-toggled native dark themes in standard dark mode
        assertTrue(js.contains("attachNativeDarkMutationObserver"))
        assertTrue(js.contains("setTimeout"))
    }

    @Test
    fun wrapOriginGuardedScriptGuardsExecutionToExactOrigin() {
        assertEquals("", ScriptInjector.wrapOriginGuardedScript("", "alert(1);"))
        assertEquals("", ScriptInjector.wrapOriginGuardedScript("https://example.com", ""))

        val wrapped = ScriptInjector.wrapOriginGuardedScript(
            expectedOrigin = "https://example.com:8443",
            script = "window.__dr();"
        )
        assertTrue(wrapped.contains("""if (!window.location || window.location.origin !== "https://example.com:8443") return;"""))
        assertTrue(wrapped.contains("window.__dr();"))
    }
}
