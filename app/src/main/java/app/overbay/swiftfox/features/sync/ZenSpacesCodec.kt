package app.overbay.swiftfox.features.sync

import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
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
