package app.overbay.swiftfox.ui.browser

import app.overbay.swiftfox.data.db.BookmarkEntity
import app.overbay.swiftfox.data.db.HistoryEntity
import app.overbay.swiftfox.data.db.SwiftfoxDatabase
import app.overbay.swiftfox.features.omnibox.InlineUrlAutocomplete
import app.overbay.swiftfox.features.omnibox.OmniboxCandidateBuilder
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.features.omnibox.OmniboxScopedSuggestions
import app.overbay.swiftfox.features.omnibox.OmniboxSearchScope
import app.overbay.swiftfox.features.omnibox.OmniboxSuggestion
import app.overbay.swiftfox.features.omnibox.SuggestionKind
import app.overbay.swiftfox.features.omnibox.TopDomainsRepository
import app.overbay.swiftfox.features.sync.MozillaSyncCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal interface OmniboxSuggestionBackend {
    suspend fun searchBookmarksByPrefix(rawPrefix: String, limit: Int): List<BookmarkEntity>
    suspend fun searchBookmarks(query: String, limit: Int): List<BookmarkEntity>
    suspend fun searchHistoryByPrefix(rawPrefix: String, limit: Int): List<HistoryEntity>
    suspend fun searchHistory(query: String, limit: Int): List<HistoryEntity>
    suspend fun querySyncedHistory(query: String, limit: Int): List<MozillaSyncCoordinator.PlacesOmniboxMatch>
}

data class OmniboxUiState(
    val query: String = "",
    val searchScope: OmniboxSearchScope? = null,
    val suggestions: List<OmniboxSuggestion> = emptyList(),
    val suggestionsQuery: String = "",
    val suggestionsScope: OmniboxSearchScope? = null,
    val inlineAutocomplete: InlineUrlAutocomplete? = null,
    val focusVersion: Long = 0L,
    val externalQueryVersion: Long = 0L
)

/**
 * Modular state holder isolating high-frequency keystroke, inline autocomplete, SQLite range-index,
 * Places history, and remote search-suggestion updates away from [BrowserUiState] so typing in the
 * Omnibox recomposes only [OmniboxOverlay].
 */
