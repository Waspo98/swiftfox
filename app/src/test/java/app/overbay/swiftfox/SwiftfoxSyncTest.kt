package app.overbay.swiftfox

import app.overbay.swiftfox.data.db.EssentialEntity
import app.overbay.swiftfox.data.db.FilterListEntity
import app.overbay.swiftfox.data.db.SiteOverrideEntity
import app.overbay.swiftfox.data.db.SpaceEntity
import app.overbay.swiftfox.data.db.SwiftfoxDatabase
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
import app.overbay.swiftfox.features.adblock.BuiltInFilterLists
import app.overbay.swiftfox.features.sync.SwiftfoxBaselineItem
import app.overbay.swiftfox.features.sync.SwiftfoxSyncBaseline
import app.overbay.swiftfox.features.sync.SwiftfoxSyncCodec
import app.overbay.swiftfox.features.sync.SwiftfoxSyncLocalStore
import app.overbay.swiftfox.features.sync.SwiftfoxSyncReconciler
import app.overbay.swiftfox.features.sync.SwiftfoxSyncResult
import app.overbay.swiftfox.features.sync.Sync15SwiftfoxClient
import app.overbay.swiftfox.features.sync.Sync15Transport
import app.overbay.swiftfox.features.sync.SyncCrypto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SwiftfoxSyncTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun defaultSeedSpaces(): List<SpaceEntity> = listOf(
        SpaceEntity(id = SwiftfoxDatabase.DEFAULT_SPACE_ID, name = "Personal", iconKey = "home", sortOrder = 0, createdAt = 100L),
        SpaceEntity(id = "space_work", name = "Work", iconKey = "work", sortOrder = 1, createdAt = 101L),
        SpaceEntity(id = "space_reading", name = "Reading", iconKey = "book", sortOrder = 2, createdAt = 102L)
    )

    private fun defaultSeedEssentials(): List<EssentialEntity> = listOf(
        EssentialEntity(id = "ess_google", title = "Google", url = "https://www.google.com", domain = "google.com", sortOrder = 0),
        EssentialEntity(id = "ess_github", title = "GitHub", url = "https://github.com", domain = "github.com", sortOrder = 1),
        EssentialEntity(id = "ess_youtube", title = "YouTube", url = "https://m.youtube.com", domain = "youtube.com", sortOrder = 2),
        EssentialEntity(id = "ess_reddit", title = "Reddit", url = "https://www.reddit.com", domain = "reddit.com", sortOrder = 3),
        EssentialEntity(id = "ess_wiki", title = "Wikipedia", url = "https://en.wikipedia.org", domain = "wikipedia.org", sortOrder = 4),
        EssentialEntity(id = "ess_hn", title = "Hacker News", url = "https://news.ycombinator.com", domain = "news.ycombinator.com", sortOrder = 5)
    )

    @Test
    fun settingsEncodeDecodeRoundTripsAllSyncedPreferencesWithStableHash() {
        val custom = BrowserSettings(
            themeMode = ThemeMode.NIGHT,
            nightSurfaceStyle = NightSurfaceStyle.AMOLED,
            accentPreset = AccentPreset.MIDNIGHT_INDIGO,
            useDynamicColor = true,
            appIconBackgroundMode = AppIconBackgroundMode.FOLLOW_THEME,
            appIconColorStyle = AppIconColorStyle.FOREST,
            appIconMatchAccent = true,
            syncAppIconInApp = true,
            webDarkMode = WebDarkModePreference.ALWAYS_ON,
            darkReaderAmoled = true,
            darkReaderBrightness = 115,
            darkReaderContrast = 120,
            darkReaderSepia = 15,
            darkReaderSkipNativeDark = false,
            adblockEnabled = false,
            adblockCosmeticEnabled = false,
            userscriptsEnabled = false,
            readerFontFamily = ReaderFontFamily.SERIF,
            readerFontSizeSp = 22,
            readerLineHeight = 1.75f,
            readerSurfaceMode = ReaderSurfaceMode.SEPIA,
            foldedProfile = PostureProfile(ToolbarPlacement.BOTTOM, SiteMode.MOBILE, false),
            unfoldedProfile = PostureProfile(ToolbarPlacement.SIDE_RAIL, SiteMode.DESKTOP, false),
            searchEngine = SearchEngineId.KAGI,
            customSearchUrlTemplate = "https://example.org/search?q=%s",
            searchSuggestionsEnabled = false,
            httpsOnlyMode = false,
            externalAppLinkPolicy = ExternalAppLinkPolicy.ASK,
            pullToRefreshEnabled = false,
            swipeUrlBarSwitchTabs = false,
            hideToolbarsOnScroll = false,
            confirmClosePinnedOnSecondTap = false,
            tabAutoClearDuration = TabAutoClearDuration.HOURS_48,
            backgroundMediaEnabled = true,
            pictureInPictureEnabled = true,
            blockThirdPartyCookies = true
        )

        val encoded = SwiftfoxSyncCodec.encodeSettingsRecord(custom, updatedAt = 5000L)
        val decoded = SwiftfoxSyncCodec.decodeRecord(SwiftfoxSyncCodec.SETTINGS_BSO_ID, encoded)
            as SwiftfoxSyncCodec.DecodedSwiftfoxRecord.Settings

        assertEquals(5000L, decoded.updatedAt)
        assertEquals(SwiftfoxSyncCodec.settingsContentHash(custom), decoded.contentHash)
        assertEquals(custom.themeMode, decoded.settings.themeMode)
        assertEquals(custom.nightSurfaceStyle, decoded.settings.nightSurfaceStyle)
        assertEquals(custom.darkReaderAmoled, decoded.settings.darkReaderAmoled)
        assertEquals(1.75f, decoded.settings.readerLineHeight)
        assertEquals(ToolbarPlacement.SIDE_RAIL, decoded.settings.unfoldedProfile.toolbarPlacement)
        assertEquals(SearchEngineId.KAGI, decoded.settings.searchEngine)
        assertEquals(TabAutoClearDuration.HOURS_48, decoded.settings.tabAutoClearDuration)
        assertTrue(decoded.settings.backgroundMediaEnabled)
        assertTrue(decoded.settings.pictureInPictureEnabled)
        assertTrue(decoded.settings.blockThirdPartyCookies)
    }

    @Test
    fun browserSettingsAndSyncDefaultsKeepBackgroundMediaAndPipOff() {
        val defaults = BrowserSettings()
        assertFalse(defaults.backgroundMediaEnabled)
        assertFalse(defaults.pictureInPictureEnabled)

        val legacyRecord = buildJsonObject {
            put("id", JsonPrimitive(SwiftfoxSyncCodec.SETTINGS_BSO_ID))
            put("schema", JsonPrimitive(1))
            put("kind", JsonPrimitive(SwiftfoxSyncCodec.KIND_SETTINGS))
            put("updatedAt", JsonPrimitive(1000L))
            put("deleted", JsonPrimitive(false))
            put("data", buildJsonObject { put("themeMode", JsonPrimitive("NIGHT")) })
        }
        val decoded = SwiftfoxSyncCodec.decodeRecord(SwiftfoxSyncCodec.SETTINGS_BSO_ID, legacyRecord)
            as SwiftfoxSyncCodec.DecodedSwiftfoxRecord.Settings
        assertFalse(decoded.settings.backgroundMediaEnabled)
        assertFalse(decoded.settings.pictureInPictureEnabled)
    }

    @Test
    fun largeUserscriptsGzipRoundTripAndFutureSchemaRecordsAreIgnoredSafely() {
        val largeBody = buildString {
            append("// ==UserScript==\n// @name Big Script\n// @match https://example.com/*\n// ==/UserScript==\n")
            repeat(900) {
                append("console.log('line_$it');\n")
            }
        }
        val script = UserscriptEntity(
            id = "us_big_1",
            name = "Big Script",
            namespace = "swiftfox.test",
            version = "2.1",
            description = "Large test script",
            sourceUrl = "https://example.com/big.user.js",
            code = largeBody,
            matchesJson = """["https://example.com/*"]""",
            excludesJson = "[]",
            runAt = "document-start",
            grantsJson = "[]",
            enabled = true,
            installedAt = 1000L,
            updatedAt = 2000L
        )
        val encoded = SwiftfoxSyncCodec.encodeUserscriptRecord(script, updatedAt = 2000L)
        val dataObj = encoded["data"]!!.jsonObject
        assertTrue(dataObj.containsKey("codeGzipBase64"))
        assertFalse(dataObj.containsKey("code"))

        val decoded = SwiftfoxSyncCodec.decodeRecord(
            SwiftfoxSyncCodec.userscriptBsoId(script),
            encoded
        ) as SwiftfoxSyncCodec.DecodedSwiftfoxRecord.Userscript
        assertEquals(largeBody, decoded.entity.code)
        assertEquals("document-start", decoded.entity.runAt)
        assertEquals(SwiftfoxSyncCodec.userscriptContentHash(script), decoded.contentHash)

        // Future schema version > 1 must decode as UnsupportedFuture without crashing
        val futureObj = buildJsonObject {
            put("id", JsonPrimitive("future_rec"))
            put("schema", JsonPrimitive(99))
            put("kind", JsonPrimitive("settings"))
            put("updatedAt", JsonPrimitive(9999L))
        }
        val futureDecoded = SwiftfoxSyncCodec.decodeRecord("future_rec", futureObj)
        assertTrue(futureDecoded is SwiftfoxSyncCodec.DecodedSwiftfoxRecord.UnsupportedFuture)
    }

    @Test
    fun spaceAndEssentialDecodeSanitizesUnsafeIconsAndForcesNullZenGuids() {
        val badEss = buildJsonObject {
            put("id", JsonPrimitive("ess_custom_1"))
            put("schema", JsonPrimitive(1))
            put("kind", JsonPrimitive(SwiftfoxSyncCodec.KIND_ESSENTIAL))
            put("updatedAt", JsonPrimitive(1000L))
            put("deleted", JsonPrimitive(false))
            put(
                "data",
                buildJsonObject {
                    put("id", JsonPrimitive("ess_custom_1"))
                    put("title", JsonPrimitive("Docs"))
                    put("url", JsonPrimitive("https://kotlinlang.org"))
                    put("domain", JsonPrimitive("kotlinlang.org"))
                    put("localOrder", JsonPrimitive(0))
                    put("customIcon", JsonPrimitive("image:../../secret.png"))
                }
            )
        }
        val decoded = SwiftfoxSyncCodec.decodeRecord("ess_x", badEss)
            as SwiftfoxSyncCodec.DecodedSwiftfoxRecord.Essential
        assertNull(decoded.entity.customIcon)
        assertNull(decoded.entity.syncGuid)
        assertNull(decoded.entity.zenContainerGuid)
    }

    @Test
    fun freshSecondaryDevicePullsRemoteStateAndTombstonesWithoutOverwritingPrimary() {
        // Device A customized settings, enabled uBO Annoyances, deleted ess_reddit & space_work, added custom script + zap + override
        val deviceASettings = BrowserSettings(
            themeMode = ThemeMode.NIGHT,
            nightSurfaceStyle = NightSurfaceStyle.AMOLED,
            searchEngine = SearchEngineId.DUCKDUCKGO
        )
        val deviceASpacePersonal = SpaceEntity(SwiftfoxDatabase.DEFAULT_SPACE_ID, "Personal", "home", sortOrder = 0, createdAt = 100L)
        val deviceASpaceReading = SpaceEntity("space_reading", "Reading", "book", sortOrder = 1, createdAt = 102L)
        val deviceASpaceDev = SpaceEntity("space_dev", "Dev", "code", sortOrder = 2, createdAt = 500L)
        val deviceAEssGoogle = defaultSeedEssentials()[0]
        val deviceAEssCustom = EssentialEntity("ess_custom", null, "Overbay", "https://overbay.app", "overbay.app", sortOrder = 1, customIcon = "emoji:🦊")
        val deviceAOverride = SiteOverrideEntity(host = "example.com", adblockDisabled = true, webDarkModeOverride = false)
        val deviceAZap = ZappedRuleEntity("zap_1", "example.com", "div.promo", "Promo", enabled = true, createdAt = 300L)
        val deviceACustomList = FilterListEntity("fl_custom_1", "Custom List", "https://example.com/list.txt", enabled = true, isBuiltIn = false)
        val deviceAScript = UserscriptEntity(
            id = "us_1",
            name = "Tweaks",
            code = "// ==UserScript==\n// @name Tweaks\n// ==/UserScript==\nconsole.log(1);",
            updatedAt = 800L
        )
        val deviceAFilterLists = BuiltInFilterLists.defaults.map {
            if (it.id == BuiltInFilterLists.ID_UBO_ANNOYANCES) it.copy(enabled = true) else it
        } + deviceACustomList

        val planA = SwiftfoxSyncReconciler.buildPlan(
            local = SwiftfoxSyncReconciler.LocalSnapshot(
                settings = deviceASettings,
                settingsUpdatedAtMs = 1000L,
                siteOverrides = listOf(deviceAOverride),
                zappedRules = listOf(deviceAZap),
                filterLists = deviceAFilterLists,
                userscripts = listOf(deviceAScript),
                localSpaces = listOf(deviceASpacePersonal, deviceASpaceReading, deviceASpaceDev),
                zenSpacesCount = 0,
                localEssentials = listOf(deviceAEssGoogle, deviceAEssCustom),
                zenEssentialsCount = 0
            ),
            baseline = SwiftfoxSyncBaseline(),
            remoteRawRecords = emptyList(),
            nowMs = 1000L
        )

        // Simulate remote server records after Device A synced and subsequently deleted space_work & ess_reddit
        val remoteServer = linkedMapOf<String, JsonObject>()
        planA.outgoingBsos.forEach { (id, obj) -> remoteServer[id] = obj }
        val workTombstoneId = SwiftfoxSyncCodec.spaceBsoId("space_work")
        remoteServer[workTombstoneId] = SwiftfoxSyncCodec.encodeTombstone(workTombstoneId, SwiftfoxSyncCodec.KIND_SPACE, 1500L)
        val redditTombstoneId = SwiftfoxSyncCodec.essentialBsoId("ess_reddit")
        remoteServer[redditTombstoneId] = SwiftfoxSyncCodec.encodeTombstone(redditTombstoneId, SwiftfoxSyncCodec.KIND_ESSENTIAL, 1500L)

        // Device B is a brand-new install with default seeds and settingsUpdatedAtMs = 0L
        val planB = SwiftfoxSyncReconciler.buildPlan(
            local = SwiftfoxSyncReconciler.LocalSnapshot(
                settings = BrowserSettings(),
                settingsUpdatedAtMs = 0L,
                siteOverrides = emptyList(),
                zappedRules = emptyList(),
                filterLists = BuiltInFilterLists.defaults,
                userscripts = emptyList(),
                localSpaces = defaultSeedSpaces(),
                zenSpacesCount = 0,
                localEssentials = defaultSeedEssentials(),
                zenEssentialsCount = 0
            ),
            baseline = SwiftfoxSyncBaseline(),
            remoteRawRecords = remoteServer.entries.map { it.key to it.value },
            nowMs = 2000L
        )

        val appliedSettings = requireNotNull(planB.settingsToApply).first
        assertEquals(ThemeMode.NIGHT, appliedSettings.themeMode)
        assertEquals(SearchEngineId.DUCKDUCKGO, appliedSettings.searchEngine)
        assertEquals(1, planB.siteOverridesToUpsert.size)
        assertEquals("example.com", planB.siteOverridesToUpsert.single().host)
        assertEquals(1, planB.zappedRulesToUpsert.size)
        assertEquals(1, planB.userscriptsToUpsert.size)
        assertTrue(planB.filterListsToUpsert.any { it.id == BuiltInFilterLists.ID_UBO_ANNOYANCES && it.enabled })
        assertTrue(planB.filterListsToUpsert.any { it.url == "https://example.com/list.txt" })
        assertTrue("space_work" in planB.spacesPlan.deleteIds)
        assertTrue(planB.spacesPlan.upserts.any { it.id == "space_dev" })
        assertTrue("ess_reddit" in planB.essentialsPlan.deleteIds)
        assertTrue(planB.essentialsPlan.upserts.any { it.id == "ess_custom" && it.customIcon == "emoji:🦊" })
        // Device B must NOT overwrite Device A's settings on first sync
        assertFalse(planB.outgoingBsos.any { it.first == SwiftfoxSyncCodec.SETTINGS_BSO_ID })
    }

    @Test
    fun localDeletionProducesTombstoneAndZenSortOrderShiftDoesNotCauseSpuriousPushes() {
        val personal = SpaceEntity(SwiftfoxDatabase.DEFAULT_SPACE_ID, "Personal", "home", sortOrder = 0, createdAt = 100L)
        val zap = ZappedRuleEntity("z1", "news.example.com", "div.ad", "Ad", enabled = true, createdAt = 100L)

        // Initial sync with 0 Zen spaces
        val plan1 = SwiftfoxSyncReconciler.buildPlan(
            local = SwiftfoxSyncReconciler.LocalSnapshot(
                settings = BrowserSettings(),
                settingsUpdatedAtMs = 1000L,
                siteOverrides = emptyList(),
                zappedRules = listOf(zap),
                filterLists = BuiltInFilterLists.defaults,
                userscripts = emptyList(),
                localSpaces = listOf(personal),
                zenSpacesCount = 0,
                localEssentials = defaultSeedEssentials(),
                zenEssentialsCount = 0
            ),
            baseline = SwiftfoxSyncBaseline(),
            remoteRawRecords = emptyList(),
            nowMs = 1000L
        )
        val remote = plan1.outgoingBsos.toList()

        // Now Zen synced 3 Zen spaces, shifting local `personal.sortOrder` from 0 to 3, AND user deleted `zap`
        val shiftedPersonal = personal.copy(sortOrder = 3)
        val plan2 = SwiftfoxSyncReconciler.buildPlan(
            local = SwiftfoxSyncReconciler.LocalSnapshot(
                settings = BrowserSettings(),
                settingsUpdatedAtMs = 1000L,
                siteOverrides = emptyList(),
                zappedRules = emptyList(),
                filterLists = BuiltInFilterLists.defaults,
                userscripts = emptyList(),
                localSpaces = listOf(shiftedPersonal),
                zenSpacesCount = 3,
                localEssentials = defaultSeedEssentials().mapIndexed { i, e -> e.copy(sortOrder = i + 5) },
                zenEssentialsCount = 5
            ),
            baseline = plan1.nextBaseline,
            remoteRawRecords = remote,
            nowMs = 2000L
        )

        // ONLY the deleted zap rule should be pushed as a tombstone; shifted spaces/essentials must NOT re-upload!
        assertEquals(1, plan2.outgoingBsos.size)
        val (tombId, tombObj) = plan2.outgoingBsos.single()
        assertEquals(SwiftfoxSyncCodec.zappedRuleBsoId("news.example.com", "div.ad"), tombId)
        assertTrue(SwiftfoxSyncCodec.isDeletedTombstone(tombObj))
    }

    @Test
    fun sync15TransportChunksPostsChainsLastModifiedAndDetects412Conflicts() = runBlocking {
        val rawKSync = ByteArray(64) { (it + 3).toByte() }
        val syncKeyBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(rawKSync)
        val defaultEnc = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 11).toByte() })
        val defaultHmac = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 33).toByte() })
        val keys = SyncCrypto.fromBase64Pair(defaultEnc, defaultHmac)

        val seenIfUnmodifiedSince = mutableListOf<String?>()
        val postedCounts = mutableListOf<Int>()
        var callNum = 0

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                fun resp(code: Int, body: String, lm: String? = null): Response {
                    val b = Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message("OK")
                        .body(body.toResponseBody("application/json".toMediaType()))
                    if (lm != null) b.header("X-Last-Modified", lm)
                    return b.build()
                }
                when {
                    url == "https://token.services.mozilla.com/1.0/sync/1.5" -> resp(
                        200,
                        """{"api_endpoint":"https://sync.example.org/1.5/9","id":"h1","key":"secret","timestamp":1700000000}"""
                    )
                    url.endsWith("/storage/swiftfox") && req.method == "POST" -> {
                        callNum++
                        seenIfUnmodifiedSince += req.header("X-If-Unmodified-Since")
                        val buf = okio.Buffer()
                        req.body!!.writeTo(buf)
                        val arr = json.parseToJsonElement(buf.readUtf8()) as kotlinx.serialization.json.JsonArray
                        postedCounts += arr.size
                        val nextLm = if (callNum == 1) "100.5" else "101.0"
                        resp(200, """{"modified":$nextLm,"success":[],"failed":{}}""", lm = nextLm)
                    }
                    else -> resp(404, "")
                }
            }
            .build()

        val transport = Sync15Transport { mockClient }
        val (session, _) = transport.getOrFetchSession(
            Sync15Transport.AuthCacheKey("kid", "tok", syncKeyBase64, "https://token.services.mozilla.com")
        )

        val smallRecs = (1..3).map { i ->
            "id_$i" to buildJsonObject { put("id", JsonPrimitive("id_$i")); put("n", JsonPrimitive(i)) }
        }
        val oversized = "too_big" to buildJsonObject {
            put("id", JsonPrimitive("too_big"))
            put("blob", JsonPrimitive("x".repeat(40_000)))
        }

        val res = transport.postEncryptedBsosChunked(
            session = session,
            collection = "swiftfox",
            keys = keys,
            records = smallRecs + oversized,
            initialIfUnmodifiedSince = "99.0",
            limits = Sync15Transport.ServerLimits(
                maxRecordPayloadBytes = 20_000,
                maxPostRecords = 2,
                maxPostBytes = 512 * 1024
            )
        )

        assertTrue(res is Sync15Transport.PostChunkResult.Success)
        val ok = res as Sync15Transport.PostChunkResult.Success
        assertEquals(listOf("id_1", "id_2", "id_3"), ok.uploadedIds)
        assertEquals(listOf("too_big"), ok.skippedOversizedIds)
        assertEquals(listOf(2, 1), postedCounts)
        assertEquals(listOf("99.0", "100.5"), seenIfUnmodifiedSince)
        assertEquals("101.0", ok.lastModified)
    }

    @Test
    fun upgradedCustomSettingsWithZeroTimestampBeatRemoteDefaults() {
        val remoteDefaultRecord = SwiftfoxSyncCodec.encodeSettingsRecord(BrowserSettings(), updatedAt = 1000L)
        val upgradedLocal = BrowserSettings(
            themeMode = ThemeMode.NIGHT,
            nightSurfaceStyle = NightSurfaceStyle.AMOLED,
            searchEngine = SearchEngineId.KAGI
        )
        val plan = SwiftfoxSyncReconciler.buildPlan(
            local = SwiftfoxSyncReconciler.LocalSnapshot(
                settings = upgradedLocal,
                settingsUpdatedAtMs = 0L,
                siteOverrides = emptyList(),
                zappedRules = emptyList(),
                filterLists = BuiltInFilterLists.defaults,
                userscripts = emptyList(),
                localSpaces = defaultSeedSpaces(),
                zenSpacesCount = 0,
                localEssentials = defaultSeedEssentials(),
                zenEssentialsCount = 0
            ),
            baseline = SwiftfoxSyncBaseline(),
            remoteRawRecords = listOf(SwiftfoxSyncCodec.SETTINGS_BSO_ID to remoteDefaultRecord),
            nowMs = 2000L
        )

        assertNull(plan.settingsToApply)
        assertEquals(2000L, plan.settingsTimestampToRecord)
        assertTrue(plan.outgoingBsos.any { it.first == SwiftfoxSyncCodec.SETTINGS_BSO_ID })
    }

    @Test
    fun sync15SwiftfoxClientGuardsNewCollectionAndRollsBackOversizedBaselineOn412Retry() = runBlocking {
        val rawKSync = ByteArray(64) { (it + 7).toByte() }
        val syncKeyBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(rawKSync)
        val rootKeys = SyncCrypto.fromKSyncBase64(syncKeyBase64)
        val defaultEnc = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 13).toByte() })
        val defaultHmac = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 29).toByte() })
        val collectionKeys = SyncCrypto.fromBase64Pair(defaultEnc, defaultHmac)

        val cryptoKeysCleartext = buildJsonObject {
            put("id", JsonPrimitive("keys"))
            put("collection", JsonPrimitive("crypto"))
            put(
                "default",
                buildJsonArray {
                    add(JsonPrimitive(defaultEnc))
                    add(JsonPrimitive(defaultHmac))
                }
            )
            put("collections", buildJsonObject {})
        }
        val cryptoKeysRecord = buildJsonObject {
            put("id", JsonPrimitive("keys"))
            put("payload", JsonPrimitive(SyncCrypto.encryptBSO(cryptoKeysCleartext.toString().toByteArray(Charsets.UTF_8), rootKeys)))
        }
        val metaGlobalRecord = buildJsonObject {
            put("id", JsonPrimitive("global"))
            put(
                "payload",
                JsonPrimitive(
                    buildJsonObject {
                        put("syncID", JsonPrimitive("sync1"))
                        put("storageVersion", JsonPrimitive(5))
                        put("engines", buildJsonObject {})
                        put("declined", buildJsonArray {})
                    }.toString()
                )
            )
        }

        val remoteOverride = SiteOverrideEntity(host = "remote.example.com", adblockDisabled = true)
        val remoteOverrideCleartext = SwiftfoxSyncCodec.encodeSiteOverrideRecord(remoteOverride, updatedAt = 500L)
        val remoteEncryptedBso = buildJsonObject {
            put("id", JsonPrimitive(SwiftfoxSyncCodec.siteOverrideBsoId("remote.example.com")))
            put("modified", JsonPrimitive(100.0))
            put(
                "payload",
                JsonPrimitive(
                    SyncCrypto.encryptBSO(
                        remoteOverrideCleartext.toString().toByteArray(Charsets.UTF_8),
                        collectionKeys
                    )
                )
            )
        }

        val postPreconditions = mutableListOf<String?>()
        var getSwiftfoxCount = 0
        var postCount = 0
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                fun resp(code: Int, body: String, lm: String? = null): Response {
                    val b = Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message("OK")
                        .body(body.toResponseBody("application/json".toMediaType()))
                    if (lm != null) b.header("X-Last-Modified", lm)
                    return b.build()
                }
                when {
                    url == "https://token.services.mozilla.com/1.0/sync/1.5" -> resp(
                        200,
                        """{"api_endpoint":"https://sync.example.org/1.5/9","id":"h1","key":"secret","timestamp":1700000000}"""
                    )
                    url.endsWith("/storage/crypto/keys") -> resp(200, cryptoKeysRecord.toString())
                    url.endsWith("/storage/meta/global") -> resp(200, metaGlobalRecord.toString())
                    url.endsWith("/info/configuration") -> resp(
                        200,
                        """{"max_record_payload_bytes":20000,"max_post_records":100,"max_post_bytes":524288}"""
                    )
                    url.endsWith("/info/collections") -> resp(200, """{"bookmarks":90.0}""")
                    url.contains("/storage/swiftfox?") && req.method == "GET" -> {
                        getSwiftfoxCount++
                        resp(200, JsonArray(listOf(remoteEncryptedBso)).toString(), lm = "100.0")
                    }
                    url.endsWith("/storage/swiftfox") && req.method == "POST" -> {
                        postCount++
                        postPreconditions += req.header("X-If-Unmodified-Since")
                        if (postCount == 1) {
                            resp(412, "{}")
                        } else {
                            resp(200, """{"modified":105.0,"success":[],"failed":{}}""", lm = "105.0")
                        }
                    }
                    else -> resp(404, "")
                }
            }
            .build()

        // Include an incompressible/large userscript > 20 KB payload limit to verify baseline rollback
        val rng = java.util.Random(42L)
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val randomBody = CharArray(36_000) { alphabet[rng.nextInt(alphabet.length)] }.concatToString()
        val hugeScript = UserscriptEntity(
            id = "us_huge",
            name = "Huge",
            code = "// ==UserScript==\n// @name Huge\n// ==/UserScript==\nconst blob = \"$randomBody\";",
            updatedAt = 900L
        )
        val hugeBsoId = SwiftfoxSyncCodec.userscriptBsoId(hugeScript)

        var savedBaseline = SwiftfoxSyncBaseline()
        var appliedPlan: SwiftfoxSyncReconciler.SwiftfoxSyncPlan? = null
        val fakeStore = object : SwiftfoxSyncLocalStore {
            override fun loadBaseline(): SwiftfoxSyncBaseline = savedBaseline
            override fun saveBaselineAtomic(baseline: SwiftfoxSyncBaseline) {
                savedBaseline = baseline
            }
            override fun clearBaseline() {
                savedBaseline = SwiftfoxSyncBaseline()
            }
            override suspend fun readLocalSnapshot() = SwiftfoxSyncReconciler.LocalSnapshot(
                settings = BrowserSettings(),
                settingsUpdatedAtMs = 0L,
                siteOverrides = listOf(SiteOverrideEntity(host = "local.example.com", javascriptEnabled = false)),
                zappedRules = emptyList(),
                filterLists = BuiltInFilterLists.defaults,
                userscripts = listOf(hugeScript),
                localSpaces = defaultSeedSpaces(),
                zenSpacesCount = 0,
                localEssentials = defaultSeedEssentials(),
                zenEssentialsCount = 0
            )
            override suspend fun applyPlanLocally(plan: SwiftfoxSyncReconciler.SwiftfoxSyncPlan): Boolean {
                appliedPlan = plan
                return true
            }
        }

        val client = Sync15SwiftfoxClient(fakeStore, Sync15Transport { mockClient })
        val result = client.sync(
            kid = "kid",
            fxaAccessToken = "tok",
            syncKeyBase64 = syncKeyBase64,
            tokenServerUrl = "https://token.services.mozilla.com"
        )

        assertTrue(result is SwiftfoxSyncResult.Success)
        val ok = result as SwiftfoxSyncResult.Success
        assertEquals(listOf("0", "100.0"), postPreconditions)
        assertEquals(1, getSwiftfoxCount)
        assertEquals(1, ok.skippedOversizedCount)
        assertEquals(1, ok.pulledCount)
        assertEquals("remote.example.com", appliedPlan?.siteOverridesToUpsert?.single()?.host)
        assertFalse(savedBaseline.items.containsKey(hugeBsoId))
        assertTrue(savedBaseline.items.containsKey(SwiftfoxSyncCodec.siteOverrideBsoId("local.example.com")))
    }

    @Test
    fun sync15SwiftfoxClientReturnsKeysNotInitializedWhenCryptoKeysMissing() = runBlocking {
        val rawKSync = ByteArray(64) { (it + 1).toByte() }
        val syncKeyBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(rawKSync)
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val body = if (req.url.toString() == "https://token.services.mozilla.com/1.0/sync/1.5") {
                    """{"api_endpoint":"https://sync.example.org/1.5/9","id":"h1","key":"secret","timestamp":1700000000}"""
                } else {
                    ""
                }
                val code = if (body.isNotEmpty()) 200 else 404
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        var mutated = false
        val fakeStore = object : SwiftfoxSyncLocalStore {
            override fun loadBaseline() = SwiftfoxSyncBaseline()
            override fun saveBaselineAtomic(baseline: SwiftfoxSyncBaseline) { mutated = true }
            override fun clearBaseline() { mutated = true }
            override suspend fun readLocalSnapshot() = error("Should not read snapshot")
            override suspend fun applyPlanLocally(plan: SwiftfoxSyncReconciler.SwiftfoxSyncPlan) = error("Should not apply plan")
        }

        val result = Sync15SwiftfoxClient(fakeStore, Sync15Transport { mockClient }).sync(
            kid = "kid",
            fxaAccessToken = "tok",
            syncKeyBase64 = syncKeyBase64,
            tokenServerUrl = "https://token.services.mozilla.com"
        )

        assertTrue(result is SwiftfoxSyncResult.KeysNotInitialized)
        assertFalse(mutated)
    }

    @Test
    fun pinnedTabCodecRoundTripsAndRejectsUnsafeSchemes() {
        val pin = TabEntity(
            id = "pin_1",
            spaceId = "space_work",
            title = "Live Page Title",
            url = "https://github.com/pulls",
            isPrivate = false,
            isPinned = true,
            sortOrder = 2,
            customIcon = "emoji:\uD83D\uDE80",
            customTitle = "GitHub PRs",
            pinnedUrl = "https://github.com/pulls?q=author",
            isUnloaded = false
        )
        val encoded = SwiftfoxSyncCodec.encodePinnedTabRecord(pin, localOrder = 1, updatedAt = 5_000L)
        val decoded = SwiftfoxSyncCodec.decodeRecord(SwiftfoxSyncCodec.pinnedTabBsoId(pin.id), encoded)
        assertTrue(decoded is SwiftfoxSyncCodec.DecodedSwiftfoxRecord.PinnedTab)
        val decodedPin = decoded as SwiftfoxSyncCodec.DecodedSwiftfoxRecord.PinnedTab
        assertEquals("pin_1", decodedPin.entity.id)
        assertEquals("space_work", decodedPin.entity.spaceId)
        assertEquals("GitHub PRs", decodedPin.entity.customTitle)
        assertEquals("https://github.com/pulls?q=author", decodedPin.entity.pinnedUrl)
        assertEquals(1, decodedPin.entity.sortOrder)
        assertEquals("emoji:\uD83D\uDE80", decodedPin.entity.customIcon)
        assertTrue(decodedPin.entity.isPinned)
        assertTrue(decodedPin.entity.isUnloaded)

        // Unsafe URL scheme must be dropped safely
        val unsafeRecord = SwiftfoxSyncCodec.encodePinnedTabRecord(
            pin.copy(pinnedUrl = "javascript:alert(1)", url = "javascript:alert(1)"),
            localOrder = 0,
            updatedAt = 5_000L
        )
        assertNull(SwiftfoxSyncCodec.decodeRecord(SwiftfoxSyncCodec.pinnedTabBsoId(pin.id), unsafeRecord))
    }

    @Test
    fun pinnedTabsTwoWaySyncMergesPreservesLiveStateAndTombstonesUnpins() {
        val localSpace = SpaceEntity(id = "space_local", name = "Work", sortOrder = 0)
        val localPinKeep = TabEntity(
            id = "pin_keep",
            spaceId = "space_local",
            title = "Loaded Child Article",
            url = "https://news.ycombinator.com/item?id=999",
            isPrivate = false,
            isPinned = true,
            sortOrder = 0,
            customIcon = "icon:star",
            customTitle = "HN",
            pinnedUrl = "https://news.ycombinator.com/",
            isUnloaded = false,
            lastAccessedAt = 100L
        )
        val localPinDropRemote = TabEntity(
            id = "pin_drop_remote",
            spaceId = "space_local",
            title = "Temp Remote Pin",
            url = "https://example.org",
            isPrivate = false,
            isPinned = true,
            sortOrder = 1,
            pinnedUrl = "https://example.org",
            isUnloaded = true
        )
        val localPinUnpinnedLocally = TabEntity(
            id = "pin_unpinned_local",
            spaceId = "space_local",
            title = "Unpinned Locally",
            url = "https://example.net",
            isPrivate = false,
            isPinned = true,
            sortOrder = 2,
            pinnedUrl = "https://example.net",
            isUnloaded = true
        )
        val remoteOnlyPin = TabEntity(
            id = "pin_remote",
            spaceId = "space_missing",
            title = "Remote Docs",
            url = "https://kotlinlang.org",
            isPrivate = false,
            isPinned = true,
            sortOrder = 0,
            pinnedUrl = "https://kotlinlang.org"
        )
        val remoteUpdatedKeep = localPinKeep.copy(
            customTitle = "Hacker News Renamed",
            title = "Hacker News Renamed",
            pinnedUrl = "https://news.ycombinator.com/"
        )

        val baseline = SwiftfoxSyncBaseline(
            hasCompletedInitialSync = true,
            items = mapOf(
                SwiftfoxSyncCodec.spaceBsoId(localSpace.id) to SwiftfoxBaselineItem(
                    bsoId = SwiftfoxSyncCodec.spaceBsoId(localSpace.id),
                    kind = SwiftfoxSyncCodec.KIND_SPACE,
                    contentHash = SwiftfoxSyncCodec.spaceContentHash(localSpace, 0),
                    updatedAt = 1_000L
                ),
                SwiftfoxSyncCodec.pinnedTabBsoId(localPinKeep.id) to SwiftfoxBaselineItem(
                    bsoId = SwiftfoxSyncCodec.pinnedTabBsoId(localPinKeep.id),
                    kind = SwiftfoxSyncCodec.KIND_PINNED_TAB,
                    contentHash = SwiftfoxSyncCodec.pinnedTabContentHash(localPinKeep, 0),
                    updatedAt = 1_000L
                ),
                SwiftfoxSyncCodec.pinnedTabBsoId(localPinDropRemote.id) to SwiftfoxBaselineItem(
                    bsoId = SwiftfoxSyncCodec.pinnedTabBsoId(localPinDropRemote.id),
                    kind = SwiftfoxSyncCodec.KIND_PINNED_TAB,
                    contentHash = SwiftfoxSyncCodec.pinnedTabContentHash(localPinDropRemote, 1),
                    updatedAt = 1_000L
                ),
                SwiftfoxSyncCodec.pinnedTabBsoId(localPinUnpinnedLocally.id) to SwiftfoxBaselineItem(
                    bsoId = SwiftfoxSyncCodec.pinnedTabBsoId(localPinUnpinnedLocally.id),
                    kind = SwiftfoxSyncCodec.KIND_PINNED_TAB,
                    contentHash = SwiftfoxSyncCodec.pinnedTabContentHash(localPinUnpinnedLocally, 2),
                    updatedAt = 1_000L
                )
            )
        )

        val remoteRecords = listOf(
            SwiftfoxSyncCodec.spaceBsoId(localSpace.id) to SwiftfoxSyncCodec.encodeSpaceRecord(localSpace, 0, 1_000L),
            SwiftfoxSyncCodec.pinnedTabBsoId(localPinKeep.id) to SwiftfoxSyncCodec.encodePinnedTabRecord(remoteUpdatedKeep, 0, 2_000L),
            SwiftfoxSyncCodec.pinnedTabBsoId(remoteOnlyPin.id) to SwiftfoxSyncCodec.encodePinnedTabRecord(remoteOnlyPin, 1, 2_000L),
            SwiftfoxSyncCodec.pinnedTabBsoId(localPinUnpinnedLocally.id) to SwiftfoxSyncCodec.encodePinnedTabRecord(localPinUnpinnedLocally, 2, 1_000L),
            SwiftfoxSyncCodec.pinnedTabBsoId(localPinDropRemote.id) to SwiftfoxSyncCodec.encodeTombstone(
                SwiftfoxSyncCodec.pinnedTabBsoId(localPinDropRemote.id),
                SwiftfoxSyncCodec.KIND_PINNED_TAB,
                2_000L
            )
        )

        val snapshot = SwiftfoxSyncReconciler.LocalSnapshot(
            settings = BrowserSettings(),
            settingsUpdatedAtMs = 1_000L,
            siteOverrides = emptyList(),
            zappedRules = emptyList(),
            filterLists = emptyList(),
            userscripts = emptyList(),
            localSpaces = listOf(localSpace),
            zenSpacesCount = 0,
            localEssentials = emptyList(),
            zenEssentialsCount = 0,
            localPinnedTabs = listOf(localPinKeep, localPinDropRemote),
            zenSpaceIds = emptySet()
        )

        val plan = SwiftfoxSyncReconciler.buildPlan(
            local = snapshot,
            baseline = baseline,
            remoteRawRecords = remoteRecords,
            nowMs = 3_000L
        )

        // Remote deletion deletes pin_drop_remote locally
        assertEquals(listOf("pin_drop_remote"), plan.pinnedTabsPlan.deleteIds)

        // Local unpin pushes tombstone to remote
        val outgoingUnpin = plan.outgoingBsos.firstOrNull {
            it.first == SwiftfoxSyncCodec.pinnedTabBsoId(localPinUnpinnedLocally.id)
        }?.second
        assertTrue(outgoingUnpin?.get("deleted")?.toString() == "true")

        // pin_keep preserves live loaded URL while picking up remote title;
        // pin_remote falls back cleanly from missing space to surviving localSpace
        val upsertsById = plan.pinnedTabsPlan.upserts.associateBy { it.id }
        val syncedKeep = upsertsById.getValue("pin_keep")
        assertEquals("Hacker News Renamed", syncedKeep.customTitle)
        assertEquals("https://news.ycombinator.com/item?id=999", syncedKeep.url)
        assertEquals(false, syncedKeep.isUnloaded)

        val syncedRemote = upsertsById.getValue("pin_remote")
        assertEquals("space_local", syncedRemote.spaceId)
        assertEquals(true, syncedRemote.isUnloaded)
    }

    @Test
    fun remappedPinnedTabFallbackSpaceNeverOverwritesRemoteSpaceOnSubsequentSyncs() {
        val localSpace = SpaceEntity(id = "space_local", name = "Personal", sortOrder = 0)
        val remotePin = TabEntity(
            id = "pin_zen",
            spaceId = "zen_workspace_1",
            title = "Linear",
            url = "https://linear.app",
            isPrivate = false,
            isPinned = true,
            sortOrder = 0,
            customTitle = "Linear",
            pinnedUrl = "https://linear.app",
            isUnloaded = true
        )

        val spaceBsoId = SwiftfoxSyncCodec.spaceBsoId(localSpace.id)
        val pinBsoId = SwiftfoxSyncCodec.pinnedTabBsoId(remotePin.id)
        val defaultSettings = BrowserSettings()
        val remoteRecords = listOf(
            SwiftfoxSyncCodec.SETTINGS_BSO_ID to SwiftfoxSyncCodec.encodeSettingsRecord(defaultSettings, 1_000L),
            spaceBsoId to SwiftfoxSyncCodec.encodeSpaceRecord(localSpace, 0, 1_000L),
            pinBsoId to SwiftfoxSyncCodec.encodePinnedTabRecord(remotePin, 0, 2_000L)
        )
        val initialBaseline = SwiftfoxSyncBaseline(
            hasCompletedInitialSync = true,
            items = mapOf(
                SwiftfoxSyncCodec.SETTINGS_BSO_ID to SwiftfoxBaselineItem(
                    bsoId = SwiftfoxSyncCodec.SETTINGS_BSO_ID,
                    kind = SwiftfoxSyncCodec.KIND_SETTINGS,
                    contentHash = SwiftfoxSyncCodec.settingsContentHash(defaultSettings),
                    updatedAt = 1_000L
                ),
                spaceBsoId to SwiftfoxBaselineItem(
                    bsoId = spaceBsoId,
                    kind = SwiftfoxSyncCodec.KIND_SPACE,
                    contentHash = SwiftfoxSyncCodec.spaceContentHash(localSpace, 0),
                    updatedAt = 1_000L
                )
            )
        )

        fun snapshotWithPins(pins: List<TabEntity>) = SwiftfoxSyncReconciler.LocalSnapshot(
            settings = BrowserSettings(),
            settingsUpdatedAtMs = 1_000L,
            siteOverrides = emptyList(),
            zappedRules = emptyList(),
            filterLists = emptyList(),
            userscripts = emptyList(),
            localSpaces = listOf(localSpace),
            zenSpacesCount = 0,
            localEssentials = emptyList(),
            zenEssentialsCount = 0,
            localPinnedTabs = pins,
            zenSpaceIds = emptySet()
        )

        // Sync 1: missing zen_workspace_1 falls back locally to space_local while tracking localContentHash
        val plan1 = SwiftfoxSyncReconciler.buildPlan(
            local = snapshotWithPins(emptyList()),
            baseline = initialBaseline,
            remoteRawRecords = remoteRecords,
            nowMs = 3_000L
        )
        val localPinInFallback = plan1.pinnedTabsPlan.upserts.single { it.id == "pin_zen" }
        assertEquals("space_local", localPinInFallback.spaceId)
        assertNotNull(plan1.nextBaseline.items[pinBsoId]?.localContentHash)

        // Sync 2 & 3: unchanged local fallback tab must neither push space_local nor trigger re-pulls
        val plan2 = SwiftfoxSyncReconciler.buildPlan(
            local = snapshotWithPins(listOf(localPinInFallback)),
            baseline = plan1.nextBaseline,
            remoteRawRecords = remoteRecords,
            nowMs = 4_000L
        )
        assertEquals(0, plan2.pulledCount)
        assertEquals(0, plan2.pushedCount)
        assertTrue(plan2.outgoingBsos.isEmpty())
        assertTrue(plan2.pinnedTabsPlan.upserts.isEmpty())
        assertEquals(
            plan1.nextBaseline.items[pinBsoId]?.localContentHash,
            plan2.nextBaseline.items[pinBsoId]?.localContentHash
        )

        val plan3 = SwiftfoxSyncReconciler.buildPlan(
            local = snapshotWithPins(listOf(localPinInFallback)),
            baseline = plan2.nextBaseline,
            remoteRawRecords = remoteRecords,
            nowMs = 5_000L
        )
        assertEquals(0, plan3.pulledCount)
        assertEquals(0, plan3.pushedCount)
        assertTrue(plan3.outgoingBsos.isEmpty())

        // Sync 4: explicit local edit DOES push to remote
        val editedLocally = localPinInFallback.copy(customTitle = "Linear Issues", title = "Linear Issues")
        val plan4 = SwiftfoxSyncReconciler.buildPlan(
            local = snapshotWithPins(listOf(editedLocally)),
            baseline = plan3.nextBaseline,
            remoteRawRecords = remoteRecords,
            nowMs = 6_000L
        )
        assertEquals(1, plan4.pushedCount)
        assertEquals(pinBsoId, plan4.outgoingBsos.single().first)
        assertNull(plan4.nextBaseline.items[pinBsoId]?.localContentHash)
    }
}
