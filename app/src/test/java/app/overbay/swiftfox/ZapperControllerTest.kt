package app.overbay.swiftfox

import app.overbay.swiftfox.data.db.ZappedRuleEntity
import app.overbay.swiftfox.features.zapper.ZapperController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZapperControllerTest {

    private val controller = ZapperController(context = null)

    @Test
    fun buildCssForRulesFiltersDisabledAndDeduplicatesSelectors() {
        val rules = listOf(
            ZappedRuleEntity(
                id = "1",
                host = "example.com",
                selector = "#cookie-banner",
                label = "div#cookie-banner",
                enabled = true
            ),
            ZappedRuleEntity(
                id = "2",
                host = "example.com",
                selector = ".sticky-footer",
                label = "footer.sticky-footer",
                enabled = false
            ),
            ZappedRuleEntity(
                id = "3",
                host = "example.com",
                selector = "#cookie-banner",
                label = "duplicate",
                enabled = true
            ),
            ZappedRuleEntity(
                id = "4",
                host = "example.com",
                selector = "main > aside.promo:nth-of-type(2)",
                label = "aside.promo",
                enabled = true
            )
        )

        val css = controller.buildCssForRules(rules)

        assertEquals(
            "#cookie-banner { display: none !important; }\n" +
                "main > aside.promo:nth-of-type(2) { display: none !important; }",
            css
        )
        assertFalse(css.contains(".sticky-footer"))
    }

    @Test
    fun buildCssForRulesBlocksStyleBreakoutAndInjection() {
        val rules = listOf(
            ZappedRuleEntity(
                id = "xss1",
                host = "evil.example",
                selector = ".ad</style><script>alert(1)</script>",
                enabled = true
            ),
            ZappedRuleEntity(
                id = "xss2",
                host = "evil.example",
                selector = ".ad</STYLE><img src=x>",
                enabled = true
            ),
            ZappedRuleEntity(
                id = "css1",
                host = "evil.example",
                selector = ".ad { background: url(https://evil.example/log); }",
                enabled = true
            ),
            ZappedRuleEntity(
                id = "css2",
                host = "evil.example",
                selector = ".ad; body { display: block }",
                enabled = true
            ),
            ZappedRuleEntity(
                id = "css3",
                host = "evil.example",
                selector = "@import url('https://evil.example/a.css')",
                enabled = true
            ),
            ZappedRuleEntity(
                id = "ok1",
                host = "evil.example",
                selector = "[data-testid=\"newsletter-modal\"]",
                enabled = true
            )
        )

        val css = controller.buildCssForRules(rules)

        assertEquals("[data-testid=\"newsletter-modal\"] { display: none !important; }", css)
        assertFalse(css.lowercase().contains("</style"))
        assertFalse(css.contains("<script"))
        assertFalse(css.contains("alert(1)"))
        assertFalse(css.contains("@import"))
        assertFalse(css.contains("background:"))
    }

    @Test
    fun buildCssForRulesReturnsEmptyWhenNoEnabledValidRules() {
        val rules = listOf(
            ZappedRuleEntity(id = "1", host = "a.com", selector = "   ", enabled = true),
            ZappedRuleEntity(id = "2", host = "a.com", selector = ".banner", enabled = false)
        )

        assertEquals("", controller.buildCssForRules(rules))
    }

    @Test
    fun parseSelectionEventParsesValidJsonAndClampsFields() {
        val json = """
            {
              "selector": "#paywall > div.overlay",
              "summary": "div.overlay",
              "scopeDepth": 2,
              "maxScopeDepth": 5,
              "matchCount": 3,
              "extraField": "ignored"
            }
        """.trimIndent()

        val parsed = controller.parseSelectionEvent(json)

        assertNotNull(parsed)
        assertEquals("#paywall > div.overlay", parsed!!.selector)
        assertEquals("div.overlay", parsed.summary)
        assertEquals(2, parsed.scopeDepth)
        assertEquals(5, parsed.maxScopeDepth)
        assertEquals(3, parsed.matchCount)
    }

    @Test
    fun parseSelectionEventRejectsMalformedOrUnsafeSelectors() {
        assertNull(controller.parseSelectionEvent(""))
        assertNull(controller.parseSelectionEvent("not valid json"))
        assertNull(
            controller.parseSelectionEvent(
                """{"selector":"","summary":"div","scopeDepth":0,"maxScopeDepth":1,"matchCount":1}"""
            )
        )
        assertNull(
            controller.parseSelectionEvent(
                """{"selector":".x</style><script>alert(1)</script>","summary":"div","scopeDepth":0,"maxScopeDepth":1,"matchCount":1}"""
            )
        )
    }

    @Test
    fun parsePermanentRuleEventParsesValidJsonAndRejectsUnsafeSelectors() {
        val valid = controller.parsePermanentRuleEvent(
            """{"selector":"aside.ad-rail","label":"  aside.ad-rail  ","matchCount":4}"""
        )
        assertNotNull(valid)
        assertEquals("aside.ad-rail", valid!!.selector)
        assertEquals("aside.ad-rail", valid.label)
        assertEquals(4, valid.matchCount)

        assertNull(controller.parsePermanentRuleEvent("invalid-json"))
        assertNull(controller.parsePermanentRuleEvent("""{"selector":"div { color: red }","label":"div","matchCount":1}"""))
        assertNull(controller.parsePermanentRuleEvent("""{"selector":"   ","label":"blank","matchCount":0}"""))
    }

    @Test
    fun parseTemporaryZapAndUndoEventsHandleValidAndInvalidInput() {
        val temp = controller.parseTemporaryZapEvent(
            """{"summary":"div.banner","temporaryZapCount":2}"""
        )
        assertNotNull(temp)
        assertEquals("div.banner", temp!!.summary)
        assertEquals(2, temp.temporaryZapCount)

        assertEquals(1, controller.parseUndoCount("""{"temporaryZapCount":1}"""))
        assertEquals(0, controller.parseUndoCount("invalid"))
        assertEquals(0, controller.parseUndoCount("""{"temporaryZapCount":-3}"""))
    }

    @Test
    fun scriptBuildersFormatSafeJavascriptInvocations() {
        val startScript = controller.buildStartScript("#FF9147")
        assertTrue(startScript.contains("""window.__swiftfoxZapper.start("#FF9147")"""))

        val sanitizedStart = controller.buildStartScript("red; alert(1)")
        assertTrue(sanitizedStart.contains("""window.__swiftfoxZapper.start("#E86A17")"""))
        assertFalse(sanitizedStart.contains("alert(1)"))

        assertTrue(controller.buildSetScopeScript(3).contains("setScopeDepth(3)"))
        assertTrue(controller.buildSetScopeScript(-2).contains("setScopeDepth(0)"))
        assertTrue(controller.buildAdjustScopeScript(-1).contains("adjustScope(-1)"))

        val applyScript = controller.buildApplyZappedCssScript(
            listOf(
                ZappedRuleEntity(
                    id = "1",
                    host = "example.com",
                    selector = "div.annoying",
                    enabled = true
                )
            )
        )
        assertTrue(applyScript.contains("window.__swiftfoxApplyZappedCss"))
        assertTrue(applyScript.contains("div.annoying { display: none !important; }"))
    }
}
