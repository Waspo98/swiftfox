package app.overbay.swiftfox.features.sync

import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import java.util.Locale
import java.util.UUID

/**
 * Decoder and safe writer for Zen Browser's encrypted `spaces` & `prefs` Sync 1.5 collections
 * (Zen PR #13984 / #13598 schema, cross-verified with Kjell6/Z-Sync).
 */
object ZenSpacesCodec {

    const val SPACES_COLLECTION = "spaces"
    const val PREFS_COLLECTION = "prefs"
    const val NORMAL_TABS_PREF_KEY = "zen.spaces-sync.normal-tabs"
    const val SEPARATE_ESSENTIALS_PREF_KEY = "zen.workspaces.separate-essentials"

    data class ZenSpaceRecord(
        val uuid: String,
        val name: String,
        val icon: String?,
        val themeColorHex: String?,
        val containerGuid: String?,
        val children: List<String>
    )

    data class ZenTabRecord(
        val tabId: String,
        val url: String,
        val title: String,
        val containerGuid: String?,
        val essential: Boolean,
        val workspaceUuid: String?,
        val folderId: String?,
        val staticLabel: String?,
        val defaultContainer: Boolean?,
        val pinned: Boolean?
    )

    data class ZenFolderRecord(
        val folderId: String,
        val name: String,
        val children: List<String>
    )

    data class ZenSplitRecord(
        val splitId: String,
        val tabs: List<String>,
        val pinned: Boolean?
    )

    data class ZenLayoutRecord(
        val spacesOrder: List<String> = emptyList(),
        val essentialsByBucket: Map<String, List<String>> = emptyMap()
    )

    data class ZenPrefsState(
        val normalTabs: Boolean = false,
        val separateEssentials: Boolean? = null
    )

    sealed class DecodedZenRecord {
        data class Space(val record: ZenSpaceRecord) : DecodedZenRecord()
        data class Tab(val record: ZenTabRecord) : DecodedZenRecord()
        data class Folder(val record: ZenFolderRecord) : DecodedZenRecord()
        data class Split(val record: ZenSplitRecord) : DecodedZenRecord()
        data class Layout(val record: ZenLayoutRecord) : DecodedZenRecord()
    }

    data class ZenPinnedTabEntry(
        val tabId: String,
        val title: String,
        val url: String,
        val folderName: String?,
        val isPinned: Boolean
    )

    data class ZenEssentialEntry(
        val tabId: String,
        val title: String,
        val url: String,
        val containerGuid: String
    )

    data class ZenSyncSnapshot(
        val spaces: List<ZenSpaceRecord>,
        val essentialsByContainer: Map<String, List<ZenEssentialEntry>>,
        val pinnedBySpace: Map<String, List<ZenPinnedTabEntry>>,
        val normalTabsEnabled: Boolean,
        val separateEssentials: Boolean?
    )

    @Serializable
    sealed class ZenPendingOp {
        abstract val workspaceUuid: String
        abstract val updatedAtMs: Long

        @Serializable
        @SerialName("update_pin")
        data class UpdatePin(
            override val workspaceUuid: String,
            val tabId: String,
            val title: String,
            val url: String,
            override val updatedAtMs: Long
        ) : ZenPendingOp()

        @Serializable
        @SerialName("delete_pin")
        data class DeletePin(
            override val workspaceUuid: String,
            val tabId: String,
            override val updatedAtMs: Long
        ) : ZenPendingOp()

        @Serializable
        @SerialName("reorder_pins")
        data class ReorderPins(
            override val workspaceUuid: String,
            val orderedTabIds: List<String>,
            override val updatedAtMs: Long
        ) : ZenPendingOp()
    }

    /**
     * Strict tombstone rule: only JSON boolean `true` counts as deleted (string `"true"` does not).
     */
    fun isDeletedTombstone(cleartext: JsonObject): Boolean {
        val deletedPrimitive = cleartext["deleted"] as? JsonPrimitive
        return deletedPrimitive != null && !deletedPrimitive.isString && deletedPrimitive.booleanOrNull == true
    }

