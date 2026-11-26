package app.overbay.swiftfox.features.sync

import android.content.Context
import androidx.room.withTransaction
import app.overbay.swiftfox.data.db.SwiftfoxDatabase
import app.overbay.swiftfox.data.prefs.PreferencesRepository
import app.overbay.swiftfox.features.adblock.FilterListRepository
import app.overbay.swiftfox.features.userscripts.UserscriptRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

sealed class SwiftfoxSyncResult {
    data class Success(
        val pulledCount: Int,
        val pushedCount: Int,
        val skippedOversizedCount: Int = 0,
        val localDataChanged: Boolean = false
    ) : SwiftfoxSyncResult()

    data class KeysNotInitialized(val message: String) : SwiftfoxSyncResult()
}

internal interface SwiftfoxSyncLocalStore {
    fun loadBaseline(): SwiftfoxSyncBaseline
    fun saveBaselineAtomic(baseline: SwiftfoxSyncBaseline)
    fun clearBaseline()
    suspend fun readLocalSnapshot(): SwiftfoxSyncReconciler.LocalSnapshot
    suspend fun applyPlanLocally(plan: SwiftfoxSyncReconciler.SwiftfoxSyncPlan): Boolean
    suspend fun applyPlanLocallyWithBaseline(
        plan: SwiftfoxSyncReconciler.SwiftfoxSyncPlan
    ): Pair<Boolean, SwiftfoxSyncBaseline> = applyPlanLocally(plan) to plan.nextBaseline
}

/**
 * Sync 1.5 client for Swiftfox-only configuration and local workspace data (`swiftfox` collection).
 * Runs against the shared [Sync15Transport] so Token Server credentials and `crypto/keys` are reused
 * without touching Firefox or Zen Browser collections.
 */
