package app.overbay.swiftfox.features.sync

import app.overbay.swiftfox.data.db.EssentialEntity
import app.overbay.swiftfox.data.db.FilterListEntity
import app.overbay.swiftfox.data.db.PinnedFolderEntity
import app.overbay.swiftfox.data.db.SiteOverrideEntity
import app.overbay.swiftfox.data.db.SpaceEntity
import app.overbay.swiftfox.data.db.SwiftfoxDatabase
import app.overbay.swiftfox.data.db.TabEntity
import app.overbay.swiftfox.data.db.UserscriptEntity
import app.overbay.swiftfox.data.db.ZappedRuleEntity
import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.features.adblock.BuiltInFilterLists
import app.overbay.swiftfox.features.adblock.FilterListRepository
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

@Serializable
data class SwiftfoxBaselineItem(
    val bsoId: String,
    val kind: String,
    val contentHash: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val localContentHash: String? = null
)

@Serializable
data class SwiftfoxSyncBaseline(
    val schema: Int = 1,
    val hasCompletedInitialSync: Boolean = false,
    val items: Map<String, SwiftfoxBaselineItem> = emptyMap()
)

/**
 * Pure two-way conflict-free merge planner for Swiftfox-only client sync.
 * Produces deterministic Room/DataStore mutations, outgoing encrypted-ready BSOs (including tombstones),
 * and the updated baseline without touching Firefox or Zen Sync records.
 */
object SwiftfoxSyncReconciler {

    data class LocalSnapshot(
        val settings: BrowserSettings,
        val settingsUpdatedAtMs: Long,
        val siteOverrides: List<SiteOverrideEntity>,
        val zappedRules: List<ZappedRuleEntity>,
        val filterLists: List<FilterListEntity>,
        val userscripts: List<UserscriptEntity>,
        val localSpaces: List<SpaceEntity>,
        val zenSpacesCount: Int,
        val localEssentials: List<EssentialEntity>,
        val zenEssentialsCount: Int,
        val localPinnedFolders: List<PinnedFolderEntity> = emptyList(),
        val localPinnedTabs: List<TabEntity> = emptyList(),
        val zenSpaceIds: Set<String> = emptySet()
    )

    data class SyncedSpacePlan(
        val upserts: List<SpaceEntity>,
        val deleteIds: List<String>,
        val fallbackSpaceId: String?
    )

    data class SyncedEssentialPlan(
        val upserts: List<EssentialEntity>,
        val deleteIds: List<String>
    )

    data class SyncedPinnedFolderPlan(
        val upserts: List<PinnedFolderEntity>,
        val deleteIds: List<String>
    )

    data class SyncedPinnedTabPlan(
        val upserts: List<TabEntity>,
        val deleteIds: List<String>
    )

    data class SwiftfoxSyncPlan(
        val settingsToApply: Pair<BrowserSettings, Long>?,
        val settingsTimestampToRecord: Long?,
        val siteOverridesToUpsert: List<SiteOverrideEntity>,
        val siteOverrideHostsToDelete: List<String>,
        val zappedRulesToUpsert: List<ZappedRuleEntity>,
        val zappedRuleIdsToDelete: List<String>,
        val filterListsToUpsert: List<FilterListEntity>,
        val filterListIdsToDelete: List<String>,
        val userscriptsToUpsert: List<UserscriptEntity>,
        val userscriptIdsToDelete: List<String>,
        val spacesPlan: SyncedSpacePlan,
        val essentialsPlan: SyncedEssentialPlan,
        val pinnedFoldersPlan: SyncedPinnedFolderPlan = SyncedPinnedFolderPlan(emptyList(), emptyList()),
        val pinnedTabsPlan: SyncedPinnedTabPlan = SyncedPinnedTabPlan(emptyList(), emptyList()),
        val outgoingBsos: List<Pair<String, JsonObject>>,
        val nextBaseline: SwiftfoxSyncBaseline,
        val pulledCount: Int,
        val pushedCount: Int
    )

    private data class LocalItemWrapper<T>(
        val bsoId: String,
        val kind: String,
        val value: T,
        val contentHash: String,
        val explicitUpdatedAt: Long?,
        val isUnmodifiedSeed: Boolean,
        val allowRemoteDelete: Boolean,
        val encode: (Long) -> JsonObject
    )

    private data class RemoteItemWrapper<T>(
        val bsoId: String,
        val kind: String,
        val updatedAt: Long,
        val deleted: Boolean,
        val contentHash: String,
        val value: T?
    )

    private data class MergeOutcome<L, R>(
        val pullUpsert: R? = null,
        val pullDelete: L? = null,
        val outgoingBso: Pair<String, JsonObject>? = null,
        val baselineItem: SwiftfoxBaselineItem? = null,
        val pulled: Boolean = false,
        val pushed: Boolean = false
    )

    private val defaultSpacesById: Map<String, Pair<String, String>> = mapOf(
        SwiftfoxDatabase.DEFAULT_SPACE_ID to ("Personal" to "home"),
        "space_work" to ("Work" to "work"),
        "space_reading" to ("Reading" to "book")
    )
    private val defaultSpaceOrderById: Map<String, Int> = mapOf(
        SwiftfoxDatabase.DEFAULT_SPACE_ID to 0,
        "space_work" to 1,
        "space_reading" to 2
    )

    private data class SeedEssential(val title: String, val url: String, val domain: String, val order: Int)
    private val defaultEssentialsById: Map<String, SeedEssential> = mapOf(
        "ess_google" to SeedEssential("Google", "https://www.google.com", "google.com", 0),
        "ess_github" to SeedEssential("GitHub", "https://github.com", "github.com", 1),
        "ess_youtube" to SeedEssential("YouTube", "https://m.youtube.com", "youtube.com", 2),
        "ess_reddit" to SeedEssential("Reddit", "https://www.reddit.com", "reddit.com", 3),
        "ess_wiki" to SeedEssential("Wikipedia", "https://en.wikipedia.org", "wikipedia.org", 4),
        "ess_hn" to SeedEssential("Hacker News", "https://news.ycombinator.com", "news.ycombinator.com", 5)
    )

    private val defaultFilterListsById: Map<String, FilterListEntity> =
        BuiltInFilterLists.defaults.associateBy { it.id }

    private val defaultSettingsHash: String by lazy {
        SwiftfoxSyncCodec.settingsContentHash(BrowserSettings())
    }

