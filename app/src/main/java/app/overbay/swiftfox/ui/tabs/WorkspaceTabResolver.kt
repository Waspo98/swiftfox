package app.overbay.swiftfox.ui.tabs

import app.overbay.swiftfox.data.db.EssentialEntity
import app.overbay.swiftfox.data.db.SpaceEntity
import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.ui.browser.BrowserTabUi
import app.overbay.swiftfox.ui.browser.BrowserUiState

enum class PinnedTabKind {
    ESSENTIAL,
    PINNED_TAB,
    ZEN_PINNED
}

data class PendingPinCloseTarget(
    val tabId: String,
    val title: String,
    val kind: PinnedTabKind
)

/**
 * Pure helper rules for Essentials and Pinned tabs living directly on their workspace tiles/rows.
 */
object WorkspaceTabResolver {
    /**
     * Sentinel spaceId used for global (cross-Space) Essential backing tabs so they keep a consistent
     * WebView profile and survive any individual Space deletion or Space reconciliation.
     */
    const val ESSENTIAL_GLOBAL_SPACE_ID = "__essential_global__"

    fun isContainerSeparatedEssentials(
        settings: BrowserSettings,
        allEssentials: List<EssentialEntity>
    ): Boolean {
        val explicit = settings.syncZenSeparateEssentials
        if (explicit != null) return explicit
        return allEssentials.any {
            !it.zenContainerGuid.isNullOrBlank() && it.zenContainerGuid != "default"
        }
    }

    fun resolveEssentialSpaceId(
        essential: EssentialEntity,
        spaces: List<SpaceEntity>,
        activeSpaceId: String,
        settings: BrowserSettings,
        allEssentials: List<EssentialEntity>
    ): String {
        val separated = isContainerSeparatedEssentials(settings, allEssentials)
        if (separated && !essential.zenContainerGuid.isNullOrBlank() && essential.zenContainerGuid != "default") {
            val matchedSpace = spaces.firstOrNull { it.zenContainerGuid == essential.zenContainerGuid }
            if (matchedSpace != null) return matchedSpace.id
            if (spaces.any { it.id == activeSpaceId }) return activeSpaceId
        }
        if (!essential.spaceId.isNullOrBlank() && spaces.any { it.id == essential.spaceId }) {
            return essential.spaceId
        }
        return ESSENTIAL_GLOBAL_SPACE_ID
    }

    /**
     * Picks the best fallback active tab after closing or unloading one or more tabs.
     * Returns null when the active Space has no remaining loaded tab (caller should open a Start Page).
     */
    fun resolveFallbackTabId(
        remainingTabs: List<BrowserTabUi>,
        activeSpaceId: String,
        isPrivateView: Boolean
    ): String? {
        if (isPrivateView) {
            return remainingTabs
                .filter { it.isPrivate }
                .maxByOrNull { it.lastAccessedAt }
                ?.id
        }
        return remainingTabs
            .filter { !it.isPrivate && !it.isUnloaded && !it.isEssentialTab && it.spaceId == activeSpaceId }
            .maxByOrNull { it.lastAccessedAt }
            ?.id
    }

    fun collectLoadedEssentialsAndPinsInScope(state: BrowserUiState): List<BrowserTabUi> {
        if (state.isPrivateView) return emptyList()
        val visibleEssIds = state.activeSpaceEssentials.mapTo(mutableSetOf()) { it.id }
        val visibleZenIds = state.activeSpaceZenPinned.mapTo(mutableSetOf()) { it.id }
        return state.tabs.filter { tab ->
            !tab.isPrivate && !tab.isUnloaded && (
                (tab.essentialId != null && tab.essentialId in visibleEssIds) ||
                    (tab.zenPinnedId != null && tab.zenPinnedId in visibleZenIds) ||
                    (tab.isPinned && !tab.isBoundWorkspaceTab && tab.spaceId == state.activeSpaceId)
                )
        }
    }

    fun hasClosableRegularTabs(state: BrowserUiState): Boolean {
        if (state.isPrivateView) {
            return state.tabs.any { it.isPrivate }
        }
        val regular = state.visibleRegularTabs
        return regular.size > 1 || regular.any { !it.isStartPage }
    }

    fun hasAnythingToCloseAll(state: BrowserUiState): Boolean {
        if (state.isPrivateView) {
            return state.tabs.any { it.isPrivate }
        }
        return collectLoadedEssentialsAndPinsInScope(state).isNotEmpty() || hasClosableRegularTabs(state)
    }
}