    fun decodeRecord(cleartext: JsonObject): DecodedZenRecord? {
        if (isDeletedTombstone(cleartext)) {
            return null
        }

        val kind = stringOrNull(cleartext["kind"]) ?: return null
        val data = cleartext["data"] as? JsonObject ?: return null

        return when (kind) {
            "space" -> decodeSpace(data)?.let { DecodedZenRecord.Space(it) }
            "tab" -> decodeTab(data)?.let { DecodedZenRecord.Tab(it) }
            "folder" -> decodeFolder(data)?.let { DecodedZenRecord.Folder(it) }
            "split" -> decodeSplit(data)?.let { DecodedZenRecord.Split(it) }
            "layout" -> DecodedZenRecord.Layout(decodeLayout(data))
            else -> null
        }
    }

    fun decodePrefs(cleartextRecords: List<JsonObject>): ZenPrefsState {
        var normalTabs = false
        var separateEssentials: Boolean? = null

        for (record in cleartextRecords) {
            if (isDeletedTombstone(record)) continue
            val valueObj = record["value"] as? JsonObject ?: continue
            if (valueObj.containsKey(NORMAL_TABS_PREF_KEY)) {
                parseFlag(valueObj[NORMAL_TABS_PREF_KEY])?.let { normalTabs = it }
            }
            if (valueObj.containsKey(SEPARATE_ESSENTIALS_PREF_KEY)) {
                parseFlag(valueObj[SEPARATE_ESSENTIALS_PREF_KEY])?.let { separateEssentials = it }
            }
        }
        return ZenPrefsState(
            normalTabs = normalTabs,
            separateEssentials = separateEssentials
        )
    }

