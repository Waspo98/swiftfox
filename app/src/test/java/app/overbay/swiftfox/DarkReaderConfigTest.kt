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
        val amoledFile = listOf(
            File("src/main/assets/js/darkreader_amoled.js"),
            File("app/src/main/assets/js/darkreader_amoled.js")
        ).first { it.exists() }
        val js = bridgeFile.readText(Charsets.UTF_8)
        val amoledJs = amoledFile.readText(Charsets.UTF_8)

        // Neutral zero-saturation poles prevent Dark Reader from tinting neutral gray text violet
        assertTrue(js.contains("darkSchemeBackgroundColor: config.amoled ? '#000000' : '#181818'"))
        assertTrue(js.contains("darkSchemeTextColor: config.amoled ? '#ffffff' : '#e8e8e8'"))
        assertFalse(js.contains("#f3eef7"))
        assertFalse(js.contains("#ece7f0"))

        // AMOLED mode overrides native dark sites instead of leaving dark-gray pages untouched
        assertTrue(js.contains("config.skipNativeDark && !config.forceOverride && !config.amoled"))

        // Shared AMOLED CSS-variable floor snaps near-black neutral surfaces to #000000 and primary text to #ffffff
        // across both Dark Reader's core variables stylesheet and root-vars stylesheet
        assertTrue(amoledJs.contains("darkreader--variables"))
        assertTrue(amoledJs.contains("darkreader--root-vars"))
        assertTrue(amoledJs.contains("--darkreader-background-"))
        assertTrue(amoledJs.contains("--darkreader-bg--"))
        assertTrue(amoledJs.contains("--darkreader-text-"))
        assertTrue(amoledJs.contains("normalizeAmoledVarValue"))
        assertTrue(js.contains("createAmoledController"))

        // Deferred DOMContentLoaded & dark-signal-gated mutation/load recheck avoids flashing light sites
        assertTrue(js.contains("attachNativeDarkMutationObserver"))
        assertTrue(js.contains("hasNewDarkThemeSignal"))
        assertTrue(js.contains("!nativeDarkCache.settled || hasNewDarkThemeSignal()"))
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

    @Test
    fun globalPrepaintDarkReaderResolvesSubframeInClosureWithoutLeakingHostLists() {
        val script = ScriptInjector.buildGlobalPrepaintDarkScript(
            GlobalPrepaintDarkConfig(
                mode = PrepaintDarkMode.DARK_READER,
                amoled = true,
                brightness = 110,
                contrast = 105,
                sepia = 15,
                skipNativeDark = true,
                globalEnabled = true,
                excludedHosts = setOf("light.example.com"),
                includedHosts = setOf("forced.example.com")
            )
        )
        // Host override lists stay strictly closure-local and are NEVER assigned onto window
        assertFalse(script.contains("__sfDarkSubframeCfg"))
        assertFalse(script.contains("\"excludedHosts\""))
        assertFalse(script.contains("\"includedHosts\""))
        assertTrue(script.contains("ancestorOrigins"))
        assertTrue(script.contains("sfCfg.forceOverride = sfIsIncluded;"))
        assertTrue(script.contains("__sfDarkSubframeBoot"))
        assertTrue(script.contains("enumerable: false"))
        assertTrue(script.contains("\"amoled\":true"))
        assertTrue(script.contains("\"brightness\":110"))
        assertTrue(script.contains("\"contrast\":105"))
        assertTrue(script.contains("\"sepia\":15"))
        assertTrue(script.contains("\"skipNativeDark\":true"))
        assertFalse(script.contains("SwiftfoxNativeChannel"))

        // Algorithmic Lite does not inject Dark Reader subframe handoff
        val liteScript = ScriptInjector.buildGlobalPrepaintDarkScript(
            GlobalPrepaintDarkConfig(
                mode = PrepaintDarkMode.ALGORITHMIC_LITE,
                amoled = false,
                globalEnabled = true
            )
        )
        assertFalse(liteScript.contains("__sfDarkSubframeBoot"))
        assertFalse(liteScript.contains("__sfDarkSubframePending"))
    }

    @Test
    fun subframeDarkReaderRuntimeAndMainBridgeSyncIframesSafely() {
        val subframeFile = listOf(
            File("src/main/assets/js/darkreader_subframe.js"),
            File("app/src/main/assets/js/darkreader_subframe.js")
        ).firstOrNull { it.exists() }
        val amoledFile = listOf(
            File("src/main/assets/js/darkreader_amoled.js"),
            File("app/src/main/assets/js/darkreader_amoled.js")
        ).firstOrNull { it.exists() }
        assertTrue("Expected darkreader_subframe.js asset to exist", subframeFile != null)
        assertTrue("Expected darkreader_amoled.js asset to exist", amoledFile != null)
        val subframeJs = subframeFile!!.readText(Charsets.UTF_8)
        val amoledJs = amoledFile!!.readText(Charsets.UTF_8)

        // Exits immediately on main frame, cleans up transient handoff keys, never references native channel
        assertTrue(subframeJs.contains("window === window.top"))
        assertFalse(subframeJs.contains("SwiftfoxNativeChannel"))
        assertTrue(subframeJs.contains("delete window[HANDOFF_CFG_KEY]"))
        assertTrue(subframeJs.contains("delete window[HANDOFF_BOOT_KEY]"))
        // Defers collapsed 0x0 iframes (e.g. tweets before hydration resize) without wasting CPU on trackers
        assertTrue(subframeJs.contains("ResizeObserver"))
        // Re-evaluates native dark mode even when toggling off AMOLED / enabling skipNativeDark live
        assertTrue(subframeJs.contains("evaluateSubframeNativeDark"))
        // Live sync via parent/top-verified postMessage
        assertTrue(subframeJs.contains("__swiftfoxDarkSync"))
        assertTrue(subframeJs.contains("event.source !== window.parent && event.source !== window.top"))
        // Skips sensitive/media embeds (CAPTCHAs, payments, pure video players)
        assertTrue(subframeJs.contains("challenges.cloudflare.com"))
        assertTrue(subframeJs.contains("js.stripe.com"))
        assertTrue(subframeJs.contains("youtube.com"))
        // Uses shared AMOLED controller without duplicating normalizeAmoledVarValue
        assertTrue(subframeJs.contains("createAmoledController"))
        assertFalse(subframeJs.contains("function normalizeAmoledVarValue"))

        // Main-frame bridge and disable script broadcast live sync to child iframes
        val bridgeFile = listOf(
            File("src/main/assets/js/darkreader_bridge.js"),
            File("app/src/main/assets/js/darkreader_bridge.js")
        ).first { it.exists() }
        val bridgeJs = bridgeFile.readText(Charsets.UTF_8)
        assertTrue(bridgeJs.contains("__swiftfoxDarkSync"))
        assertTrue(bridgeJs.contains("postMessage"))
        assertFalse(bridgeJs.contains("function normalizeAmoledVarValue"))

        val injector = DarkReaderInjector(null)
        val disableScript = injector.buildDisableScript("drKey1")
        assertTrue(disableScript.contains("__swiftfoxDarkSync"))

        // Missing assets return empty string so BrowserSession skips registering a broken stub
        assertEquals("", injector.buildSubframeRuntimeScript())
        assertEquals("", injector.assembleSubframeRuntimeScript("", amoledJs, subframeJs))
        assertEquals("", injector.assembleSubframeRuntimeScript("window.DarkReader={};", "", subframeJs))
        assertEquals("", injector.assembleSubframeRuntimeScript("window.DarkReader={};", amoledJs, ""))

        // Full assembled script wraps DarkReader, shared AMOLED helper, and subframe runtime without tokens
        val assembled = injector.assembleSubframeRuntimeScript("window.DarkReader={};", amoledJs, subframeJs)
        assertTrue(assembled.contains("window===window.top"))
        assertTrue(assembled.contains("createAmoledController"))
        assertTrue(assembled.contains("(ensureDarkReader,createAmoled);"))
        assertFalse(assembled.contains("SwiftfoxNativeChannel"))
    }
}
