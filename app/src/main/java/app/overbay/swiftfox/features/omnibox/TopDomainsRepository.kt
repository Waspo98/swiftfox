package app.overbay.swiftfox.features.omnibox

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Offline popularity-ranked corpus of common human-navigable domains (loaded from
 * `assets/omnibox/top_domains.txt`). Used as the lowest-priority inline autocomplete fallback
 * behind the user's own tabs, pins, bookmarks, and history so typing well-known sites like
 * "theverge" completes immediately even on a fresh install or in Private Mode.
 */
class TopDomainsRepository private constructor(
    private val appContext: Context?,
    initialEntries: List<RankedDomain>?
) {
    data class RankedDomain(
        val domain: String,
        val rank: Int
    )

    constructor(context: Context) : this(
        appContext = context.applicationContext,
        initialEntries = null
    )

    private val loadMutex = Mutex()

    @Volatile
    private var sortedByDomain: List<RankedDomain> = initialEntries
        ?.sortedBy { it.domain }
        ?: emptyList()

    @Volatile
    private var isLoaded: Boolean = initialEntries != null

    val size: Int
        get() = sortedByDomain.size

    suspend fun warmUp() {
        ensureLoaded()
    }

    suspend fun ensureLoaded() {
        if (isLoaded) return
        val ctx = appContext ?: return
        loadMutex.withLock {
            if (isLoaded) return
            val parsed = withContext(Dispatchers.IO) {
                try {
                    ctx.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { reader ->
                        parseRankedDomains(reader.lineSequence())
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Throwable) {
                    null
                }
            }
            if (parsed != null) {
                sortedByDomain = parsed.sortedBy { it.domain }
                isLoaded = true
            }
        }
    }

    /**
     * Non-blocking prefix search against the in-memory sorted index. Safe to call on the Main
     * thread (returns empty until [warmUp] completes, then runs in O(log n)).
     */
    fun findPrefixCandidates(
        rawQuery: String,
        limit: Int = 5
    ): List<AutocompleteCandidate> {
        val entries = sortedByDomain
        if (entries.isEmpty() || rawQuery.isEmpty() || limit <= 0) return emptyList()
        if (rawQuery.any { it.isWhitespace() }) return emptyList()
        if (rawQuery.startsWith("!") || rawQuery.startsWith("@")) return emptyList()
        if (rawQuery.contains('?') || rawQuery.contains('#')) return emptyList()

        val lower = rawQuery.lowercase(Locale.ROOT)
        // Never suggest HTTPS top domains when the user explicitly typed http://
        if (lower.startsWith("http://")) return emptyList()
        val afterScheme = if (lower.startsWith("https://")) {
            lower.removePrefix("https://")
        } else {
            lower
        }
        if (afterScheme.isEmpty() || afterScheme.contains('/')) return emptyList()

        val prefix = afterScheme.removePrefix("www.")
        if (prefix.isEmpty()) return emptyList()
        // Keep short 1-2 character searches free from generic top-domain hijacking unless a dot
        // was explicitly typed (e.g. "x.").
        if (prefix.length < MIN_PREFIX_LENGTH && !prefix.contains('.')) return emptyList()

        val startIndex = lowerBound(entries, prefix)
        if (startIndex >= entries.size) return emptyList()

        val matches = ArrayList<RankedDomain>(16)
        var idx = startIndex
        while (idx < entries.size && entries[idx].domain.startsWith(prefix)) {
            val entry = entries[idx]
            // Only return completions that actually extend what was typed
            if (entry.domain.length > prefix.length) {
                matches.add(entry)
            }
            idx++
        }
        if (matches.isEmpty()) return emptyList()

        matches.sortBy { it.rank }
        val capped = if (matches.size > limit) matches.subList(0, limit) else matches
        return capped.map { entry ->
            AutocompleteCandidate(
                url = "https://${entry.domain}/",
                priority = OmniboxEngine.PRIORITY_TOP_DOMAIN,
                visitCount = (MAX_RANK_SCORE - entry.rank).coerceAtLeast(1),
                lastVisitedAt = 0L
            )
        }
    }

    private fun lowerBound(entries: List<RankedDomain>, prefix: String): Int {
        var low = 0
        var high = entries.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (entries[mid].domain < prefix) {
                low = mid + 1
            } else {
                high = mid
            }
        }
        return low
    }

    companion object {
        const val ASSET_PATH = "omnibox/top_domains.txt"
        const val MIN_PREFIX_LENGTH = 3
        private const val MAX_RANK_SCORE = 100_000

        fun fromLines(lines: Sequence<String>): TopDomainsRepository {
            return TopDomainsRepository(
                appContext = null,
                initialEntries = parseRankedDomains(lines)
            )
        }

        internal fun parseRankedDomains(lines: Sequence<String>): List<RankedDomain> {
            val seen = HashSet<String>(1024)
            val result = ArrayList<RankedDomain>(1024)
            var rank = 0
            for (raw in lines) {
                val trimmed = raw.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                val domain = trimmed.lowercase(Locale.ROOT).removePrefix("www.")
                if (domain.contains('/') || !domain.contains('.')) continue
                if (seen.add(domain)) {
                    result.add(RankedDomain(domain = domain, rank = rank++))
                }
            }
            return result
        }
    }
}