    fun assembleSnapshot(
        records: List<Pair<String, JsonObject>>,
        prefs: ZenPrefsState = ZenPrefsState()
    ): ZenSyncSnapshot {
        val spacesById = linkedMapOf<String, ZenSpaceRecord>()
        val tabsById = linkedMapOf<String, ZenTabRecord>()
        val foldersById = linkedMapOf<String, ZenFolderRecord>()
        val splitsById = linkedMapOf<String, ZenSplitRecord>()
        var layout = ZenLayoutRecord()

        for ((_, cleartext) in records) {
            when (val decoded = decodeRecord(cleartext)) {
                is DecodedZenRecord.Space -> {
                    spacesById[decoded.record.uuid] = decoded.record
                }
                is DecodedZenRecord.Tab -> {
                    if (prefs.normalTabs || decoded.record.pinned != false) {
                        tabsById[decoded.record.tabId] = decoded.record
                    }
                }
                is DecodedZenRecord.Folder -> {
                    foldersById[decoded.record.folderId] = decoded.record
                }
                is DecodedZenRecord.Split -> {
                    if (prefs.normalTabs || decoded.record.pinned != false) {
                        splitsById[decoded.record.splitId] = decoded.record
                    }
                }
                is DecodedZenRecord.Layout -> {
                    layout = decoded.record
                }
                null -> Unit
            }
        }

        // Order spaces according to layout.spaces first, then append any remaining spaces
        val orderedSpaces = mutableListOf<ZenSpaceRecord>()
        val seenSpaceIds = mutableSetOf<String>()
        for (uuid in layout.spacesOrder) {
            val s = spacesById[uuid]
            if (s != null && seenSpaceIds.add(uuid)) {
                orderedSpaces += s
            }
        }
        for ((uuid, s) in spacesById) {
            if (seenSpaceIds.add(uuid)) {
                orderedSpaces += s
            }
        }

        // Essentials by container bucket ("default" or containerGuid)
        val essentialsByContainer = linkedMapOf<String, MutableList<ZenEssentialEntry>>()
        val seenEssentialIds = mutableSetOf<String>()

        fun addEssentialTab(tab: ZenTabRecord, bucketOverride: String? = null) {
            if (!isHttpUrl(tab.url)) return
            if (!seenEssentialIds.add(tab.tabId)) return
            val bucket = bucketOverride
                ?: tab.containerGuid?.takeIf { it.isNotBlank() && tab.defaultContainer != true }
                ?: "default"
            val list = essentialsByContainer.getOrPut(bucket) { mutableListOf() }
            list += ZenEssentialEntry(
                tabId = tab.tabId,
                title = (tab.staticLabel ?: tab.title).ifBlank { extractHostFallback(tab.url) },
                url = tab.url,
                containerGuid = bucket
            )
        }

        for ((bucket, ids) in layout.essentialsByBucket) {
            for (tabId in ids) {
                val tab = tabsById[tabId]
                if (tab != null && tab.essential) {
                    addEssentialTab(tab, bucketOverride = bucket.ifBlank { "default" })
                }
            }
        }
        for ((_, tab) in tabsById) {
            if (tab.essential) {
                addEssentialTab(tab)
            }
        }

        // Pinned / space items per workspace
        val pinnedBySpace = linkedMapOf<String, MutableList<ZenPinnedTabEntry>>()
        val assignedTabIds = mutableSetOf<String>()

        fun appendTabToSpace(
            workspaceUuid: String,
            tab: ZenTabRecord,
            folderName: String?
        ) {
            if (tab.essential || !isHttpUrl(tab.url)) return
            if (!assignedTabIds.add("${workspaceUuid}:${tab.tabId}")) return
            val list = pinnedBySpace.getOrPut(workspaceUuid) { mutableListOf() }
            list += ZenPinnedTabEntry(
                tabId = tab.tabId,
                title = (tab.staticLabel ?: tab.title).ifBlank { extractHostFallback(tab.url) },
                url = tab.url,
                folderName = folderName,
                isPinned = tab.pinned != false
            )
        }

        fun collectChildNode(
            workspaceUuid: String,
            childId: String,
            folderPrefix: String?,
            visited: MutableSet<String>
        ) {
            if (!visited.add(childId)) return
            val tab = tabsById[childId]
            if (tab != null) {
                appendTabToSpace(workspaceUuid, tab, folderPrefix)
                return
            }
            val split = splitsById[childId]
            if (split != null) {
                for (memberId in split.tabs) {
                    tabsById[memberId]?.let { appendTabToSpace(workspaceUuid, it, folderPrefix) }
                }
                return
            }
            val folder = foldersById[childId]
            if (folder != null) {
                val label = when {
                    folderPrefix.isNullOrBlank() -> folder.name.takeIf { it.isNotBlank() }
                    folder.name.isBlank() -> folderPrefix
                    else -> "$folderPrefix / ${folder.name}"
                }
                for (nestedId in folder.children) {
                    collectChildNode(workspaceUuid, nestedId, label, visited)
                }
            }
        }

        for (space in orderedSpaces) {
            val visited = mutableSetOf<String>()
            for (childId in space.children) {
                collectChildNode(space.uuid, childId, null, visited)
            }
        }

        // Also capture any tabs referencing workspaceUuid directly even if absent from space.children
        for ((_, tab) in tabsById) {
            val wsUuid = tab.workspaceUuid?.takeIf { it.isNotBlank() } ?: continue
            val folderName = tab.folderId?.let { foldersById[it]?.name }?.takeIf { it.isNotBlank() }
            appendTabToSpace(wsUuid, tab, folderName)
        }

        return ZenSyncSnapshot(
            spaces = orderedSpaces,
            essentialsByContainer = essentialsByContainer,
            pinnedBySpace = pinnedBySpace,
            normalTabsEnabled = prefs.normalTabs,
            separateEssentials = prefs.separateEssentials
        )
    }