    fun buildPlan(
        local: LocalSnapshot,
        baseline: SwiftfoxSyncBaseline,
        remoteRawRecords: List<Pair<String, JsonObject>>,
        nowMs: Long = System.currentTimeMillis()
    ): SwiftfoxSyncPlan {
        val decodedById = linkedMapOf<String, SwiftfoxSyncCodec.DecodedSwiftfoxRecord>()
        val unsupportedFutureIds = linkedSetOf<String>()
        for ((bsoId, cleartext) in remoteRawRecords) {
            when (val decoded = SwiftfoxSyncCodec.decodeRecord(bsoId, cleartext)) {
                is SwiftfoxSyncCodec.DecodedSwiftfoxRecord.UnsupportedFuture -> unsupportedFutureIds += bsoId
                null -> Unit
                else -> decodedById[decoded.bsoId] = decoded
            }
        }

        val outgoingBsos = mutableListOf<Pair<String, JsonObject>>()
        val nextBaselineItems = linkedMapOf<String, SwiftfoxBaselineItem>()
        var pulledCount = 0
        var pushedCount = 0

        // Preserve baseline entries for unsupported future records so we never lose track of them
        for (futureId in unsupportedFutureIds) {
            baseline.items[futureId]?.let { nextBaselineItems[futureId] = it }
        }

        // 1. Settings (single BSO: swiftfox_settings)
        val localSettingsHash = SwiftfoxSyncCodec.settingsContentHash(local.settings)
        val baselineSettings = baseline.items[SwiftfoxSyncCodec.SETTINGS_BSO_ID]
        val remoteSettings = decodedById[SwiftfoxSyncCodec.SETTINGS_BSO_ID] as? SwiftfoxSyncCodec.DecodedSwiftfoxRecord.Settings
        val remoteSettingsTombstone = decodedById[SwiftfoxSyncCodec.SETTINGS_BSO_ID] as? SwiftfoxSyncCodec.DecodedSwiftfoxRecord.Tombstone

        var settingsToApply: Pair<BrowserSettings, Long>? = null
        var settingsTimestampToRecord: Long? = null

        if (SwiftfoxSyncCodec.SETTINGS_BSO_ID !in unsupportedFutureIds) {
            val localSettingsUpdatedAt = when {
                baselineSettings != null && localSettingsHash != baselineSettings.contentHash ->
                    maxOf(local.settingsUpdatedAtMs, baselineSettings.updatedAt + 1L)
                local.settingsUpdatedAtMs > 0L -> local.settingsUpdatedAtMs
                baselineSettings != null -> baselineSettings.updatedAt
                localSettingsHash != defaultSettingsHash -> nowMs
                else -> 0L
            }
            when {
                remoteSettings == null -> {
                    if (remoteSettingsTombstone == null || localSettingsUpdatedAt > remoteSettingsTombstone.updatedAt) {
                        val pushTs = localSettingsUpdatedAt.takeIf { it > 0L } ?: nowMs
                        outgoingBsos += SwiftfoxSyncCodec.SETTINGS_BSO_ID to
                            SwiftfoxSyncCodec.encodeSettingsRecord(local.settings, pushTs)
                        nextBaselineItems[SwiftfoxSyncCodec.SETTINGS_BSO_ID] = SwiftfoxBaselineItem(
                            bsoId = SwiftfoxSyncCodec.SETTINGS_BSO_ID,
                            kind = SwiftfoxSyncCodec.KIND_SETTINGS,
                            contentHash = localSettingsHash,
                            updatedAt = pushTs
                        )
                        if (local.settingsUpdatedAtMs == 0L) {
                            settingsTimestampToRecord = pushTs
                        }
                        pushedCount++
                    }
                }
                remoteSettings.updatedAt > localSettingsUpdatedAt -> {
                    settingsToApply = remoteSettings.settings to remoteSettings.updatedAt
                    nextBaselineItems[SwiftfoxSyncCodec.SETTINGS_BSO_ID] = SwiftfoxBaselineItem(
                        bsoId = SwiftfoxSyncCodec.SETTINGS_BSO_ID,
                        kind = SwiftfoxSyncCodec.KIND_SETTINGS,
                        contentHash = remoteSettings.contentHash,
                        updatedAt = remoteSettings.updatedAt
                    )
                    if (remoteSettings.contentHash != localSettingsHash) pulledCount++
                }
                localSettingsUpdatedAt > remoteSettings.updatedAt -> {
                    outgoingBsos += SwiftfoxSyncCodec.SETTINGS_BSO_ID to
                        SwiftfoxSyncCodec.encodeSettingsRecord(local.settings, localSettingsUpdatedAt)
                    nextBaselineItems[SwiftfoxSyncCodec.SETTINGS_BSO_ID] = SwiftfoxBaselineItem(
                        bsoId = SwiftfoxSyncCodec.SETTINGS_BSO_ID,
                        kind = SwiftfoxSyncCodec.KIND_SETTINGS,
                        contentHash = localSettingsHash,
                        updatedAt = localSettingsUpdatedAt
                    )
                    if (local.settingsUpdatedAtMs == 0L) {
                        settingsTimestampToRecord = localSettingsUpdatedAt
                    }
                    pushedCount++
                }
                else -> {
                    nextBaselineItems[SwiftfoxSyncCodec.SETTINGS_BSO_ID] = SwiftfoxBaselineItem(
                        bsoId = SwiftfoxSyncCodec.SETTINGS_BSO_ID,
                        kind = SwiftfoxSyncCodec.KIND_SETTINGS,
                        contentHash = remoteSettings.contentHash,
                        updatedAt = remoteSettings.updatedAt
                    )
                }
            }
        }

        // 2. Site Overrides
        val localOverridesMap = local.siteOverrides
            .filter { !it.isDefault() && OmniboxEngine.normalizeHost(it.host).isNotBlank() }
            .associateBy { SwiftfoxSyncCodec.siteOverrideBsoId(it.host) }
            .mapValues { (bsoId, entity) ->
                val normalized = entity.copy(host = OmniboxEngine.normalizeHost(entity.host))
                LocalItemWrapper(
                    bsoId = bsoId,
                    kind = SwiftfoxSyncCodec.KIND_SITE_OVERRIDE,
                    value = normalized,
                    contentHash = SwiftfoxSyncCodec.siteOverrideContentHash(normalized),
                    explicitUpdatedAt = null,
                    isUnmodifiedSeed = false,
                    allowRemoteDelete = true,
                    encode = { ts -> SwiftfoxSyncCodec.encodeSiteOverrideRecord(normalized, ts) }
                )
            }
        val remoteOverridesMap = buildRemoteMap<SwiftfoxSyncCodec.DecodedSwiftfoxRecord.SiteOverride>(
            decodedById = decodedById,
            kind = SwiftfoxSyncCodec.KIND_SITE_OVERRIDE,
            extract = { rec ->
                if (rec is SwiftfoxSyncCodec.DecodedSwiftfoxRecord.SiteOverride) {
                    Triple(rec.updatedAt, rec.contentHash, rec)
                } else null
            }
        )
        val siteOverridesToUpsert = mutableListOf<SiteOverrideEntity>()
        val siteOverrideHostsToDelete = mutableListOf<String>()
        for (outcome in mergeCollection(SwiftfoxSyncCodec.KIND_SITE_OVERRIDE, localOverridesMap, baseline, remoteOverridesMap, unsupportedFutureIds, nowMs)) {
            outcome.pullUpsert?.let { siteOverridesToUpsert += it.entity }
            outcome.pullDelete?.let { siteOverrideHostsToDelete += it.host }
            outcome.outgoingBso?.let { outgoingBsos += it }
            outcome.baselineItem?.let { nextBaselineItems[it.bsoId] = it }
            if (outcome.pulled) pulledCount++
            if (outcome.pushed) pushedCount++
        }

        // 3. Element Zapper Rules
        val localZapMap = local.zappedRules
            .associateBy { SwiftfoxSyncCodec.zappedRuleBsoId(it.host, it.selector) }
            .mapValues { (bsoId, entity) ->
                LocalItemWrapper(
                    bsoId = bsoId,
                    kind = SwiftfoxSyncCodec.KIND_ZAPPED_RULE,
                    value = entity,
                    contentHash = SwiftfoxSyncCodec.zappedRuleContentHash(entity),
                    explicitUpdatedAt = null,
                    isUnmodifiedSeed = false,
                    allowRemoteDelete = true,
                    encode = { ts -> SwiftfoxSyncCodec.encodeZappedRuleRecord(entity, ts) }
                )
            }
        val remoteZapMap = buildRemoteMap<SwiftfoxSyncCodec.DecodedSwiftfoxRecord.ZappedRule>(
            decodedById = decodedById,
            kind = SwiftfoxSyncCodec.KIND_ZAPPED_RULE,
            extract = { rec ->
                if (rec is SwiftfoxSyncCodec.DecodedSwiftfoxRecord.ZappedRule) {
                    Triple(rec.updatedAt, rec.contentHash, rec)
                } else null
            }
        )
        val zappedRulesToUpsert = mutableListOf<ZappedRuleEntity>()
        val zappedRuleIdsToDelete = mutableListOf<String>()
        for (outcome in mergeCollection(SwiftfoxSyncCodec.KIND_ZAPPED_RULE, localZapMap, baseline, remoteZapMap, unsupportedFutureIds, nowMs)) {
            outcome.pullUpsert?.let { remote ->
                val existingLocal = localZapMap[remote.bsoId]?.value
                zappedRulesToUpsert += if (existingLocal != null) {
                    existingLocal.copy(
                        label = remote.entity.label,
                        enabled = remote.entity.enabled
                    )
                } else {
                    remote.entity
                }
            }
            outcome.pullDelete?.let { zappedRuleIdsToDelete += it.id }
            outcome.outgoingBso?.let { outgoingBsos += it }
            outcome.baselineItem?.let { nextBaselineItems[it.bsoId] = it }
            if (outcome.pulled) pulledCount++
            if (outcome.pushed) pushedCount++
        }

        // 4. Filter Lists (built-in enabled state + custom lists, capped at MAX_CUSTOM_FILTER_LISTS)
        val localFilterMap = local.filterLists
            .associateBy { SwiftfoxSyncCodec.filterListBsoId(it) }
            .mapValues { (bsoId, entity) ->
                val seedDefault = if (entity.isBuiltIn) defaultFilterListsById[entity.id] else null
                val isUnmodifiedSeed = seedDefault != null &&
                    seedDefault.enabled == entity.enabled &&
                    seedDefault.url.trim().equals(entity.url.trim(), ignoreCase = true)
                LocalItemWrapper(
                    bsoId = bsoId,
                    kind = SwiftfoxSyncCodec.KIND_FILTER_LIST,
                    value = entity,
                    contentHash = SwiftfoxSyncCodec.filterListContentHash(entity),
                    explicitUpdatedAt = null,
                    isUnmodifiedSeed = isUnmodifiedSeed,
                    allowRemoteDelete = !entity.isBuiltIn,
                    encode = { ts -> SwiftfoxSyncCodec.encodeFilterListRecord(entity, ts) }
                )
            }
        val remoteFilterMap = buildRemoteMap<SwiftfoxSyncCodec.DecodedSwiftfoxRecord.FilterList>(
            decodedById = decodedById,
            kind = SwiftfoxSyncCodec.KIND_FILTER_LIST,
            extract = { rec ->
                if (rec is SwiftfoxSyncCodec.DecodedSwiftfoxRecord.FilterList) {
                    Triple(rec.updatedAt, rec.contentHash, rec)
                } else null
            }
        )
        val filterListsToUpsert = mutableListOf<FilterListEntity>()
        val filterListIdsToDelete = mutableListOf<String>()
        var projectedCustomCount = local.filterLists.count { !it.isBuiltIn }
        for (outcome in mergeCollection(SwiftfoxSyncCodec.KIND_FILTER_LIST, localFilterMap, baseline, remoteFilterMap, unsupportedFutureIds, nowMs)) {
            outcome.pullUpsert?.let { remote ->
                val existingLocal = localFilterMap[remote.bsoId]?.value
                    ?: if (remote.isBuiltIn) local.filterLists.firstOrNull { it.id == remote.id } else null
                when {
                    existingLocal != null -> {
                        val urlChanged = !existingLocal.url.trim().equals(remote.url.trim(), ignoreCase = false)
                        filterListsToUpsert += existingLocal.copy(
                            name = if (existingLocal.isBuiltIn) existingLocal.name else remote.name,
                            url = if (existingLocal.isBuiltIn) existingLocal.url else remote.url,
                            enabled = remote.enabled,
                            ruleCount = if (urlChanged) 0 else existingLocal.ruleCount,
                            lastUpdatedAt = if (urlChanged) 0L else existingLocal.lastUpdatedAt,
                            etag = if (urlChanged) null else existingLocal.etag,
                            lastError = if (urlChanged) null else existingLocal.lastError
                        )
                    }
                    !remote.isBuiltIn && projectedCustomCount < FilterListRepository.MAX_CUSTOM_FILTER_LISTS -> {
                        val safeId = remote.id.takeIf { id ->
                            id.startsWith("fl_custom_") && local.filterLists.none { it.id == id }
                        } ?: "fl_custom_${remote.bsoId.removePrefix("flc_").take(12)}"
                        filterListsToUpsert += FilterListEntity(
                            id = safeId,
                            name = remote.name,
                            url = remote.url,
                            enabled = remote.enabled,
                            isBuiltIn = false
                        )
                        projectedCustomCount++
                    }
                }
            }
            outcome.pullDelete?.let { localList ->
                if (!localList.isBuiltIn) {
                    filterListIdsToDelete += localList.id
                    projectedCustomCount = (projectedCustomCount - 1).coerceAtLeast(0)
                }
            }
            outcome.outgoingBso?.let { outgoingBsos += it }
            outcome.baselineItem?.let { nextBaselineItems[it.bsoId] = it }
            if (outcome.pulled) pulledCount++
            if (outcome.pushed) pushedCount++
        }

        // 5. Userscripts
        val localUserscriptsMap = local.userscripts
            .associateBy { SwiftfoxSyncCodec.userscriptBsoId(it) }
            .mapValues { (bsoId, entity) ->
                LocalItemWrapper(
                    bsoId = bsoId,
                    kind = SwiftfoxSyncCodec.KIND_USERSCRIPT,
                    value = entity,
                    contentHash = SwiftfoxSyncCodec.userscriptContentHash(entity),
                    explicitUpdatedAt = entity.updatedAt.takeIf { it > 0L },
                    isUnmodifiedSeed = false,
                    allowRemoteDelete = true,
                    encode = { ts -> SwiftfoxSyncCodec.encodeUserscriptRecord(entity, ts) }
                )
            }
        val remoteUserscriptsMap = buildRemoteMap<SwiftfoxSyncCodec.DecodedSwiftfoxRecord.Userscript>(
            decodedById = decodedById,
            kind = SwiftfoxSyncCodec.KIND_USERSCRIPT,
            extract = { rec ->
                if (rec is SwiftfoxSyncCodec.DecodedSwiftfoxRecord.Userscript) {
                    Triple(rec.updatedAt, rec.contentHash, rec)
                } else null
            }
        )
        val userscriptsToUpsert = mutableListOf<UserscriptEntity>()
        val userscriptIdsToDelete = mutableListOf<String>()
        for (outcome in mergeCollection(SwiftfoxSyncCodec.KIND_USERSCRIPT, localUserscriptsMap, baseline, remoteUserscriptsMap, unsupportedFutureIds, nowMs)) {
            outcome.pullUpsert?.let { remote ->
                val existingLocal = localUserscriptsMap[remote.bsoId]?.value
                val finalEntity = if (existingLocal != null) {
                    remote.entity.copy(
                        id = existingLocal.id,
                        installedAt = existingLocal.installedAt
                    )
                } else {
                    remote.entity
                }
                userscriptsToUpsert += finalEntity
            }
            outcome.pullDelete?.let { userscriptIdsToDelete += it.id }
            outcome.outgoingBso?.let { outgoingBsos += it }
            outcome.baselineItem?.let { nextBaselineItems[it.bsoId] = it }
            if (outcome.pulled) pulledCount++
            if (outcome.pushed) pushedCount++
        }

        // 6. Local-only Spaces (syncGuid == null, ordered relatively among local-only spaces)
        val orderedLocalSpaces = local.localSpaces
            .filter { it.syncGuid == null }
            .sortedWith(compareBy<SpaceEntity> { it.sortOrder }.thenBy { it.createdAt })
        val localSpacesMap = orderedLocalSpaces.mapIndexed { idx, space ->
            val bsoId = SwiftfoxSyncCodec.spaceBsoId(space.id)
            val seedMeta = defaultSpacesById[space.id]
            val seedOrder = defaultSpaceOrderById[space.id]
            val isUnmodifiedSeed = seedMeta != null &&
                seedOrder == idx &&
                space.name == seedMeta.first &&
                space.iconKey == seedMeta.second
            bsoId to LocalItemWrapper(
                bsoId = bsoId,
                kind = SwiftfoxSyncCodec.KIND_SPACE,
                value = space to idx,
                contentHash = SwiftfoxSyncCodec.spaceContentHash(space, idx),
                explicitUpdatedAt = null,
                isUnmodifiedSeed = isUnmodifiedSeed,
                allowRemoteDelete = true,
                encode = { ts -> SwiftfoxSyncCodec.encodeSpaceRecord(space, idx, ts) }
            )
        }.toMap(LinkedHashMap())

        val remoteSpacesMap = buildRemoteMap<SwiftfoxSyncCodec.DecodedSwiftfoxRecord.Space>(
            decodedById = decodedById,
            kind = SwiftfoxSyncCodec.KIND_SPACE,
            extract = { rec ->
                if (rec is SwiftfoxSyncCodec.DecodedSwiftfoxRecord.Space) {
                    Triple(rec.updatedAt, rec.contentHash, rec)
                } else null
            }
        )

        val spaceMergeOutcomes = mergeCollection(SwiftfoxSyncCodec.KIND_SPACE, localSpacesMap, baseline, remoteSpacesMap, unsupportedFutureIds, nowMs)
        val mergedLocalSpaces = linkedMapOf<String, Pair<SpaceEntity, Int>>()
        orderedLocalSpaces.forEachIndexed { idx, sp -> mergedLocalSpaces[sp.id] = sp to idx }

        val candidateDeleteSpaceIds = mutableListOf<String>()
        var spacesMutated = false
        for (outcome in spaceMergeOutcomes) {
            outcome.pullUpsert?.let { remote ->
                val prev = mergedLocalSpaces[remote.entity.id]?.first
                mergedLocalSpaces[remote.entity.id] = remote.entity.copy(
                    createdAt = prev?.createdAt ?: remote.entity.createdAt
                ) to remote.localOrder
                spacesMutated = true
            }
            outcome.pullDelete?.let { (space, _) ->
                candidateDeleteSpaceIds += space.id
                mergedLocalSpaces.remove(space.id)
            }
            outcome.outgoingBso?.let { outgoingBsos += it }
            outcome.baselineItem?.let { nextBaselineItems[it.bsoId] = it }
            if (outcome.pulled) pulledCount++
            if (outcome.pushed) pushedCount++
        }

        // Guarantee at least one space always remains on the device
        val spaceIdsToDelete = mutableListOf<String>()
        if (mergedLocalSpaces.isEmpty() && local.zenSpacesCount == 0) {
            val rescued = orderedLocalSpaces.firstOrNull()
                ?: SpaceEntity(
                    id = SwiftfoxDatabase.DEFAULT_SPACE_ID,
                    name = "Personal",
                    iconKey = "home",
                    sortOrder = 0
                )
            mergedLocalSpaces[rescued.id] = rescued to 0
            candidateDeleteSpaceIds.filterTo(spaceIdsToDelete) { it != rescued.id }
            val rescuedBsoId = SwiftfoxSyncCodec.spaceBsoId(rescued.id)
            nextBaselineItems.remove(rescuedBsoId)
            spacesMutated = true
        } else {
            spaceIdsToDelete.addAll(candidateDeleteSpaceIds)
            if (spaceIdsToDelete.isNotEmpty()) spacesMutated = true
        }

        val sortedResultSpaces = mergedLocalSpaces.values
            .sortedWith(compareBy<Pair<SpaceEntity, Int>> { it.second }.thenBy { it.first.createdAt })
            .mapIndexed { idx, (sp, _) -> sp.copy(sortOrder = local.zenSpacesCount + idx) }
        val spaceUpserts = if (spacesMutated) {
            sortedResultSpaces
        } else {
            sortedResultSpaces.filter { updated ->
                val prev = orderedLocalSpaces.firstOrNull { it.id == updated.id }
                prev == null || prev != updated
            }
        }
        val fallbackSpaceId = sortedResultSpaces.firstOrNull()?.id

        // 7. Local-only Essentials (syncGuid == null, ordered relatively among local-only essentials)
        val orderedLocalEssentials = local.localEssentials
            .filter { it.syncGuid == null }
            .sortedBy { it.sortOrder }
        val localEssentialsMap = orderedLocalEssentials.mapIndexed { idx, ess ->
            val bsoId = SwiftfoxSyncCodec.essentialBsoId(ess.id)
            val seedEss = defaultEssentialsById[ess.id]
            val isUnmodifiedSeed = seedEss != null &&
                seedEss.order == idx &&
                ess.spaceId == null &&
                ess.customIcon == null &&
                ess.title == seedEss.title &&
                ess.url == seedEss.url &&
                ess.domain == seedEss.domain
            bsoId to LocalItemWrapper(
                bsoId = bsoId,
                kind = SwiftfoxSyncCodec.KIND_ESSENTIAL,
                value = ess to idx,
                contentHash = SwiftfoxSyncCodec.essentialContentHash(ess, idx),
                explicitUpdatedAt = null,
                isUnmodifiedSeed = isUnmodifiedSeed,
                allowRemoteDelete = true,
                encode = { ts -> SwiftfoxSyncCodec.encodeEssentialRecord(ess, idx, ts) }
            )
        }.toMap(LinkedHashMap())

        val remoteEssentialsMap = buildRemoteMap<SwiftfoxSyncCodec.DecodedSwiftfoxRecord.Essential>(
            decodedById = decodedById,
            kind = SwiftfoxSyncCodec.KIND_ESSENTIAL,
            extract = { rec ->
                if (rec is SwiftfoxSyncCodec.DecodedSwiftfoxRecord.Essential) {
                    Triple(rec.updatedAt, rec.contentHash, rec)
                } else null
            }
        )

        val essMergeOutcomes = mergeCollection(SwiftfoxSyncCodec.KIND_ESSENTIAL, localEssentialsMap, baseline, remoteEssentialsMap, unsupportedFutureIds, nowMs)
        val mergedLocalEssentials = linkedMapOf<String, Pair<EssentialEntity, Int>>()
        orderedLocalEssentials.forEachIndexed { idx, ess -> mergedLocalEssentials[ess.id] = ess to idx }

        val essentialIdsToDelete = mutableListOf<String>()
        var essentialsMutated = false
        for (outcome in essMergeOutcomes) {
            outcome.pullUpsert?.let { remote ->
                mergedLocalEssentials[remote.entity.id] = remote.entity to remote.localOrder
                essentialsMutated = true
            }
            outcome.pullDelete?.let { (ess, _) ->
                essentialIdsToDelete += ess.id
                mergedLocalEssentials.remove(ess.id)
                essentialsMutated = true
            }
            outcome.outgoingBso?.let { outgoingBsos += it }
            outcome.baselineItem?.let { nextBaselineItems[it.bsoId] = it }
            if (outcome.pulled) pulledCount++
            if (outcome.pushed) pushedCount++
        }

        val sortedResultEssentials = mergedLocalEssentials.values
            .sortedBy { it.second }
            .mapIndexed { idx, (ess, _) -> ess.copy(sortOrder = local.zenEssentialsCount + idx) }
        val essentialUpserts = if (essentialsMutated) {
            sortedResultEssentials
        } else {
            sortedResultEssentials.filter { updated ->
                val prev = orderedLocalEssentials.firstOrNull { it.id == updated.id }
                prev == null || prev != updated
            }
        }

        // 8. Local Pinned Folders & Local Pinned Tabs (ordered per Space with interleaved top-level sortOrder)
        val syncableLocalPinned = local.localPinnedTabs.filter { tab ->
            if (tab.isPrivate || !tab.isPinned || tab.essentialId != null || tab.zenPinnedId != null) {
                return@filter false
            }
            val parsed = SwiftfoxSyncCodec.pinnedTabHomeUrl(tab).toHttpUrlOrNull() ?: return@filter false
            parsed.scheme == "http" || parsed.scheme == "https"
        }

        val localFoldersBySpace = local.localPinnedFolders.groupBy { it.spaceId }
        val localPinsBySpace = syncableLocalPinned.groupBy { it.spaceId }
        val allLocalPinnedSpaces = (localFoldersBySpace.keys + localPinsBySpace.keys).distinct()

        val localFolderWithOrder = mutableListOf<Pair<PinnedFolderEntity, Int>>()
        val localPinnedWithOrder = mutableListOf<Pair<TabEntity, Int>>()

        for (spaceId in allLocalPinnedSpaces) {
            val spaceFolders = localFoldersBySpace[spaceId].orEmpty()
            val folderIds = spaceFolders.mapTo(mutableSetOf()) { it.id }
            val spacePins = localPinsBySpace[spaceId].orEmpty()

            // Top-level entries in this Space: folders + ungrouped pinned tabs share one order sequence
            val topLevelEntries = buildList<Triple<Int, Boolean, Any>> {
                spaceFolders.forEach { add(Triple(it.sortOrder, true, it)) }
                spacePins.filter { it.folderId == null || it.folderId !in folderIds }.forEach {
                    add(Triple(it.sortOrder, false, it.copy(folderId = null)))
                }
            }.sortedWith(compareBy<Triple<Int, Boolean, Any>> { it.first }.thenByDescending { it.second })

            topLevelEntries.forEachIndexed { idx, (_, _, item) ->
                when (item) {
                    is PinnedFolderEntity -> localFolderWithOrder += item to idx
                    is TabEntity -> localPinnedWithOrder += item to idx
                }
            }

            // Grouped pins inside each folder have their own 0..M-1 sortOrder within that folder
            for (folder in spaceFolders) {
                spacePins
                    .filter { it.folderId == folder.id }
                    .sortedBy { it.sortOrder }
                    .forEachIndexed { idx, tab ->
                        localPinnedWithOrder += tab to idx
                    }
            }
        }

        val validSpaceIds = (sortedResultSpaces.map { it.id } + local.zenSpaceIds).toSet()
        val defaultSpaceFallback = fallbackSpaceId
            ?: local.zenSpaceIds.firstOrNull()
            ?: SwiftfoxDatabase.DEFAULT_SPACE_ID

        val localFolderMap = localFolderWithOrder.associate { (folder, topIdx) ->
            val bsoId = SwiftfoxSyncCodec.pinnedFolderBsoId(folder.id)
            bsoId to LocalItemWrapper(
                bsoId = bsoId,
                kind = SwiftfoxSyncCodec.KIND_PINNED_FOLDER,
                value = folder to topIdx,
                contentHash = SwiftfoxSyncCodec.pinnedFolderContentHash(folder, topIdx),
                explicitUpdatedAt = null,
                isUnmodifiedSeed = false,
                allowRemoteDelete = true,
                encode = { ts -> SwiftfoxSyncCodec.encodePinnedFolderRecord(folder, topIdx, ts) }
            )
        }
        val remoteFolderMap = buildRemoteMap<SwiftfoxSyncCodec.DecodedSwiftfoxRecord.PinnedFolder>(
            decodedById = decodedById,
            kind = SwiftfoxSyncCodec.KIND_PINNED_FOLDER,
            extract = { rec ->
                if (rec is SwiftfoxSyncCodec.DecodedSwiftfoxRecord.PinnedFolder) {
                    Triple(rec.updatedAt, rec.contentHash, rec)
                } else null
            }
        )

        val folderMergeOutcomes = mergeCollection(
            SwiftfoxSyncCodec.KIND_PINNED_FOLDER,
            localFolderMap,
            baseline,
            remoteFolderMap,
            unsupportedFutureIds,
            nowMs
        )
        val mergedFoldersById = linkedMapOf<String, Pair<PinnedFolderEntity, Int>>()
        localFolderWithOrder.forEach { (folder, topIdx) ->
            mergedFoldersById[folder.id] = folder to topIdx
        }

        val pinnedFolderIdsToDelete = mutableListOf<String>()
        val mutatedPinnedSpaces = mutableSetOf<String>()
        val remappedFolderIds = mutableSetOf<String>()
        for (outcome in folderMergeOutcomes) {
            outcome.pullUpsert?.let { remote ->
                val prev = mergedFoldersById[remote.entity.id]?.first
                val resolvedSpaceId = if (remote.entity.spaceId in validSpaceIds) {
                    remote.entity.spaceId
                } else {
                    remappedFolderIds += remote.entity.id
                    defaultSpaceFallback
                }
                val mergedFolder = remote.entity.copy(
                    spaceId = resolvedSpaceId,
                    isExpanded = prev?.isExpanded ?: true,
                    createdAt = prev?.createdAt ?: remote.entity.createdAt
                )
                mergedFoldersById[mergedFolder.id] = mergedFolder to remote.localOrder
                mutatedPinnedSpaces += resolvedSpaceId
                if (prev != null) mutatedPinnedSpaces += prev.spaceId
            }
            outcome.pullDelete?.let { (folder, _) ->
                pinnedFolderIdsToDelete += folder.id
                mergedFoldersById.remove(folder.id)
                mutatedPinnedSpaces += folder.spaceId
            }
            outcome.outgoingBso?.let { outgoingBsos += it }
            outcome.baselineItem?.let { nextBaselineItems[it.bsoId] = it }
            if (outcome.pulled) pulledCount++
            if (outcome.pushed) pushedCount++
        }

        val localPinnedMap = localPinnedWithOrder.associate { (tab, perBucketIdx) ->
            val bsoId = SwiftfoxSyncCodec.pinnedTabBsoId(tab.id)
            bsoId to LocalItemWrapper(
                bsoId = bsoId,
                kind = SwiftfoxSyncCodec.KIND_PINNED_TAB,
                value = tab to perBucketIdx,
                contentHash = SwiftfoxSyncCodec.pinnedTabContentHash(tab, perBucketIdx),
                explicitUpdatedAt = null,
                isUnmodifiedSeed = false,
                allowRemoteDelete = true,
                encode = { ts -> SwiftfoxSyncCodec.encodePinnedTabRecord(tab, perBucketIdx, ts) }
            )
        }
        val remotePinnedMap = buildRemoteMap<SwiftfoxSyncCodec.DecodedSwiftfoxRecord.PinnedTab>(
            decodedById = decodedById,
            kind = SwiftfoxSyncCodec.KIND_PINNED_TAB,
            extract = { rec ->
                if (rec is SwiftfoxSyncCodec.DecodedSwiftfoxRecord.PinnedTab) {
                    Triple(rec.updatedAt, rec.contentHash, rec)
                } else null
            }
        )

        val pinnedMergeOutcomes = mergeCollection(
            SwiftfoxSyncCodec.KIND_PINNED_TAB,
            localPinnedMap,
            baseline,
            remotePinnedMap,
            unsupportedFutureIds,
            nowMs
        )
        val mergedPinnedById = linkedMapOf<String, Pair<TabEntity, Int>>()
        localPinnedWithOrder.forEach { (tab, perBucketIdx) ->
            mergedPinnedById[tab.id] = tab to perBucketIdx
        }

        val pinnedTabIdsToDelete = mutableListOf<String>()
        val remappedSpacePinIds = mutableSetOf<String>()
        for (outcome in pinnedMergeOutcomes) {
            outcome.pullUpsert?.let { remote ->
                val prev = mergedPinnedById[remote.entity.id]?.first
                val resolvedSpaceId = if (remote.entity.spaceId in validSpaceIds) {
                    remote.entity.spaceId
                } else {
                    remappedSpacePinIds += remote.entity.id
                    defaultSpaceFallback
                }
                val mergedEntity = if (prev != null) {
                    val prevHomeUrl = SwiftfoxSyncCodec.pinnedTabHomeUrl(prev)
                    val remoteHomeUrl = SwiftfoxSyncCodec.pinnedTabHomeUrl(remote.entity)
                    val homeUrlChanged = !prevHomeUrl.equals(remoteHomeUrl, ignoreCase = false)
                    prev.copy(
                        spaceId = resolvedSpaceId,
                        title = if (homeUrlChanged || prev.isUnloaded) remote.entity.title else prev.title,
                        url = if (homeUrlChanged || prev.isUnloaded) remoteHomeUrl else prev.url,
                        customTitle = remote.entity.title,
                        customIcon = remote.entity.customIcon,
                        isUnloaded = if (homeUrlChanged) true else prev.isUnloaded,
                        pinnedUrl = remoteHomeUrl,
                        folderId = remote.entity.folderId
                    )
                } else {
                    remote.entity.copy(spaceId = resolvedSpaceId)
                }
                mergedPinnedById[mergedEntity.id] = mergedEntity to remote.localOrder
                mutatedPinnedSpaces += resolvedSpaceId
                if (prev != null) mutatedPinnedSpaces += prev.spaceId
            }
            outcome.pullDelete?.let { (tab, _) ->
                pinnedTabIdsToDelete += tab.id
                mergedPinnedById.remove(tab.id)
                mutatedPinnedSpaces += tab.spaceId
            }
            outcome.outgoingBso?.let { outgoingBsos += it }
            outcome.baselineItem?.let { nextBaselineItems[it.bsoId] = it }
            if (outcome.pulled) pulledCount++
            if (outcome.pushed) pushedCount++
        }

        val localFolderPrevById = local.localPinnedFolders.associateBy { it.id }
        val localPinnedPrevById = local.localPinnedTabs.associateBy { it.id }
        val pinnedFolderUpserts = mutableListOf<PinnedFolderEntity>()
        val pinnedTabUpserts = mutableListOf<TabEntity>()

        val mergedFoldersBySpace = mergedFoldersById.values.groupBy { it.first.spaceId }
        val mergedPinsBySpace = mergedPinnedById.values.groupBy { it.first.spaceId }
        val allMergedSpaces = (mergedFoldersBySpace.keys + mergedPinsBySpace.keys).distinct()

        for (spaceId in allMergedSpaces) {
            val spaceFolders = mergedFoldersBySpace[spaceId].orEmpty()
            val validFolderIdsInSpace = spaceFolders.mapTo(mutableSetOf()) { it.first.id }
            val spacePins = mergedPinsBySpace[spaceId].orEmpty().map { (tab, order) ->
                val resolvedFolderId = tab.folderId?.takeIf { it in validFolderIdsInSpace }
                if (resolvedFolderId != tab.folderId) {
                    remappedSpacePinIds += tab.id
                    tab.copy(folderId = null) to order
                } else {
                    tab to order
                }
            }

            // Normalize top-level sortOrder across folders + ungrouped pins
            val topLevelItems = buildList<Triple<Int, Boolean, Any>> {
                spaceFolders.forEach { (folder, order) -> add(Triple(order, true, folder)) }
                spacePins.filter { it.first.folderId == null }.forEach { (tab, order) ->
                    add(Triple(order, false, tab))
                }
            }.sortedWith(
                compareBy<Triple<Int, Boolean, Any>> { it.first }
                    .thenByDescending { it.second }
                    .thenBy { (_, _, item) ->
                        when (item) {
                            is PinnedFolderEntity -> item.id
                            is TabEntity -> item.id
                            else -> ""
                        }
                    }
            )

            topLevelItems.forEachIndexed { idx, (_, _, item) ->
                when (item) {
                    is PinnedFolderEntity -> {
                        val finalFolder = item.copy(sortOrder = idx)
                        val prev = localFolderPrevById[finalFolder.id]
                        if (spaceId in mutatedPinnedSpaces || prev == null || prev != finalFolder) {
                            pinnedFolderUpserts += finalFolder
                        }
                        val bsoId = SwiftfoxSyncCodec.pinnedFolderBsoId(finalFolder.id)
                        val base = nextBaselineItems[bsoId]
                        if (base != null && !base.deleted &&
                            (finalFolder.id in remappedFolderIds || base.localContentHash != null)
                        ) {
                            val storedHash = SwiftfoxSyncCodec.pinnedFolderContentHash(finalFolder, idx)
                            nextBaselineItems[bsoId] = if (storedHash == base.contentHash) {
                                base.copy(localContentHash = null)
                            } else {
                                base.copy(localContentHash = storedHash)
                            }
                        }
                    }
                    is TabEntity -> {
                        val finalTab = item.copy(sortOrder = idx)
                        val prev = localPinnedPrevById[finalTab.id]
                        if (spaceId in mutatedPinnedSpaces || prev == null || prev != finalTab) {
                            pinnedTabUpserts += finalTab
                        }
                        val bsoId = SwiftfoxSyncCodec.pinnedTabBsoId(finalTab.id)
                        val base = nextBaselineItems[bsoId]
                        if (base != null && !base.deleted &&
                            (finalTab.id in remappedSpacePinIds || base.localContentHash != null)
                        ) {
                            val storedHash = SwiftfoxSyncCodec.pinnedTabContentHash(finalTab, idx)
                            nextBaselineItems[bsoId] = if (storedHash == base.contentHash) {
                                base.copy(localContentHash = null)
                            } else {
                                base.copy(localContentHash = storedHash)
                            }
                        }
                    }
                }
            }

            // Normalize per-folder sortOrder for pins inside each folder
            for ((folder, _) in spaceFolders) {
                spacePins
                    .filter { it.first.folderId == folder.id }
                    .sortedWith(compareBy<Pair<TabEntity, Int>> { it.second }.thenBy { it.first.id })
                    .forEachIndexed { idx, (tab, _) ->
                        val finalTab = tab.copy(sortOrder = idx)
                        val prev = localPinnedPrevById[finalTab.id]
                        if (spaceId in mutatedPinnedSpaces || prev == null || prev != finalTab) {
                            pinnedTabUpserts += finalTab
                        }
                        val bsoId = SwiftfoxSyncCodec.pinnedTabBsoId(finalTab.id)
                        val base = nextBaselineItems[bsoId]
                        if (base != null && !base.deleted &&
                            (finalTab.id in remappedSpacePinIds || base.localContentHash != null)
                        ) {
                            val storedHash = SwiftfoxSyncCodec.pinnedTabContentHash(finalTab, idx)
                            nextBaselineItems[bsoId] = if (storedHash == base.contentHash) {
                                base.copy(localContentHash = null)
                            } else {
                                base.copy(localContentHash = storedHash)
                            }
                        }
                    }
            }
        }

        return SwiftfoxSyncPlan(
            settingsToApply = settingsToApply,
            settingsTimestampToRecord = settingsTimestampToRecord,
            siteOverridesToUpsert = siteOverridesToUpsert,
            siteOverrideHostsToDelete = siteOverrideHostsToDelete,
            zappedRulesToUpsert = zappedRulesToUpsert,
            zappedRuleIdsToDelete = zappedRuleIdsToDelete,
            filterListsToUpsert = filterListsToUpsert,
            filterListIdsToDelete = filterListIdsToDelete,
            userscriptsToUpsert = userscriptsToUpsert,
            userscriptIdsToDelete = userscriptIdsToDelete,
            spacesPlan = SyncedSpacePlan(
                upserts = spaceUpserts,
                deleteIds = spaceIdsToDelete,
                fallbackSpaceId = fallbackSpaceId
            ),
            essentialsPlan = SyncedEssentialPlan(
                upserts = essentialUpserts,
                deleteIds = essentialIdsToDelete
            ),
            pinnedFoldersPlan = SyncedPinnedFolderPlan(
                upserts = pinnedFolderUpserts,
                deleteIds = pinnedFolderIdsToDelete
            ),
            pinnedTabsPlan = SyncedPinnedTabPlan(
                upserts = pinnedTabUpserts,
                deleteIds = pinnedTabIdsToDelete
            ),
            outgoingBsos = outgoingBsos,
            nextBaseline = SwiftfoxSyncBaseline(
                schema = 1,
                hasCompletedInitialSync = true,
                items = nextBaselineItems
            ),
            pulledCount = pulledCount,
            pushedCount = pushedCount
        )
    }

