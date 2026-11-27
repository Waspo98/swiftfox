package app.overbay.swiftfox

import app.overbay.swiftfox.data.db.BookmarkEntity
import app.overbay.swiftfox.data.db.HistoryEntity
import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.features.omnibox.TopDomainsRepository
import app.overbay.swiftfox.features.sync.MozillaSyncCoordinator
import app.overbay.swiftfox.ui.browser.BrowserUiState
import app.overbay.swiftfox.ui.browser.OmniboxStateHolder
import app.overbay.swiftfox.ui.browser.OmniboxSuggestionBackend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OmniboxStateHolderTest {

    private fun fakeBackend(
        bookmarks: List<BookmarkEntity> = emptyList(),
        history: List<HistoryEntity> = emptyList()
    ): OmniboxSuggestionBackend = object : OmniboxSuggestionBackend {
        override suspend fun searchBookmarksByPrefix(rawPrefix: String, limit: Int): List<BookmarkEntity> =
            bookmarks.filter { it.url.contains(rawPrefix, ignoreCase = true) }.take(limit)

        override suspend fun searchBookmarks(query: String, limit: Int): List<BookmarkEntity> =
            bookmarks.filter {
                it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true)
            }.take(limit)

        override suspend fun searchHistoryByPrefix(rawPrefix: String, limit: Int): List<HistoryEntity> =
            history.filter { it.url.contains(rawPrefix, ignoreCase = true) }.take(limit)

        override suspend fun searchHistory(query: String, limit: Int): List<HistoryEntity> =
            history.filter {
                it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true)
            }.take(limit)

        override suspend fun querySyncedHistory(
            query: String,
            limit: Int
        ): List<MozillaSyncCoordinator.PlacesOmniboxMatch> = emptyList()
    }

    @Test
    fun rapidTypingUpdatesQueryAndAutocompleteWithoutBumpingExternalSyncVersions() = runTest {
        val topDomains = TopDomainsRepository.fromLines(sequenceOf("youtube.com", "github.com"))
        val holder = OmniboxStateHolder(
            scope = backgroundScope,
            backend = fakeBackend(),
            topDomainsRepository = topDomains,
            browserStateProvider = { BrowserUiState(settings = BrowserSettings(searchSuggestionsEnabled = false)) }
        )

        holder.onFocused(initialText = "", effectiveScope = null)
        val initialFocusVersion = holder.state.value.focusVersion
        val initialExternalVersion = holder.state.value.externalQueryVersion
        assertEquals(1L, initialFocusVersion)
        assertEquals(0L, initialExternalVersion)

        // Rapid character-by-character typing burst must never trigger external text-field overwrites
        listOf("y", "yo", "you", "yout", "youtu", "youtub", "youtube").forEach { typed ->
            holder.onQueryChanged(typed)
            val snap = holder.state.value
            assertEquals(typed, snap.query)
            assertEquals(initialFocusVersion, snap.focusVersion)
            assertEquals(initialExternalVersion, snap.externalQueryVersion)
        }

        val instant = holder.state.value.inlineAutocomplete
        assertNotNull(instant)
        assertEquals(".com", instant?.completionSuffix)
        assertEquals("https://youtube.com/", instant?.targetUrl)

        advanceTimeBy(100)
        advanceUntilIdle()
        assertEquals(initialFocusVersion, holder.state.value.focusVersion)
        assertEquals(initialExternalVersion, holder.state.value.externalQueryVersion)
    }

    @Test
    fun applyExternalQueryAndReFocusIncrementMonotonicVersionsEvenAcrossReset() = runTest {
        val topDomains = TopDomainsRepository.fromLines(sequenceOf("github.com"))
        val holder = OmniboxStateHolder(
            scope = backgroundScope,
            backend = fakeBackend(),
            topDomainsRepository = topDomains,
            browserStateProvider = { BrowserUiState(settings = BrowserSettings(searchSuggestionsEnabled = false)) }
        )

        holder.onFocused(initialText = "https://example.com/", effectiveScope = null)
        assertEquals(1L, holder.state.value.focusVersion)
        assertEquals(0L, holder.state.value.externalQueryVersion)

        holder.onQueryChanged("!y")
        assertEquals(1L, holder.state.value.focusVersion)
        assertEquals(0L, holder.state.value.externalQueryVersion)

        val observed = mutableListOf<Pair<String, Long>>()
        val collectorJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            holder.state.collect { observed += it.query to it.externalQueryVersion }
        }

        // Tapping a bang suggestion atomically updates query and externalQueryVersion together
        holder.applyExternalQuery("!yt ")
        collectorJob.cancel()
        assertEquals("!yt ", holder.state.value.query)
        assertEquals(1L, holder.state.value.focusVersion)
        assertEquals(1L, holder.state.value.externalQueryVersion)
        assertTrue(observed.none { (q, ver) -> ver == 1L && q != "!yt " })
        assertTrue(observed.any { (q, ver) -> ver == 1L && q == "!yt " })

        // Resetting and reopening keeps focus/external versions strictly monotonic
        holder.reset()
        holder.onFocused(initialText = "", effectiveScope = null)
        assertTrue(holder.state.value.focusVersion > 1L)
        holder.applyExternalQuery("!gh ")
        assertTrue(holder.state.value.externalQueryVersion > 1L)
    }
}