    fun mapZenIconToSwiftfoxKey(icon: String?, spaceName: String): String {
        val combined = "${icon.orEmpty()} $spaceName".lowercase(Locale.ROOT)
        return when {
            combined.contains("work") || combined.contains("briefcase") || combined.contains("office") || combined.contains("💼") -> "work"
            combined.contains("book") || combined.contains("read") || combined.contains("study") || combined.contains("📚") || combined.contains("📖") -> "book"
            combined.contains("code") || combined.contains("dev") || combined.contains("git") || combined.contains("terminal") || combined.contains("💻") -> "code"
            combined.contains("shop") || combined.contains("cart") || combined.contains("bag") || combined.contains("buy") || combined.contains("🛒") -> "shop"
            combined.contains("star") || combined.contains("fav") || combined.contains("⭐") -> "star"
            combined.contains("home") || combined.contains("personal") || combined.contains("🏠") -> "home"
            else -> "layers"
        }
    }

    /**
     * Safety write gate (SPEC §7.5): refuses writes if `meta/global` reports
     * an unknown Sync storageVersion (!= 5), spaces engine version > 1, or `spaces` declined.
     */
    fun metaGlobalAllowsSpacesWrite(
        metaGlobalPayloadJson: String?,
        parser: kotlinx.serialization.json.Json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    ): Boolean = Sync15Transport.metaGlobalAllowsCollectionWrite(
        metaGlobalPayloadJson = metaGlobalPayloadJson,
        collection = SPACES_COLLECTION,
        maxVersion = 1.0,
        parser = parser
    )