internal class Sync15SwiftfoxClient internal constructor(
    private val localStore: SwiftfoxSyncLocalStore,
    private val transport: Sync15Transport
) {
    constructor(
        context: Context,
        db: SwiftfoxDatabase,
        prefs: PreferencesRepository,
        filterListRepository: FilterListRepository?,
        userscriptRepository: UserscriptRepository?,
        transport: Sync15Transport
    ) : this(
        localStore = RoomSwiftfoxSyncLocalStore(
            context = context,
            db = db,
            prefs = prefs,
            filterListRepository = filterListRepository,
            userscriptRepository = userscriptRepository,
            json = transport.json
        ),
        transport = transport
    )

    fun clearBaseline() {
        localStore.clearBaseline()
    }

    suspend fun sync(
        kid: String,
        fxaAccessToken: String,
        syncKeyBase64: String,
        tokenServerUrl: String
    ): SwiftfoxSyncResult = withContext(Dispatchers.IO) {
        val cacheKey = Sync15Transport.AuthCacheKey(kid, fxaAccessToken, syncKeyBase64, tokenServerUrl)
        val (session, cachedKeyRing) = transport.getOrFetchSession(cacheKey)
        val keyRing = transport.getOrFetchCollectionKeyRing(cacheKey, session, cachedKeyRing)
            ?: return@withContext SwiftfoxSyncResult.KeysNotInitialized(
                "Enable Bookmarks, History, or Tabs once to initialize Sync encryption keys on this Mozilla account"
            )
        val metaPayload = transport.fetchMetaGlobalPayload(session)
            ?: return@withContext SwiftfoxSyncResult.KeysNotInitialized(
                "Enable Bookmarks, History, or Tabs once to initialize Mozilla Sync metadata"
            )
        check(SwiftfoxSyncCodec.metaGlobalAllowsSwiftfoxWrite(metaPayload, transport.json)) {
            "Swiftfox sync blocked by incompatible or declined meta/global record"
        }

        val keys = keyRing.keysFor(SwiftfoxSyncCodec.SWIFTFOX_COLLECTION)
        val limits = transport.fetchInfoConfiguration(session)
        val collections = transport.fetchInfoCollections(session)

        for (attempt in 1..2) {
            val (decrypted, lastModified) = if (
                collections.containsKey(SwiftfoxSyncCodec.SWIFTFOX_COLLECTION) || attempt > 1
            ) {
                val (rawRecords, lm) = transport.fetchCollectionWithLastModified(
                    session = session,
                    collection = SwiftfoxSyncCodec.SWIFTFOX_COLLECTION,
                    limit = 2500
                )
                val sorted = rawRecords.sortedBy {
                    (it["modified"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
                }
                val decryptResult = transport.decryptBsoList(sorted, keys)
                if (decryptResult.failedCount > 0) {
                    throw SyncCryptoException(
                        "Could not decrypt ${decryptResult.failedCount} Swiftfox sync records (possible Sync key mismatch)"
                    )
                }
                decryptResult.records to (lm ?: "0")
            } else {
                // Guard brand-new collection creation with X-If-Unmodified-Since: 0 so a concurrent
                // client creating `swiftfox` first triggers a 412 retry & merge instead of clobbering.
                emptyList<Pair<String, kotlinx.serialization.json.JsonObject>>() to "0"
            }

            val baseline = localStore.loadBaseline()
            val localSnapshot = localStore.readLocalSnapshot()
            val nowMs = System.currentTimeMillis()
            val plan = SwiftfoxSyncReconciler.buildPlan(
                local = localSnapshot,
                baseline = baseline,
                remoteRawRecords = decrypted,
                nowMs = nowMs
            )

            var skippedOversizedIds = emptySet<String>()
            if (plan.outgoingBsos.isNotEmpty()) {
                when (
                    val postResult = transport.postEncryptedBsosChunked(
                        session = session,
                        collection = SwiftfoxSyncCodec.SWIFTFOX_COLLECTION,
                        keys = keys,
                        records = plan.outgoingBsos,
                        initialIfUnmodifiedSince = lastModified,
                        limits = limits
                    )
                ) {
                    is Sync15Transport.PostChunkResult.Success -> {
                        skippedOversizedIds = postResult.skippedOversizedIds.toSet()
                    }
                    Sync15Transport.PostChunkResult.Conflict -> {
                        if (attempt < 2) continue
                        throw IOException("Sync conflict while updating Swiftfox client data (412)")
                    }
                }
            }

            val (localDataChanged, baselineAfterApply) = localStore.applyPlanLocallyWithBaseline(plan)
            val finalBaseline = if (skippedOversizedIds.isEmpty()) {
                baselineAfterApply
            } else {
                val adjusted = baselineAfterApply.items.toMutableMap()
                for (skippedId in skippedOversizedIds) {
                    val prev = baseline.items[skippedId]
                    if (prev != null) {
                        adjusted[skippedId] = prev
                    } else {
                        adjusted.remove(skippedId)
                    }
                }
                baselineAfterApply.copy(items = adjusted)
            }
            localStore.saveBaselineAtomic(finalBaseline)

            return@withContext SwiftfoxSyncResult.Success(
                pulledCount = plan.pulledCount,
                pushedCount = (plan.pushedCount - skippedOversizedIds.size).coerceAtLeast(0),
                skippedOversizedCount = skippedOversizedIds.size,
                localDataChanged = localDataChanged
            )
        }
        throw IOException("Sync conflict while updating Swiftfox client data (412)")
    }
}

private class RoomSwiftfoxSyncLocalStore(
    private val context: Context,
    private val db: SwiftfoxDatabase,
    private val prefs: PreferencesRepository,
    private val filterListRepository: FilterListRepository?,
    private val userscriptRepository: UserscriptRepository?,
    private val json: Json
) : SwiftfoxSyncLocalStore {

    private val baselineLock = Any()

    private fun baselineFile(): File =
        File(context.applicationContext.filesDir, BASELINE_FILE_NAME)

    override fun clearBaseline() {
        synchronized(baselineLock) {
            runCatching { baselineFile().delete() }
        }
    }

    override fun loadBaseline(): SwiftfoxSyncBaseline = synchronized(baselineLock) {
        val file = baselineFile()
        if (!file.isFile) return SwiftfoxSyncBaseline()
        return runCatching {
            json.decodeFromString<SwiftfoxSyncBaseline>(file.readText(Charsets.UTF_8))
        }.getOrDefault(SwiftfoxSyncBaseline())
    }

    override fun saveBaselineAtomic(baseline: SwiftfoxSyncBaseline) {
        synchronized(baselineLock) {
            val target = baselineFile()
            val parent = target.parentFile ?: return
            parent.mkdirs()
            val payload = json.encodeToString(baseline)
            var tmp: File? = null
            try {
                tmp = File.createTempFile("${target.name}.", ".tmp", parent)
                tmp.writeText(payload, Charsets.UTF_8)
                val renamed = if (target.exists()) {
                    tmp.renameTo(target) || (target.delete() && tmp.renameTo(target))
                } else {
                    tmp.renameTo(target)
                }
                if (!renamed) {
                    tmp.delete()
                }
            } catch (ce: CancellationException) {
                tmp?.delete()
                throw ce
            } catch (_: Throwable) {
                tmp?.delete()
            }
        }
    }

    override suspend fun readLocalSnapshot(): SwiftfoxSyncReconciler.LocalSnapshot {
        val settings = prefs.settingsFlow.first()
        val siteOverrides = db.siteOverrideDao().getAll()
        val zappedRules = db.zappedRuleDao().getAll()
        val filterLists = db.filterListDao().getAll()
        val userscripts = db.userscriptDao().getAll()
        val allSpaces = db.spaceDao().getAll()
        val allEssentials = db.essentialDao().getAll()
        val zenSpaceIds = allSpaces.asSequence()
            .filter { it.syncGuid != null }
            .mapTo(mutableSetOf()) { it.id }
        val localPinnedTabs = db.tabDao().getNormalTabs().filter {
            !it.isPrivate && it.isPinned && it.essentialId == null && it.zenPinnedId == null
        }
        return SwiftfoxSyncReconciler.LocalSnapshot(
            settings = settings,
            settingsUpdatedAtMs = settings.swiftfoxSettingsUpdatedAtMs,
            siteOverrides = siteOverrides,
            zappedRules = zappedRules,
            filterLists = filterLists,
            userscripts = userscripts,
            localSpaces = allSpaces.filter { it.syncGuid == null },
            zenSpacesCount = zenSpaceIds.size,
            localEssentials = allEssentials.filter { it.syncGuid == null },
            zenEssentialsCount = allEssentials.count { it.syncGuid != null },
            localPinnedTabs = localPinnedTabs,
            zenSpaceIds = zenSpaceIds
        )
    }

    override suspend fun applyPlanLocally(plan: SwiftfoxSyncReconciler.SwiftfoxSyncPlan): Boolean =
        applyPlanLocallyWithBaseline(plan).first

    override suspend fun applyPlanLocallyWithBaseline(
        plan: SwiftfoxSyncReconciler.SwiftfoxSyncPlan
    ): Pair<Boolean, SwiftfoxSyncBaseline> {
        var changed = false
        var adjustedBaseline = plan.nextBaseline

        plan.settingsToApply?.let { (syncedSettings, updatedAtMs) ->
            prefs.applySyncedSettings(syncedSettings, updatedAtMs)
            changed = true
        } ?: plan.settingsTimestampToRecord?.let { ts ->
            prefs.setSwiftfoxSettingsUpdatedAtMs(ts)
        }

        val hasDbMutations = plan.siteOverridesToUpsert.isNotEmpty() ||
            plan.siteOverrideHostsToDelete.isNotEmpty() ||
            plan.zappedRulesToUpsert.isNotEmpty() ||
            plan.zappedRuleIdsToDelete.isNotEmpty() ||
            plan.userscriptsToUpsert.isNotEmpty() ||
            plan.userscriptIdsToDelete.isNotEmpty() ||
            plan.spacesPlan.upserts.isNotEmpty() ||
            plan.spacesPlan.deleteIds.isNotEmpty() ||
            plan.essentialsPlan.upserts.isNotEmpty() ||
            plan.essentialsPlan.deleteIds.isNotEmpty() ||
            plan.pinnedTabsPlan.upserts.isNotEmpty() ||
            plan.pinnedTabsPlan.deleteIds.isNotEmpty()

        if (hasDbMutations) {
            changed = true
            val remappedPinIds = mutableSetOf<String>()
            var postTxLocalPins: List<app.overbay.swiftfox.data.db.TabEntity> = emptyList()
            db.withTransaction {
                for (host in plan.siteOverrideHostsToDelete) {
                    db.siteOverrideDao().deleteByHost(host)
                }
                for (override in plan.siteOverridesToUpsert) {
                    db.siteOverrideDao().upsert(override)
                }

                for (id in plan.zappedRuleIdsToDelete) {
                    db.zappedRuleDao().deleteById(id)
                }
                for (rule in plan.zappedRulesToUpsert) {
                    db.zappedRuleDao().upsert(rule)
                }

                for (id in plan.userscriptIdsToDelete) {
                    db.userscriptDao().deleteById(id)
                    db.userscriptStorageDao().deleteAllForScript(id)
                }
                for (script in plan.userscriptsToUpsert) {
                    db.userscriptDao().upsert(script)
                }

                if (plan.spacesPlan.upserts.isNotEmpty()) {
                    db.spaceDao().upsertAll(plan.spacesPlan.upserts)
                }
                val fallbackSpaceId = plan.spacesPlan.fallbackSpaceId
                    ?: db.spaceDao().getAll().firstOrNull { it.id !in plan.spacesPlan.deleteIds }?.id
                    ?: SwiftfoxDatabase.DEFAULT_SPACE_ID
                for (delSpaceId in plan.spacesPlan.deleteIds) {
                    db.tabDao().reassignSpace(delSpaceId, fallbackSpaceId)
                    db.spaceDao().deleteById(delSpaceId)
                }

                if (plan.essentialsPlan.upserts.isNotEmpty()) {
                    db.essentialDao().upsertAll(plan.essentialsPlan.upserts)
                }
                for (delEssId in plan.essentialsPlan.deleteIds) {
                    db.tabDao().deleteByEssentialId(delEssId)
                    db.essentialDao().deleteById(delEssId)
                }

                for (chunk in plan.pinnedTabsPlan.deleteIds.chunked(400)) {
                    db.tabDao().deleteByIds(chunk)
                }
                if (plan.pinnedTabsPlan.upserts.isNotEmpty()) {
                    val currentSpaceIds = db.spaceDao().getAll().mapTo(mutableSetOf()) { it.id }
                    val normalizedUpserts = plan.pinnedTabsPlan.upserts.map { tab ->
                        if (tab.spaceId in currentSpaceIds) {
                            tab
                        } else {
                            remappedPinIds += tab.id
                            tab.copy(spaceId = fallbackSpaceId)
                        }
                    }
                    db.tabDao().upsertAll(normalizedUpserts)
                }
                if (remappedPinIds.isNotEmpty()) {
                    postTxLocalPins = db.tabDao().getNormalTabs().filter {
                        !it.isPrivate && it.isPinned && it.essentialId == null && it.zenPinnedId == null
                    }
                }
            }
            if (remappedPinIds.isNotEmpty()) {
                val updatedItems = adjustedBaseline.items.toMutableMap()
                postTxLocalPins
                    .groupBy { it.spaceId }
                    .values
                    .forEach { spaceTabs ->
                        spaceTabs.sortedBy { it.sortOrder }.forEachIndexed { idx, tab ->
                            if (tab.id in remappedPinIds) {
                                val bsoId = SwiftfoxSyncCodec.pinnedTabBsoId(tab.id)
                                val base = updatedItems[bsoId]
                                if (base != null && !base.deleted) {
                                    val storedHash = SwiftfoxSyncCodec.pinnedTabContentHash(tab, idx)
                                    updatedItems[bsoId] = if (storedHash == base.contentHash) {
                                        base.copy(localContentHash = null)
                                    } else {
                                        base.copy(localContentHash = storedHash)
                                    }
                                }
                            }
                        }
                    }
                adjustedBaseline = adjustedBaseline.copy(items = updatedItems)
            }
        }

        if (plan.userscriptsToUpsert.isNotEmpty() || plan.userscriptIdsToDelete.isNotEmpty()) {
            userscriptRepository?.invalidateRuntimeCache()
        }

        if (plan.filterListsToUpsert.isNotEmpty() || plan.filterListIdsToDelete.isNotEmpty()) {
            changed = true
            filterListRepository?.applySyncedFilterLists(
                upserts = plan.filterListsToUpsert,
                deleteCustomIds = plan.filterListIdsToDelete
            )
        }

        return changed to adjustedBaseline
    }

    companion object {
        private const val BASELINE_FILE_NAME = "swiftfox_sync_baseline.json"
    }
}