    private fun <R> buildRemoteMap(
        decodedById: Map<String, SwiftfoxSyncCodec.DecodedSwiftfoxRecord>,
        kind: String,
        extract: (SwiftfoxSyncCodec.DecodedSwiftfoxRecord) -> Triple<Long, String, R>?
    ): Map<String, RemoteItemWrapper<R>> {
        val out = linkedMapOf<String, RemoteItemWrapper<R>>()
        for ((bsoId, rec) in decodedById) {
            if (rec is SwiftfoxSyncCodec.DecodedSwiftfoxRecord.Tombstone && rec.kind == kind) {
                out[bsoId] = RemoteItemWrapper(
                    bsoId = bsoId,
                    kind = kind,
                    updatedAt = rec.updatedAt,
                    deleted = true,
                    contentHash = "",
                    value = null
                )
            } else {
                val extracted = extract(rec)
                if (extracted != null) {
                    out[bsoId] = RemoteItemWrapper(
                        bsoId = bsoId,
                        kind = kind,
                        updatedAt = extracted.first,
                        deleted = false,
                        contentHash = extracted.second,
                        value = extracted.third
                    )
                }
            }
        }
        return out
    }

    private fun <L, R> mergeCollection(
        kind: String,
        localMap: Map<String, LocalItemWrapper<L>>,
        baseline: SwiftfoxSyncBaseline,
        remoteMap: Map<String, RemoteItemWrapper<R>>,
        unsupportedFutureIds: Set<String>,
        nowMs: Long
    ): List<MergeOutcome<L, R>> {
        val allIds = linkedSetOf<String>()
        allIds.addAll(localMap.keys)
        allIds.addAll(remoteMap.keys)
        for ((id, item) in baseline.items) {
            if (item.kind == kind) {
                allIds.add(id)
            }
        }

        val outcomes = mutableListOf<MergeOutcome<L, R>>()
        for (bsoId in allIds) {
            if (bsoId in unsupportedFutureIds) continue
            val localItem = localMap[bsoId]
            val baseItem = baseline.items[bsoId]
            val remoteItem = remoteMap[bsoId]
            if (localItem == null && remoteItem == null && (baseItem == null || baseItem.deleted)) {
                if (baseItem != null && baseItem.deleted) {
                    outcomes += MergeOutcome(baselineItem = baseItem)
                }
                continue
            }

            val itemKind = localItem?.kind ?: remoteItem?.kind ?: baseItem!!.kind
            val localDeleted: Boolean
            val localChanged: Boolean
            val localUpdatedAt: Long

            if (localItem != null) {
                localDeleted = false
                val expectedLocalHash = baseItem?.localContentHash ?: baseItem?.contentHash
                if (baseItem != null && !baseItem.deleted && localItem.contentHash == expectedLocalHash) {
                    localChanged = false
                    localUpdatedAt = baseItem.updatedAt
                } else if (localItem.isUnmodifiedSeed && !baseline.hasCompletedInitialSync && remoteItem != null) {
                    localChanged = false
                    localUpdatedAt = 0L
                } else {
                    localChanged = true
                    val minNext = (baseItem?.updatedAt ?: 0L) + 1L
                    localUpdatedAt = maxOf(localItem.explicitUpdatedAt ?: nowMs, minNext)
                }
            } else {
                if (baseItem != null && !baseItem.deleted) {
                    localDeleted = true
                    localChanged = true
                    localUpdatedAt = maxOf(nowMs, baseItem.updatedAt + 1L)
                } else {
                    localDeleted = false
                    localChanged = false
                    localUpdatedAt = baseItem?.updatedAt ?: 0L
                }
            }

            when {
                remoteItem == null -> {
                    if (localItem != null) {
                        outcomes += MergeOutcome(
                            outgoingBso = bsoId to localItem.encode(localUpdatedAt),
                            baselineItem = SwiftfoxBaselineItem(
                                bsoId = bsoId,
                                kind = itemKind,
                                contentHash = localItem.contentHash,
                                updatedAt = localUpdatedAt,
                                deleted = false
                            ),
                            pushed = true
                        )
                    } else if (localDeleted) {
                        outcomes += MergeOutcome(
                            baselineItem = SwiftfoxBaselineItem(
                                bsoId = bsoId,
                                kind = itemKind,
                                contentHash = "",
                                updatedAt = localUpdatedAt,
                                deleted = true
                            )
                        )
                    }
                }

                remoteItem.deleted -> {
                    if (localItem != null) {
                        val remoteWins = !localChanged || remoteItem.updatedAt >= localUpdatedAt
                        if (remoteWins && localItem.allowRemoteDelete) {
                            outcomes += MergeOutcome(
                                pullDelete = localItem.value,
                                baselineItem = SwiftfoxBaselineItem(
                                    bsoId = bsoId,
                                    kind = itemKind,
                                    contentHash = "",
                                    updatedAt = remoteItem.updatedAt,
                                    deleted = true
                                ),
                                pulled = true
                            )
                        } else {
                            val pushTs = maxOf(localUpdatedAt, remoteItem.updatedAt + 1L)
                            outcomes += MergeOutcome(
                                outgoingBso = bsoId to localItem.encode(pushTs),
                                baselineItem = SwiftfoxBaselineItem(
                                    bsoId = bsoId,
                                    kind = itemKind,
                                    contentHash = localItem.contentHash,
                                    updatedAt = pushTs,
                                    deleted = false
                                ),
                                pushed = true
                            )
                        }
                    } else {
                        outcomes += MergeOutcome(
                            baselineItem = SwiftfoxBaselineItem(
                                bsoId = bsoId,
                                kind = itemKind,
                                contentHash = "",
                                updatedAt = maxOf(remoteItem.updatedAt, localUpdatedAt),
                                deleted = true
                            )
                        )
                    }
                }

                else -> {
                    // Remote exists and is NOT deleted
                    val remoteValue = remoteItem.value ?: continue
                    if (!localChanged) {
                        val changedOnServer = baseItem == null ||
                            baseItem.deleted ||
                            remoteItem.contentHash != baseItem.contentHash ||
                            localItem == null
                        if (changedOnServer) {
                            val differsFromLocal = localItem == null || localItem.contentHash != remoteItem.contentHash
                            outcomes += MergeOutcome(
                                pullUpsert = if (differsFromLocal) remoteValue else null,
                                baselineItem = SwiftfoxBaselineItem(
                                    bsoId = bsoId,
                                    kind = itemKind,
                                    contentHash = remoteItem.contentHash,
                                    updatedAt = remoteItem.updatedAt,
                                    deleted = false
                                ),
                                pulled = differsFromLocal
                            )
                        } else {
                            outcomes += MergeOutcome(
                                baselineItem = SwiftfoxBaselineItem(
                                    bsoId = bsoId,
                                    kind = itemKind,
                                    contentHash = remoteItem.contentHash,
                                    updatedAt = remoteItem.updatedAt,
                                    deleted = false,
                                    localContentHash = baseItem.localContentHash
                                )
                            )
                        }
                    } else {
                        // Local changed (edited or deleted)
                        val remoteChangedSinceBaseline = baseItem == null ||
                            remoteItem.updatedAt > baseItem.updatedAt
                        val remoteWins = remoteChangedSinceBaseline && remoteItem.updatedAt >= localUpdatedAt
                        if (remoteWins) {
                            val differsFromLocal = localItem == null || localItem.contentHash != remoteItem.contentHash
                            outcomes += MergeOutcome(
                                pullUpsert = if (differsFromLocal) remoteValue else null,
                                baselineItem = SwiftfoxBaselineItem(
                                    bsoId = bsoId,
                                    kind = itemKind,
                                    contentHash = remoteItem.contentHash,
                                    updatedAt = remoteItem.updatedAt,
                                    deleted = false
                                ),
                                pulled = differsFromLocal
                            )
                        } else {
                            val pushTs = maxOf(localUpdatedAt, remoteItem.updatedAt + 1L)
                            if (localDeleted) {
                                outcomes += MergeOutcome(
                                    outgoingBso = bsoId to SwiftfoxSyncCodec.encodeTombstone(bsoId, itemKind, pushTs),
                                    baselineItem = SwiftfoxBaselineItem(
                                        bsoId = bsoId,
                                        kind = itemKind,
                                        contentHash = "",
                                        updatedAt = pushTs,
                                        deleted = true
                                    ),
                                    pushed = true
                                )
                            } else if (localItem != null) {
                                outcomes += MergeOutcome(
                                    outgoingBso = bsoId to localItem.encode(pushTs),
                                    baselineItem = SwiftfoxBaselineItem(
                                        bsoId = bsoId,
                                        kind = itemKind,
                                        contentHash = localItem.contentHash,
                                        updatedAt = pushTs,
                                        deleted = false
                                    ),
                                    pushed = true
                                )
                            }
                        }
                    }
                }
            }
        }
        return outcomes
    }
}