    fun encodeNewTabRecord(
        tabId: String = UUID.randomUUID().toString().lowercase(Locale.ROOT),
        workspaceUuid: String?,
        url: String,
        title: String,
        essential: Boolean,
        containerGuid: String? = null
    ): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(tabId))
        put("kind", JsonPrimitive("tab"))
        put(
            "data",
            buildJsonObject {
                put("tabId", JsonPrimitive(tabId))
                put("url", JsonPrimitive(url))
                put("title", JsonPrimitive(title))
                put("essential", JsonPrimitive(essential))
                put("pinned", JsonPrimitive(true))
                if (!workspaceUuid.isNullOrBlank()) {
                    put("workspaceUuid", JsonPrimitive(workspaceUuid))
                }
                if (!containerGuid.isNullOrBlank() && containerGuid != "default") {
                    put("containerGuid", JsonPrimitive(containerGuid))
                    put("defaultContainer", JsonPrimitive(false))
                } else {
                    put("defaultContainer", JsonPrimitive(true))
                }
            }
        )
    }

    fun extractTabId(pinnedItemId: String, workspaceUuid: String): String? {
        val prefix = "${workspaceUuid}_"
        if (workspaceUuid.isBlank() || !pinnedItemId.startsWith(prefix)) return null
        return pinnedItemId.removePrefix(prefix).takeIf { it.isNotBlank() }
    }

    fun encodeTabTombstone(bsoId: String): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(bsoId))
        put("kind", JsonPrimitive("tab"))
        put("deleted", JsonPrimitive(true))
    }

    fun rewriteTabRecordForUpdate(
        existing: JsonObject,
        newTitle: String,
        newUrl: String
    ): JsonObject? {
        if (isDeletedTombstone(existing)) return null
        if (stringOrNull(existing["kind"]) != "tab") return null
        val cleanUrl = newUrl.trim()
        if (!isHttpUrl(cleanUrl)) return null
        val cleanTitle = newTitle.trim().ifBlank { extractHostFallback(cleanUrl) }
        val dataObj = existing["data"] as? JsonObject ?: return null
        val curUrl = stringOrNull(dataObj["url"]).orEmpty()
        val curTitle = stringOrNull(dataObj["title"]).orEmpty()
        val curStatic = stringOrNull(dataObj["staticLabel"])
        if (curUrl == cleanUrl && curTitle == cleanTitle && curStatic == cleanTitle) {
            return null
        }

        val updatedData = buildJsonObject {
            for ((k, v) in dataObj) {
                if (k != "url" && k != "title" && k != "staticLabel") {
                    put(k, v)
                }
            }
            put("url", JsonPrimitive(cleanUrl))
            put("title", JsonPrimitive(cleanTitle))
            put("staticLabel", JsonPrimitive(cleanTitle))
        }
        return replaceDataObject(existing, updatedData)
    }

    fun rewriteContainerRemovingTab(
        existing: JsonObject,
        tabId: String
    ): JsonObject? {
        if (isDeletedTombstone(existing)) return null
        val listKey = when (stringOrNull(existing["kind"])) {
            "space", "folder" -> "children"
            "split" -> "tabs"
            else -> return null
        }
        val dataObj = existing["data"] as? JsonObject ?: return null
        val currentIds = stringList(dataObj[listKey])
        if (tabId !in currentIds) return null
        val updatedIds = currentIds.filterNot { it == tabId }
        val updatedData = replaceStringListField(dataObj, listKey, updatedIds)
        return replaceDataObject(existing, updatedData)
    }

    fun permuteChildIdsInPlace(
        currentChildren: List<String>,
        orderedIds: List<String>
    ): List<String> {
        if (currentChildren.size < 2 || orderedIds.size < 2) return currentChildren
        val presentSet = currentChildren.toSet()
        val orderedPresent = orderedIds.asSequence()
            .filter { it in presentSet }
            .distinct()
            .toList()
        if (orderedPresent.size < 2) return currentChildren
        val targetSet = orderedPresent.toSet()
        var cursor = 0
        return currentChildren.map { child ->
            if (child in targetSet && cursor < orderedPresent.size) {
                orderedPresent[cursor++]
            } else {
                child
            }
        }
    }

    fun rewriteContainerWithReorderedChildren(
        existing: JsonObject,
        orderedTabIds: List<String>
    ): JsonObject? {
        if (isDeletedTombstone(existing)) return null
        val kind = stringOrNull(existing["kind"])
        if (kind != "space" && kind != "folder") return null
        val dataObj = existing["data"] as? JsonObject ?: return null
        val currentChildren = stringList(dataObj["children"])
        val reordered = permuteChildIdsInPlace(currentChildren, orderedTabIds)
        if (reordered == currentChildren) return null
        val updatedData = replaceStringListField(dataObj, "children", reordered)
        return replaceDataObject(existing, updatedData)
    }

    fun coalescePendingOps(
        existing: List<ZenPendingOp>,
        incoming: ZenPendingOp
    ): List<ZenPendingOp> {
        val next = existing.toMutableList()
        when (incoming) {
            is ZenPendingOp.UpdatePin -> {
                next.removeAll {
                    (it is ZenPendingOp.UpdatePin && it.tabId == incoming.tabId) ||
                        (it is ZenPendingOp.DeletePin && it.tabId == incoming.tabId)
                }
                next += incoming
            }
            is ZenPendingOp.DeletePin -> {
                next.removeAll {
                    (it is ZenPendingOp.UpdatePin && it.tabId == incoming.tabId) ||
                        (it is ZenPendingOp.DeletePin && it.tabId == incoming.tabId)
                }
                val idxIterator = next.listIterator()
                while (idxIterator.hasNext()) {
                    val op = idxIterator.next()
                    if (op is ZenPendingOp.ReorderPins && op.workspaceUuid == incoming.workspaceUuid) {
                        val remaining = op.orderedTabIds.filterNot { it == incoming.tabId }
                        if (remaining.size < 2) {
                            idxIterator.remove()
                        } else {
                            idxIterator.set(op.copy(orderedTabIds = remaining))
                        }
                    }
                }
                next += incoming
            }
            is ZenPendingOp.ReorderPins -> {
                next.removeAll {
                    it is ZenPendingOp.ReorderPins && it.workspaceUuid == incoming.workspaceUuid
                }
                if (incoming.orderedTabIds.size >= 2) {
                    next += incoming
                }
            }
        }
        return next
    }

    /**
     * Applies queued local Zen pinned item edits, deletions, and reorders against the current
     * decrypted `spaces` BSO list, returning only modified/tombstoned BSOs while leaving folders,
     * splits, normal tabs, and unknown BSO fields intact.
     */
    fun buildMutationBsos(
        decryptedRecords: List<Pair<String, JsonObject>>,
        ops: List<ZenPendingOp>
    ): List<Pair<String, JsonObject>> {
        if (ops.isEmpty() || decryptedRecords.isEmpty()) return emptyList()
        val working = linkedMapOf<String, JsonObject>()
        for ((bsoId, obj) in decryptedRecords) {
            working[bsoId] = obj
        }
        val dirtyIds = linkedSetOf<String>()

        fun findTabEntry(tabId: String): Pair<String, JsonObject>? {
            working[tabId]?.let { obj ->
                if (!isDeletedTombstone(obj) && stringOrNull(obj["kind"]) == "tab") {
                    return tabId to obj
                }
            }
            for ((bsoId, obj) in working) {
                if (isDeletedTombstone(obj)) continue
                if (stringOrNull(obj["kind"]) != "tab") continue
                val data = obj["data"] as? JsonObject ?: continue
                if (stringOrNull(data["tabId"]) == tabId) {
                    return bsoId to obj
                }
            }
            return null
        }

        fun collectFolderIdsForSpace(spaceUuid: String): Set<String> {
            val spaceObj = working.values.firstOrNull { obj ->
                !isDeletedTombstone(obj) &&
                    stringOrNull(obj["kind"]) == "space" &&
                    stringOrNull((obj["data"] as? JsonObject)?.get("uuid")) == spaceUuid
            } ?: return emptySet()
            val foldersById = linkedMapOf<String, JsonObject>()
            for (obj in working.values) {
                if (!isDeletedTombstone(obj) && stringOrNull(obj["kind"]) == "folder") {
                    val fId = stringOrNull((obj["data"] as? JsonObject)?.get("folderId"))
                    if (!fId.isNullOrBlank()) foldersById[fId] = obj
                }
            }
            val result = linkedSetOf<String>()
            val queue = ArrayDeque<String>()
            val spaceData = spaceObj["data"] as? JsonObject ?: return emptySet()
            stringList(spaceData["children"]).forEach { queue.addLast(it) }
            while (queue.isNotEmpty()) {
                val id = queue.removeFirst()
                val folderObj = foldersById[id] ?: continue
                if (result.add(id)) {
                    val fData = folderObj["data"] as? JsonObject ?: continue
                    stringList(fData["children"]).forEach { queue.addLast(it) }
                }
            }
            return result
        }

        for (op in ops) {
            when (op) {
                is ZenPendingOp.UpdatePin -> {
                    val (bsoId, existing) = findTabEntry(op.tabId) ?: continue
                    val rewritten = rewriteTabRecordForUpdate(existing, op.title, op.url)
                    if (rewritten != null) {
                        working[bsoId] = rewritten
                        dirtyIds += bsoId
                    }
                }
                is ZenPendingOp.DeletePin -> {
                    val existingTab = findTabEntry(op.tabId)
                    if (existingTab != null) {
                        val (bsoId, _) = existingTab
                        working[bsoId] = encodeTabTombstone(bsoId)
                        dirtyIds += bsoId
                    }
                    val keys = working.keys.toList()
                    for (id in keys) {
                        val obj = working[id] ?: continue
                        val updated = rewriteContainerRemovingTab(obj, op.tabId)
                        if (updated != null) {
                            working[id] = updated
                            dirtyIds += id
                        }
                    }
                }
                is ZenPendingOp.ReorderPins -> {
                    val folderIdsInSpace = collectFolderIdsForSpace(op.workspaceUuid)
                    val keys = working.keys.toList()
                    for (id in keys) {
                        val obj = working[id] ?: continue
                        if (isDeletedTombstone(obj)) continue
                        val kind = stringOrNull(obj["kind"])
                        val data = obj["data"] as? JsonObject ?: continue
                        val matchesSpace = when (kind) {
                            "space" -> stringOrNull(data["uuid"]) == op.workspaceUuid
                            "folder" -> {
                                val fId = stringOrNull(data["folderId"])
                                val fWs = stringOrNull(data["workspaceUuid"])
                                fWs == op.workspaceUuid || (fId != null && fId in folderIdsInSpace)
                            }
                            else -> false
                        }
                        if (matchesSpace) {
                            val updated = rewriteContainerWithReorderedChildren(obj, op.orderedTabIds)
                            if (updated != null) {
                                working[id] = updated
                                dirtyIds += id
                            }
                        }
                    }
                }
            }
        }
        return dirtyIds.mapNotNull { id -> working[id]?.let { id to it } }
    }

    private fun replaceDataObject(existing: JsonObject, newData: JsonObject): JsonObject = buildJsonObject {
        for ((k, v) in existing) {
            if (k != "data") put(k, v)
        }
        put("data", newData)
    }

    private fun replaceStringListField(
        dataObj: JsonObject,
        fieldName: String,
        values: List<String>
    ): JsonObject = buildJsonObject {
        for ((k, v) in dataObj) {
            if (k != fieldName) put(k, v)
        }
        put(fieldName, buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
    }

    private fun decodeSpace(data: JsonObject): ZenSpaceRecord? {
        val uuid = stringOrNull(data["uuid"])?.takeIf { it.isNotEmpty() } ?: return null
        val rawName = stringOrNull(data["name"])?.trim().orEmpty()
        val themeObj = data["theme"] as? JsonObject
        val primaryHex = themeObj?.let { extractPrimaryThemeHex(it) }
        return ZenSpaceRecord(
            uuid = uuid,
            name = rawName.ifEmpty { "Zen Space" },
            icon = stringOrNull(data["icon"]),
            themeColorHex = primaryHex,
            containerGuid = stringOrNull(data["containerGuid"]),
            children = stringList(data["children"])
        )
    }

    private fun decodeTab(data: JsonObject): ZenTabRecord? {
        val tabId = stringOrNull(data["tabId"])?.takeIf { it.isNotEmpty() } ?: return null
        val url = stringOrNull(data["url"])?.takeIf { it.isNotEmpty() } ?: return null
        return ZenTabRecord(
            tabId = tabId,
            url = url,
            title = stringOrNull(data["title"]).orEmpty(),
            containerGuid = stringOrNull(data["containerGuid"]),
            essential = parseFlag(data["essential"]) == true,
            workspaceUuid = stringOrNull(data["workspaceUuid"]),
            folderId = stringOrNull(data["folderId"]),
            staticLabel = stringOrNull(data["staticLabel"]),
            defaultContainer = parseFlag(data["defaultContainer"]),
            pinned = parseFlag(data["pinned"])
        )
    }

    private fun decodeFolder(data: JsonObject): ZenFolderRecord? {
        val folderId = stringOrNull(data["folderId"])?.takeIf { it.isNotEmpty() } ?: return null
        return ZenFolderRecord(
            folderId = folderId,
            name = stringOrNull(data["name"]).orEmpty(),
            children = stringList(data["children"])
        )
    }

    private fun decodeSplit(data: JsonObject): ZenSplitRecord? {
        val splitId = stringOrNull(data["splitId"])?.takeIf { it.isNotEmpty() } ?: return null
        return ZenSplitRecord(
            splitId = splitId,
            tabs = stringList(data["tabs"]),
            pinned = parseFlag(data["pinned"])
        )
    }

    private fun decodeLayout(data: JsonObject): ZenLayoutRecord {
        val spacesOrder = stringList(data["spaces"])
        val essentialsMap = linkedMapOf<String, List<String>>()
        val rawEssentials = data["essentials"] as? JsonObject
        if (rawEssentials != null) {
            for ((bucket, value) in rawEssentials) {
                essentialsMap[bucket] = stringList(value)
            }
        }
        return ZenLayoutRecord(
            spacesOrder = spacesOrder,
            essentialsByBucket = essentialsMap
        )
    }

    internal fun extractPrimaryThemeHex(themeObj: JsonObject): String? {
        val gc = themeObj["gradientColors"] as? JsonArray ?: return null
        var firstHex: String? = null
        for (element in gc) {
            val dotObj = element as? JsonObject
            val isPrimary = dotObj?.let { parseFlag(it["isPrimary"]) == true } == true
            val hex = decodeColorElement(element)
            if (hex != null) {
                if (isPrimary) return hex
                if (firstHex == null) firstHex = hex
            }
        }
        return firstHex
    }

    private fun decodeColorElement(element: JsonElement): String? = when (element) {
        is JsonPrimitive -> {
            if (element.isString) normalizeColorString(element.content) else null
        }
        is JsonArray -> {
            if (element.size >= 3) {
                val r = (element[0] as? JsonPrimitive)?.doubleOrNull
                val g = (element[1] as? JsonPrimitive)?.doubleOrNull
                val b = (element[2] as? JsonPrimitive)?.doubleOrNull
                if (r != null && g != null && b != null) rgbToHex(r, g, b) else null
            } else {
                null
            }
        }
        is JsonObject -> {
            val nested = element["color"] ?: element["c"] ?: element["value"]
            if (nested != null) decodeColorElement(nested) else null
        }
    }

    internal fun normalizeColorString(raw: String): String? {
        val text = raw.trim()
        if (text.startsWith("#")) {
            val body = text.substring(1)
            val expanded = when (body.length) {
                3 -> body.map { "$it$it" }.joinToString("")
                6 -> body
                8 -> body.substring(0, 6)
                else -> return null
            }
            return if (expanded.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                "#${expanded.lowercase(Locale.ROOT)}"
            } else {
                null
            }
        }
        if (text.startsWith("rgb", ignoreCase = true)) {
            val parts = Regex("""\d+(?:\.\d+)?""").findAll(text).mapNotNull { it.value.toDoubleOrNull() }.toList()
            if (parts.size >= 3) {
                return rgbToHex(parts[0], parts[1], parts[2])
            }
        }
        return null
    }

    private fun rgbToHex(r: Double, g: Double, b: Double): String {
        val ri = r.toInt().coerceIn(0, 255)
        val gi = g.toInt().coerceIn(0, 255)
        val bi = b.toInt().coerceIn(0, 255)
        return String.format(Locale.ROOT, "#%02x%02x%02x", ri, gi, bi)
    }

    internal fun parseFlag(element: JsonElement?): Boolean? {
        val prim = element as? JsonPrimitive ?: return null
        if (!prim.isString) return prim.booleanOrNull
        return when (prim.content.trim().lowercase(Locale.ROOT)) {
            "true", "1" -> true
            "false", "0" -> false
            else -> null
        }
    }

    private fun stringOrNull(element: JsonElement?): String? {
        val prim = element as? JsonPrimitive ?: return null
        return if (prim.isString) prim.content else null
    }

    private fun stringList(element: JsonElement?): List<String> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { stringOrNull(it) }
    }

    private fun isHttpUrl(url: String): Boolean {
        val trimmed = url.trim()
        val isHttp = trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        return isHttp && OmniboxEngine.extractHost(trimmed).isNotBlank()
    }

    private fun extractHostFallback(url: String): String =
        OmniboxEngine.extractHost(url).takeIf { it.isNotBlank() } ?: url
}