internal class OmniboxStateHolder(
    private val scope: CoroutineScope,
    private val backend: OmniboxSuggestionBackend,
    private val topDomainsRepository: TopDomainsRepository,
    private val browserStateProvider: () -> BrowserUiState
) {
    constructor(
        scope: CoroutineScope,
        db: SwiftfoxDatabase,
        topDomainsRepository: TopDomainsRepository,
        syncCoordinator: MozillaSyncCoordinator,
        browserStateProvider: () -> BrowserUiState
    ) : this(
        scope = scope,
        backend = object : OmniboxSuggestionBackend {
            override suspend fun searchBookmarksByPrefix(rawPrefix: String, limit: Int) =
                db.bookmarkDao().searchByRawUrlPrefix(rawPrefix, limit = limit)

            override suspend fun searchBookmarks(query: String, limit: Int) =
                db.bookmarkDao().search(query, limit = limit)

            override suspend fun searchHistoryByPrefix(rawPrefix: String, limit: Int) =
                db.historyDao().searchByRawUrlPrefix(rawPrefix, limit = limit)

            override suspend fun searchHistory(query: String, limit: Int) =
                db.historyDao().search(query, limit = limit)

            override suspend fun querySyncedHistory(query: String, limit: Int) =
                syncCoordinator.querySyncedHistoryForOmnibox(query, limit = limit)
        },
        topDomainsRepository = topDomainsRepository,
        browserStateProvider = browserStateProvider
    )
    private val _state = MutableStateFlow(OmniboxUiState())
    val state: StateFlow<OmniboxUiState> = _state.asStateFlow()

    private var suggestionJob: Job? = null
    private var localSubmitJob: Job? = null
    private var pendingLocalSubmitQuery: String? = null
    private var nextFocusVersion = 0L
    private var nextExternalQueryVersion = 0L

    fun reset() {
        suggestionJob?.cancel()
        suggestionJob = null
        cancelPendingLocalSubmit()
        if (_state.value != OmniboxUiState()) {
            _state.value = OmniboxUiState()
        }
    }

    fun cancelJobsAndClearSuggestions() {
        suggestionJob?.cancel()
        suggestionJob = null
        cancelPendingLocalSubmit()
        _state.update {
            it.copy(
                searchScope = null,
                suggestions = emptyList(),
                suggestionsQuery = "",
                suggestionsScope = null,
                inlineAutocomplete = null
            )
        }
    }

    fun cancelPendingLocalSubmit() {
        localSubmitJob?.cancel()
        localSubmitJob = null
        pendingLocalSubmitQuery = null
    }

    fun onFocused(initialText: String, effectiveScope: OmniboxSearchScope?) {
        cancelPendingLocalSubmit()
        val focusVer = ++nextFocusVersion
        _state.update {
            it.copy(
                query = initialText,
                searchScope = effectiveScope,
                focusVersion = focusVer
            )
        }
        onQueryChanged(initialText)
    }

    /**
     * Replaces the omnibox query from an external UI action (such as tapping a bang-hint
     * suggestion row) and increments [OmniboxUiState.externalQueryVersion] so the text field syncs
     * only explicit external edits rather than echoing user keystrokes.
     */
    fun applyExternalQuery(query: String) {
        onQueryChanged(query = query, externalQueryVersion = ++nextExternalQueryVersion)
    }

    fun setSearchScope(effectiveScope: OmniboxSearchScope?) {
        cancelPendingLocalSubmit()
        _state.update {
            it.copy(
                searchScope = effectiveScope,
                inlineAutocomplete = if (effectiveScope != null) null else it.inlineAutocomplete
            )
        }
        onQueryChanged(_state.value.query)
    }

    fun submit(
        rawInput: String,
        isOmniboxStillFocused: () -> Boolean,
        onSelectTab: (String) -> Unit,
        onNavigateUrl: (String) -> Unit
    ) {
        cancelPendingLocalSubmit()
        val browserState = browserStateProvider()
        val omni = _state.value
        val activeScope = omni.searchScope
        if (activeScope != null && activeScope.isLocalFilter) {
            if (omni.suggestionsQuery == rawInput && omni.suggestionsScope == activeScope) {
                omni.suggestions.firstOrNull()?.let { openSuggestion(it, onSelectTab, onNavigateUrl) }
                return
            }
            pendingLocalSubmitQuery = rawInput
            localSubmitJob = scope.launch {
                val matches = resolveLocalScopeSuggestions(activeScope, rawInput, browserStateProvider())
                val latestOmni = _state.value
                if (!isOmniboxStillFocused() ||
                    latestOmni.searchScope != activeScope ||
                    pendingLocalSubmitQuery != rawInput
                ) {
                    return@launch
                }
                pendingLocalSubmitQuery = null
                _state.update {
                    it.copy(
                        suggestions = matches,
                        suggestionsQuery = rawInput,
                        suggestionsScope = activeScope,
                        inlineAutocomplete = null
                    )
                }
                matches.firstOrNull()?.let { openSuggestion(it, onSelectTab, onNavigateUrl) }
            }
            return
        }
        val targetUrl = OmniboxEngine.resolveInput(rawInput, browserState.settings, searchScope = activeScope)
        onNavigateUrl(targetUrl)
    }

    private fun openSuggestion(
        suggestion: OmniboxSuggestion,
        onSelectTab: (String) -> Unit,
        onNavigateUrl: (String) -> Unit
    ) {
        if (suggestion.tabId != null) {
            onSelectTab(suggestion.tabId)
        } else {
            onNavigateUrl(suggestion.targetUrl)
        }
    }

    private suspend fun resolveLocalScopeSuggestions(
        searchScope: OmniboxSearchScope,
        query: String,
        browserState: BrowserUiState
    ): List<OmniboxSuggestion> = OmniboxScopedSuggestions.buildLocalScopeSuggestions(
        scope = searchScope,
        query = query,
        state = browserState,
        searchBookmarksByPrefix = backend::searchBookmarksByPrefix,
        searchBookmarks = backend::searchBookmarks,
        searchHistoryByPrefix = backend::searchHistoryByPrefix,
        searchHistory = backend::searchHistory,
        searchSyncedHistory = backend::querySyncedHistory
    )

    fun onQueryChanged(query: String) {
        onQueryChanged(query = query, externalQueryVersion = null)
    }

    private fun onQueryChanged(query: String, externalQueryVersion: Long?) {
        if (pendingLocalSubmitQuery != null && query != pendingLocalSubmitQuery) {
            cancelPendingLocalSubmit()
        }
        val browserSnapshot = browserStateProvider()
        val activeScope = _state.value.searchScope
        // Synchronous Main-thread pass stays strictly bounded across all collections;
        // binary search over TopDomainsRepository is O(log n).
        val instantAutocomplete = if (activeScope == null) {
            val instantCandidates = OmniboxCandidateBuilder.buildInMemoryCandidates(
                state = browserSnapshot,
                maxBookmarksAndHistory = 80
            ) + topDomainsRepository.findPrefixCandidates(query)
            OmniboxEngine.findInlineAutocomplete(
                rawQuery = query,
                candidates = instantCandidates
            )
        } else {
            null
        }
        _state.update {
            it.copy(
                query = query,
                inlineAutocomplete = instantAutocomplete,
                externalQueryVersion = externalQueryVersion ?: it.externalQueryVersion
            )
        }
        suggestionJob?.cancel()
        suggestionJob = scope.launch {
            val trimmed = query.trim()
            val currentBrowserState = browserStateProvider()
            val inPrivate = currentBrowserState.isPrivateView || currentBrowserState.activeTab?.isPrivate == true
            val results = mutableListOf<OmniboxSuggestion>()

            when (activeScope) {
                OmniboxSearchScope.Bookmarks,
                OmniboxSearchScope.Tabs,
                OmniboxSearchScope.History -> {
                    if (trimmed.isNotEmpty() && activeScope != OmniboxSearchScope.Tabs) {
                        delay(LOCAL_DB_DEBOUNCE_MS)
                    }
                    val scopedResults = resolveLocalScopeSuggestions(activeScope, query, browserStateProvider())
                    _state.update { s ->
                        if (s.query != query || s.searchScope != activeScope) s
                        else s.copy(
                            suggestions = scopedResults,
                            suggestionsQuery = query,
                            suggestionsScope = activeScope,
                            inlineAutocomplete = null
                        )
                    }
                    return@launch
                }

                is OmniboxSearchScope.Engine -> {
                    if (trimmed.isEmpty()) {
                        _state.update { s ->
                            if (s.query != query || s.searchScope != activeScope) s
                            else s.copy(
                                suggestions = emptyList(),
                                suggestionsQuery = query,
                                suggestionsScope = activeScope,
                                inlineAutocomplete = null
                            )
                        }
                        return@launch
                    }
                    val currentSettings = browserStateProvider().settings
                    val effectiveSettings = activeScope.effectiveSettings(currentSettings)
                    results += OmniboxScopedSuggestions.buildEngineScopePrimarySuggestion(
                        trimmed = trimmed,
                        settings = currentSettings,
                        scope = activeScope
                    )
                    _state.update { s ->
                        if (s.query != query || s.searchScope != activeScope) s
                        else s.copy(
                            suggestions = results,
                            suggestionsQuery = query,
                            suggestionsScope = activeScope,
                            inlineAutocomplete = null
                        )
                    }
                    if (inPrivate) return@launch
                    delay(180)
                    val liveResult = OmniboxEngine.fetchLiveSuggestionsResult(
                        query = trimmed,
                        settings = effectiveSettings,
                        engineOverride = activeScope.engine
                    )
                    if (liveResult.searchQueries.isNotEmpty()) {
                        val merged = OmniboxCandidateBuilder.mergeLiveSuggestions(
                            existingSuggestions = results,
                            liveResult = liveResult,
                            settings = effectiveSettings,
                            allowNavPromotion = false
                        )
                        _state.update { s ->
                            if (s.query != query || s.searchScope != activeScope) s
                            else s.copy(
                                suggestions = merged,
                                suggestionsQuery = query,
                                suggestionsScope = activeScope,
                                inlineAutocomplete = null
                            )
                        }
                    }
                    return@launch
                }

                null -> Unit
            }

            if (trimmed.isEmpty()) {
                val bState = browserStateProvider()
                bState.bookmarks.take(3).forEach { bm ->
                    results += OmniboxSuggestion(
                        id = "bm_${bm.id}",
                        title = bm.title,
                        subtitle = OmniboxEngine.extractHost(bm.url),
                        targetUrl = bm.url,
                        kind = SuggestionKind.BOOKMARK
                    )
                }
                if (!inPrivate) {
                    bState.history.take(4).forEach { h ->
                        if (results.none { it.targetUrl == h.url }) {
                            results += OmniboxSuggestion(
                                id = "hist_${h.url}",
                                title = h.title,
                                subtitle = OmniboxEngine.extractHost(h.url),
                                targetUrl = h.url,
                                kind = SuggestionKind.HISTORY
                            )
                        }
                    }
                }
                _state.update { s ->
                    if (s.query != query || s.searchScope != null) s
                    else s.copy(
                        suggestions = results,
                        suggestionsQuery = query,
                        suggestionsScope = null,
                        inlineAutocomplete = null
                    )
                }
                return@launch
            }

            // Coalesce rapid typing bursts before launching SQLite + Places queries
            delay(LOCAL_DB_DEBOUNCE_MS)
            topDomainsRepository.ensureLoaded()

            val postDebounceBrowser = browserStateProvider()
            val bang = OmniboxEngine.detectBang(trimmed)
            if (bang != null) {
                results += OmniboxScopedSuggestions.buildBangSuggestion(
                    bang = bang,
                    rawTrimmed = trimmed,
                    settings = postDebounceBrowser.settings
                )
            } else if (trimmed.startsWith("!") || trimmed.startsWith("@")) {
                val lowerTrimmed = trimmed.lowercase()
                OmniboxEngine.bangs
                    .filter { b -> b.triggers.any { it.startsWith(lowerTrimmed) } }
                    .take(4)
                    .forEach { b ->
                        results += OmniboxSuggestion(
                            id = "bang_hint_${b.name}",
                            title = "${b.triggers.first()}  ${b.name}",
                            subtitle = "Type space + query",
                            targetUrl = b.triggers.first() + " ",
                            kind = SuggestionKind.BANG
                        )
                    }
            }

            results += OmniboxScopedSuggestions.filterMatchingTabSuggestions(
                tabs = postDebounceBrowser.tabs,
                trimmedQuery = trimmed,
                inPrivate = inPrivate,
                limit = 2,
                sortByRecency = false
            )

            val rawHostPrefix = OmniboxEngine.extractRawHostPrefixForDbQuery(query)
            val dbPrefixBookmarks = if (rawHostPrefix != null) {
                backend.searchBookmarksByPrefix(rawHostPrefix, limit = 12)
            } else {
                emptyList()
            }
            val dbSearchBookmarks = backend.searchBookmarks(trimmed, limit = 12)
            val combinedBookmarks = (dbPrefixBookmarks + dbSearchBookmarks).distinctBy { it.id }
            combinedBookmarks.take(3).forEach { bm ->
                if (results.none { it.targetUrl.trimEnd('/').equals(bm.url.trimEnd('/'), ignoreCase = true) }) {
                    results += OmniboxSuggestion(
                        id = "bm_${bm.id}",
                        title = bm.title,
                        subtitle = OmniboxEngine.extractHost(bm.url),
                        targetUrl = bm.url,
                        kind = SuggestionKind.BOOKMARK
                    )
                }
            }

            val dbPrefixHistory = if (!inPrivate && rawHostPrefix != null) {
                backend.searchHistoryByPrefix(rawHostPrefix, limit = 20)
            } else {
                emptyList()
            }
            val dbSearchHistory = if (!inPrivate) {
                backend.searchHistory(trimmed, limit = 16)
            } else {
                emptyList()
            }
            val combinedHistory = (dbPrefixHistory + dbSearchHistory).distinctBy { it.url }
            var historyRowsAdded = 0
            combinedHistory.forEach { h ->
                if (historyRowsAdded < 4 &&
                    results.none { it.targetUrl.trimEnd('/').equals(h.url.trimEnd('/'), ignoreCase = true) }
                ) {
                    results += OmniboxSuggestion(
                        id = "hist_${h.url}",
                        title = h.title,
                        subtitle = OmniboxEngine.extractHost(h.url),
                        targetUrl = h.url,
                        kind = SuggestionKind.HISTORY
                    )
                    historyRowsAdded++
                }
            }

            val placesMatches = if (!inPrivate && bang == null && postDebounceBrowser.settings.syncHistoryEnabled) {
                backend.querySyncedHistory(trimmed, limit = 10)
            } else {
                emptyList()
            }
            placesMatches.forEach { hit ->
                if (historyRowsAdded < 5 &&
                    results.none { it.targetUrl.trimEnd('/').equals(hit.url.trimEnd('/'), ignoreCase = true) }
                ) {
                    val host = OmniboxEngine.extractHost(hit.url).ifBlank { hit.url }
                    results += OmniboxSuggestion(
                        id = "places_${hit.url}",
                        title = hit.title.ifBlank { host },
                        subtitle = host,
                        targetUrl = hit.url,
                        kind = SuggestionKind.HISTORY
                    )
                    historyRowsAdded++
                }
            }

            val stateSnapshot = browserStateProvider()
            val (allAutocompleteCandidates, refinedAutocomplete) = withContext(Dispatchers.Default) {
                val allCandidates = buildList {
                    addAll(OmniboxCandidateBuilder.buildInMemoryCandidates(stateSnapshot))
                    addAll(OmniboxCandidateBuilder.fromBookmarks(combinedBookmarks))
                    addAll(OmniboxCandidateBuilder.fromHistory(combinedHistory))
                    addAll(OmniboxCandidateBuilder.fromPlacesMatches(placesMatches))
                    addAll(topDomainsRepository.findPrefixCandidates(query))
                }
                val best = OmniboxEngine.findInlineAutocomplete(
                    rawQuery = query,
                    candidates = allCandidates
                )
                allCandidates to best
            }

            val currentSettings = stateSnapshot.settings
            val resolvedDirect = OmniboxEngine.resolveInput(trimmed, currentSettings)
            val looksDirect = OmniboxEngine.looksLikeUrl(trimmed)
            if (looksDirect) {
                results.add(
                    0,
                    OmniboxSuggestion(
                        id = "direct_$trimmed",
                        title = trimmed,
                        subtitle = "Go to site",
                        targetUrl = resolvedDirect,
                        kind = SuggestionKind.DIRECT_URL
                    )
                )
            } else if (bang == null) {
                if (refinedAutocomplete != null &&
                    results.none {
                        it.targetUrl.trimEnd('/').equals(refinedAutocomplete.targetUrl.trimEnd('/'), ignoreCase = true)
                    }
                ) {
                    results.add(
                        0,
                        OmniboxSuggestion(
                            id = "autocomplete_${refinedAutocomplete.targetUrl}",
                            title = refinedAutocomplete.displayText,
                            subtitle = "Go to suggested site",
                            targetUrl = refinedAutocomplete.targetUrl,
                            kind = SuggestionKind.DIRECT_URL
                        )
                    )
                }
                results += OmniboxSuggestion(
                    id = "search_self_$trimmed",
                    title = trimmed,
                    subtitle = "Search with ${currentSettings.searchEngine.displayName(currentSettings.customSearchUrlTemplate)}",
                    targetUrl = resolvedDirect,
                    kind = SuggestionKind.SEARCH
                )
            }

            _state.update { s ->
                if (s.query != query || s.searchScope != null) s
                else s.copy(
                    suggestions = results,
                    suggestionsQuery = query,
                    suggestionsScope = null,
                    inlineAutocomplete = refinedAutocomplete
                )
            }

            if (inPrivate) return@launch

            delay(145)
            val latestSettings = browserStateProvider().settings
            val liveResult = OmniboxEngine.fetchLiveSuggestionsResult(trimmed, latestSettings)
            if (liveResult.navigationalHits.isNotEmpty() || liveResult.searchQueries.isNotEmpty()) {
                val allowNav = !looksDirect && bang == null
                val merged = OmniboxCandidateBuilder.mergeLiveSuggestions(
                    existingSuggestions = results,
                    liveResult = liveResult,
                    settings = latestSettings,
                    allowNavPromotion = allowNav
                )
                val updatedAutocomplete = if (allowNav && liveResult.navigationalHits.isNotEmpty()) {
                    withContext(Dispatchers.Default) {
                        val navCandidates = OmniboxCandidateBuilder.fromNavigationalSuggestions(
                            liveResult.navigationalHits
                        )
                        OmniboxEngine.findInlineAutocomplete(
                            rawQuery = query,
                            candidates = allAutocompleteCandidates + navCandidates
                        )
                    }
                } else {
                    refinedAutocomplete
                }
                _state.update { s ->
                    if (s.query != query || s.searchScope != null) s
                    else s.copy(
                        suggestions = merged,
                        suggestionsQuery = query,
                        suggestionsScope = null,
                        inlineAutocomplete = updatedAutocomplete
                    )
                }
            }
        }
    }

    companion object {
        private const val LOCAL_DB_DEBOUNCE_MS = 35L
    }
}
