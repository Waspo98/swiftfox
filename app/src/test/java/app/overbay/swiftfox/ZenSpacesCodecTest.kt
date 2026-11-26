package app.overbay.swiftfox

import app.overbay.swiftfox.features.sync.ZenSpacesCodec
import app.overbay.swiftfox.features.sync.ZenSpacesPendingOpsStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ZenSpacesCodecTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun obj(raw: String): JsonObject = json.parseToJsonElement(raw).jsonObject

    @Test
    fun decodesSpacesFoldersSplitsLayoutsAndRespectsTombstoneBoolRule() {
        val records = listOf(
            "layout" to obj(
                """
                {
                  "id": "layout",
                  "kind": "layout",
                  "data": {
                    "spaces": ["sp-work", "sp-home"],
                    "essentials": {
                      "default": ["ess-1"],
                      "container-work": ["ess-2"]
                    }
                  }
                }
                """.trimIndent()
            ),
            "sp-home" to obj(
                """
                {
                  "id": "sp-home",
                  "kind": "space",
                  "deleted": "true",
                  "data": {
                    "uuid": "sp-home",
                    "name": "Home",
                    "icon": "🏠",
                    "theme": {
                      "gradientColors": [
                        { "color": "#E86A17", "isPrimary": true }
                      ]
                    },
                    "children": ["tab-1"]
                  }
                }
                """.trimIndent()
            ),
            "sp-work" to obj(
                """
                {
                  "id": "sp-work",
                  "kind": "space",
                  "data": {
                    "uuid": "sp-work",
                    "name": "Engineering",
                    "icon": "code",
                    "containerGuid": "container-work",
                    "theme": {
                      "gradientColors": ["rgb(91, 108, 250)"]
                    },
                    "children": ["folder-1", "split-1"]
                  }
                }
                """.trimIndent()
            ),
            "sp-deleted" to obj(
                """
                {
                  "id": "sp-deleted",
                  "kind": "space",
                  "deleted": true,
                  "data": {
                    "uuid": "sp-deleted",
                    "name": "Deleted Space",
                    "children": []
                  }
                }
                """.trimIndent()
            ),
            "folder-1" to obj(
                """
                {
                  "id": "folder-1",
                  "kind": "folder",
                  "data": {
                    "folderId": "folder-1",
                    "name": "Docs",
                    "workspaceUuid": "sp-work",
                    "children": ["tab-2"]
                  }
                }
                """.trimIndent()
            ),
            "split-1" to obj(
                """
                {
                  "id": "split-1",
                  "kind": "split",
                  "data": {
                    "splitId": "split-1",
                    "gridType": "vertical",
                    "workspaceUuid": "sp-work",
                    "tabs": ["tab-3"],
                    "pinned": true
                  }
                }
                """.trimIndent()
            ),
            "tab-1" to obj(
                """
                {
                  "id": "tab-1",
                  "kind": "tab",
                  "data": {
                    "tabId": "tab-1",
                    "url": "https://news.ycombinator.com",
                    "title": "Hacker News",
                    "workspaceUuid": "sp-home",
                    "pinned": true
                  }
                }
                """.trimIndent()
            ),
            "tab-2" to obj(
                """
                {
                  "id": "tab-2",
                  "kind": "tab",
                  "data": {
                    "tabId": "tab-2",
                    "url": "https://developer.android.com",
                    "title": "Android Docs",
                    "workspaceUuid": "sp-work",
                    "folderId": "folder-1",
                    "pinned": true
                  }
                }
                """.trimIndent()
            ),
            "tab-3" to obj(
                """
                {
                  "id": "tab-3",
                  "kind": "tab",
                  "data": {
                    "tabId": "tab-3",
                    "url": "https://github.com",
                    "title": "GitHub",
                    "workspaceUuid": "sp-work",
                    "pinned": "true"
                  }
                }
                """.trimIndent()
            ),
            "ess-1" to obj(
                """
                {
                  "id": "ess-1",
                  "kind": "tab",
                  "data": {
                    "tabId": "ess-1",
                    "url": "https://overbay.app",
                    "staticLabel": "Overbay",
                    "essential": true,
                    "defaultContainer": true
                  }
                }
                """.trimIndent()
            ),
            "ess-2" to obj(
                """
                {
                  "id": "ess-2",
                  "kind": "tab",
                  "data": {
                    "tabId": "ess-2",
                    "url": "https://jira.example.com",
                    "title": "Jira",
                    "essential": "1",
                    "containerGuid": "container-work"
                  }
                }
                """.trimIndent()
            )
        )

        val snapshot = ZenSpacesCodec.assembleSnapshot(records)

        // Layout order puts sp-work first, sp-home second (deleted:"true" string is NOT tombstone; deleted:true IS)
        assertEquals(listOf("sp-work", "sp-home"), snapshot.spaces.map { it.uuid })
        assertEquals("#5b6cfa", snapshot.spaces[0].themeColorHex)
        assertEquals("#e86a17", snapshot.spaces[1].themeColorHex)

        // Essentials separated by container bucket
        assertEquals("Overbay", snapshot.essentialsByContainer["default"]?.single()?.title)
        assertEquals("Jira", snapshot.essentialsByContainer["container-work"]?.single()?.title)

        // Pinned items include folder label & split members
        val workPinned = snapshot.pinnedBySpace["sp-work"].orEmpty()
        assertEquals(2, workPinned.size)
        assertEquals("Docs", workPinned[0].folderName)
        assertEquals("Android Docs", workPinned[0].title)
        assertEquals("GitHub", workPinned[1].title)
    }

    @Test
    fun gatesNormalUnpinnedTabsUsingSyncedPrefs() {
        val records = listOf(
            "sp-1" to obj("""{"id":"sp-1","kind":"space","data":{"uuid":"sp-1","name":"Reading","children":["t-pin","t-norm"]}}"""),
            "t-pin" to obj("""{"id":"t-pin","kind":"tab","data":{"tabId":"t-pin","url":"https://a.com","title":"Pinned","workspaceUuid":"sp-1","pinned":true}}"""),
            "t-norm" to obj("""{"id":"t-norm","kind":"tab","data":{"tabId":"t-norm","url":"https://b.com","title":"Normal","workspaceUuid":"sp-1","pinned":false}}""")
        )

        val prefsDisabled = ZenSpacesCodec.decodePrefs(
            listOf(obj("""{"id":"prefs","value":{"zen.spaces-sync.normal-tabs":false,"zen.workspaces.separate-essentials":true}}"""))
        )
        val snapDisabled = ZenSpacesCodec.assembleSnapshot(records, prefsDisabled)
        assertEquals(listOf("Pinned"), snapDisabled.pinnedBySpace["sp-1"]?.map { it.title })
        assertEquals(true, prefsDisabled.separateEssentials)

        val prefsEnabled = ZenSpacesCodec.decodePrefs(
            listOf(obj("""{"id":"prefs","value":{"zen.spaces-sync.normal-tabs":true}}"""))
        )
        val snapEnabled = ZenSpacesCodec.assembleSnapshot(records, prefsEnabled)
        assertEquals(listOf("Pinned", "Normal"), snapEnabled.pinnedBySpace["sp-1"]?.map { it.title })
    }

    @Test
    fun parsesAllThemeGradientColorFormats() {
        assertEquals(
            "#ff8800",
            ZenSpacesCodec.extractPrimaryThemeHex(obj("""{"gradientColors":["#f80"]}"""))
        )
        assertEquals(
            "#112233",
            ZenSpacesCodec.extractPrimaryThemeHex(obj("""{"gradientColors":["#112233cc"]}"""))
        )
        assertEquals(
            "#204060",
            ZenSpacesCodec.extractPrimaryThemeHex(obj("""{"gradientColors":[[32, 64, 96]]}"""))
        )
        assertEquals(
            "#abcdef",
            ZenSpacesCodec.extractPrimaryThemeHex(obj("""{"gradientColors":[{"c":"#123456"},{"color":"#ABCDEF","isPrimary":true}]}"""))
        )
    }

    @Test
    fun enforcesMetaGlobalSpacesWriteSafetyGate() {
        assertTrue(
            ZenSpacesCodec.metaGlobalAllowsSpacesWrite(
                """{"storageVersion":5,"engines":{"spaces":{"version":1}}}"""
            )
        )
        assertTrue(
            ZenSpacesCodec.metaGlobalAllowsSpacesWrite(
                """{"storageVersion":5.0,"engines":{"spaces":{"version":1.0}}}"""
            )
        )
        assertTrue(
            ZenSpacesCodec.metaGlobalAllowsSpacesWrite(
                """{"storageVersion":5,"engines":{"bookmarks":{"version":2}}}"""
            )
        )
        assertFalse(
            ZenSpacesCodec.metaGlobalAllowsSpacesWrite(
                """{"storageVersion":4,"engines":{"spaces":{"version":1}}}"""
            )
        )
        assertFalse(
            ZenSpacesCodec.metaGlobalAllowsSpacesWrite(
                """{"storageVersion":5,"engines":{"spaces":{"version":2}}}"""
            )
        )
        assertFalse(
            ZenSpacesCodec.metaGlobalAllowsSpacesWrite(
                """{"storageVersion":5,"declined":["spaces"],"engines":{"spaces":{"version":1}}}"""
            )
        )
        assertFalse(ZenSpacesCodec.metaGlobalAllowsSpacesWrite(null))
        assertFalse(ZenSpacesCodec.metaGlobalAllowsSpacesWrite("not-json"))
        assertTrue(ZenSpacesCodec.isDeletedTombstone(obj("""{"deleted":true}""")))
        assertFalse(ZenSpacesCodec.isDeletedTombstone(obj("""{"deleted":"true"}""")))
        assertFalse(ZenSpacesCodec.isDeletedTombstone(obj("""{"deleted":false}""")))
    }

    @Test
    fun encodeNewTabRecordRoundTripsThroughAssembleSnapshot() {
        val spaceRec = "sp-1" to obj(
            """{"id":"sp-1","kind":"space","data":{"uuid":"sp-1","name":"Work","containerGuid":"container-work","children":["tab-new"]}}"""
        )
        val newTabObj = ZenSpacesCodec.encodeNewTabRecord(
            tabId = "tab-new",
            workspaceUuid = "sp-1",
            url = "https://kotlinlang.org/docs/",
            title = "Kotlin Docs",
            essential = false,
            containerGuid = "container-work"
        )
        val newEssentialObj = ZenSpacesCodec.encodeNewTabRecord(
            tabId = "ess-new",
            workspaceUuid = null,
            url = "https://overbay.app/",
            title = "Overbay",
            essential = true,
            containerGuid = null
        )

        val snapshot = ZenSpacesCodec.assembleSnapshot(
            listOf(
                spaceRec,
                "tab-new" to newTabObj,
                "ess-new" to newEssentialObj
            )
        )

        val decodedTab = (ZenSpacesCodec.decodeRecord(newTabObj) as ZenSpacesCodec.DecodedZenRecord.Tab).record
        assertEquals("container-work", decodedTab.containerGuid)
        assertFalse(decodedTab.essential)
        assertEquals(false, decodedTab.defaultContainer)

        val decodedEss = (ZenSpacesCodec.decodeRecord(newEssentialObj) as ZenSpacesCodec.DecodedZenRecord.Tab).record
        assertTrue(decodedEss.essential)
        assertEquals(true, decodedEss.defaultContainer)

        val pinned = snapshot.pinnedBySpace["sp-1"].orEmpty().single()
        assertEquals("tab-new", pinned.tabId)
        assertEquals("https://kotlinlang.org/docs/", pinned.url)
        assertEquals("Kotlin Docs", pinned.title)
        assertTrue(pinned.isPinned)

        val essential = snapshot.essentialsByContainer["default"].orEmpty().single()
        assertEquals("ess-new", essential.tabId)
        assertEquals("https://overbay.app/", essential.url)
        assertEquals("Overbay", essential.title)
        assertEquals("default", essential.containerGuid)
    }

    @Test
    fun extractTabIdCoalesceOpsAndBuildMutationBsosPreserveFoldersAndSplits() {
        assertEquals("tab-a", ZenSpacesCodec.extractTabId("sp-1_tab-a", "sp-1"))
        assertNull(ZenSpacesCodec.extractTabId("sp-2_tab-a", "sp-1"))
        assertNull(ZenSpacesCodec.extractTabId("sp-1_", "sp-1"))

        val opUpdate1 = ZenSpacesCodec.ZenPendingOp.UpdatePin("sp-1", "tab-a", "A1", "https://a.example", 1L)
        val opReorder1 = ZenSpacesCodec.ZenPendingOp.ReorderPins("sp-1", listOf("tab-a", "tab-b"), 2L)
        val opDeleteB = ZenSpacesCodec.ZenPendingOp.DeletePin("sp-1", "tab-b", 3L)
        var coalesced = ZenSpacesCodec.coalescePendingOps(emptyList(), opUpdate1)
        coalesced = ZenSpacesCodec.coalescePendingOps(coalesced, opReorder1)
        coalesced = ZenSpacesCodec.coalescePendingOps(coalesced, opDeleteB)
        // Update for tab-a preserved; deleted tab-b removed from ReorderPins leaving <2 items
        assertEquals(2, coalesced.size)
        assertTrue(coalesced.any { it is ZenSpacesCodec.ZenPendingOp.UpdatePin && it.tabId == "tab-a" })
        assertTrue(coalesced.any { it is ZenSpacesCodec.ZenPendingOp.DeletePin && it.tabId == "tab-b" })

        val decryptedRecords = listOf(
            "sp-1" to obj(
                """
                {
                  "id": "sp-1",
                  "kind": "space",
                  "data": {
                    "uuid": "sp-1",
                    "name": "Work",
                    "customExtra": "keep-me",
                    "children": ["tab-a", "fld-1", "tab-c", "tab-b"]
                  }
                }
                """.trimIndent()
            ),
            "fld-1" to obj(
                """
                {
                  "id": "fld-1",
                  "kind": "folder",
                  "data": {
                    "folderId": "fld-1",
                    "name": "Docs",
                    "children": ["tab-f1", "tab-f2"]
                  }
                }
                """.trimIndent()
            ),
            "tab-a" to obj(
                """{"id":"tab-a","kind":"tab","data":{"tabId":"tab-a","url":"https://old.a","title":"Old A","workspaceUuid":"sp-1","pinned":true,"folderId":"root","customTag":"preserve"}}"""
            ),
            "tab-b" to obj(
                """{"id":"tab-b","kind":"tab","data":{"tabId":"tab-b","url":"https://b.example","title":"B","workspaceUuid":"sp-1","pinned":true}}"""
            ),
            "tab-c" to obj(
                """{"id":"tab-c","kind":"tab","data":{"tabId":"tab-c","url":"https://c.example","title":"C","workspaceUuid":"sp-1","pinned":true}}"""
            ),
            "tab-f1" to obj(
                """{"id":"tab-f1","kind":"tab","data":{"tabId":"tab-f1","url":"https://f1.example","title":"F1","workspaceUuid":"sp-1","pinned":true,"folderId":"fld-1"}}"""
            ),
            "tab-f2" to obj(
                """{"id":"tab-f2","kind":"tab","data":{"tabId":"tab-f2","url":"https://f2.example","title":"F2","workspaceUuid":"sp-1","pinned":true,"folderId":"fld-1"}}"""
            )
        )

        val mutated = ZenSpacesCodec.buildMutationBsos(
            decryptedRecords = decryptedRecords,
            ops = listOf(
                ZenSpacesCodec.ZenPendingOp.UpdatePin("sp-1", "tab-a", "New A", "https://new.a", 10L),
                ZenSpacesCodec.ZenPendingOp.DeletePin("sp-1", "tab-b", 11L),
                ZenSpacesCodec.ZenPendingOp.ReorderPins("sp-1", listOf("tab-c", "tab-a", "tab-f2", "tab-f1"), 12L)
            )
        ).toMap()

        // tab-a rewritten with unknown fields preserved
        val tabAData = mutated.getValue("tab-a")["data"]!!.jsonObject
        assertEquals("https://new.a", tabAData["url"]?.jsonPrimitive?.content)
        assertEquals("New A", tabAData["title"]?.jsonPrimitive?.content)
        assertEquals("New A", tabAData["staticLabel"]?.jsonPrimitive?.content)
        assertEquals("preserve", tabAData["customTag"]?.jsonPrimitive?.content)

        // tab-b tombstoned
        assertEquals("true", mutated.getValue("tab-b")["deleted"]?.jsonPrimitive?.content)

        // Space children keep fld-1 in middle slot while permuting tab-c before tab-a and removing tab-b
        val spaceData = mutated.getValue("sp-1")["data"]!!.jsonObject
        assertEquals("keep-me", spaceData["customExtra"]?.jsonPrimitive?.content)
        val spaceChildren = spaceData["children"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("tab-c", "fld-1", "tab-a"), spaceChildren)

        // Folder children reordered cleanly inside fld-1 without lifting into space root
        val folderChildren = mutated.getValue("fld-1")["data"]!!.jsonObject["children"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("tab-f2", "tab-f1"), folderChildren)
    }

    @Test
    fun pendingOpsStoreCoalescesPersistsReloadsAndRemovesSafely() {
        val tempDir = Files.createTempDirectory("zen-ops-test").toFile()
        try {
            val storeFile = File(tempDir, "zen_spaces_pending_ops.json")
            val store = ZenSpacesPendingOpsStore(storeFile, json)
            val op1 = ZenSpacesCodec.ZenPendingOp.UpdatePin("sp-1", "tab-a", "A1", "https://a1.example", 1L)
            val op2 = ZenSpacesCodec.ZenPendingOp.UpdatePin("sp-1", "tab-a", "A2", "https://a2.example", 2L)
            val op3 = ZenSpacesCodec.ZenPendingOp.DeletePin("sp-1", "tab-b", 3L)

            store.enqueueBuffered(op1)
            store.enqueueBuffered(op2)
            store.enqueueBuffered(op3)
            store.persistPendingToDisk()
            assertTrue(storeFile.isFile)
            assertTrue(store.hasPending())

            // Reload from disk in a fresh store instance
            val reloaded = ZenSpacesPendingOpsStore(storeFile, json)
            val loadedOps = reloaded.snapshotPending()
            assertEquals(listOf(op2, op3), loadedOps)

            // Remove applied ops one at a time; disk file deleted once empty
            reloaded.removeApplied(listOf(op2))
            assertEquals(listOf(op3), reloaded.snapshotPending())
            assertTrue(storeFile.isFile)

            reloaded.removeApplied(listOf(op3))
            assertTrue(reloaded.snapshotPending().isEmpty())
            assertFalse(reloaded.hasPending())
            assertFalse(storeFile.exists())

            // Stress-check concurrent persist vs removeApplied / clear does not resurrect applied ops
            val executor = Executors.newFixedThreadPool(4)
            try {
                for (i in 0 until 40) {
                    val op = ZenSpacesCodec.ZenPendingOp.UpdatePin("sp-1", "tab-$i", "T$i", "https://t$i.example", i.toLong())
                    reloaded.enqueueBuffered(op)
                    val f1 = executor.submit { reloaded.persistPendingToDisk() }
                    val f2 = executor.submit { reloaded.removeApplied(listOf(op)) }
                    f1.get(5, TimeUnit.SECONDS)
                    f2.get(5, TimeUnit.SECONDS)
                }
            } finally {
                executor.shutdownNow()
            }
            val afterConcurrency = ZenSpacesPendingOpsStore(storeFile, json)
            assertTrue(afterConcurrency.snapshotPending().isEmpty())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
