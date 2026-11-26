package app.overbay.swiftfox.ui.tabs

import app.overbay.swiftfox.ui.browser.BrowserTabUi

data class AutoClearedTabsSnapshot(
    val regularTabs: List<BrowserTabUi> = emptyList(),
    val pinnedOrBoundTabs: List<BrowserTabUi> = emptyList()
) {
    val allCustomIcons: List<String>
        get() = buildList {
            regularTabs.forEach { it.customIcon?.let(::add) }
            pinnedOrBoundTabs.forEach { it.customIcon?.let(::add) }
        }
}

data class TabAutoClearPlan(
    val expiredPrivateTabIds: List<String> = emptyList(),
    val expiredRegularTabs: List<BrowserTabUi> = emptyList(),
    val expiredPinnedOrBoundTabs: List<BrowserTabUi> = emptyList()
) {
    val normalClearedCount: Int
        get() = expiredRegularTabs.size + expiredPinnedOrBoundTabs.size

    val hasAnyChanges: Boolean
        get() = expiredPrivateTabIds.isNotEmpty() || normalClearedCount > 0
}

/**
 * Pure policy for expiring inactive opened tabs and resetting stale loaded Pins/Essentials
 * back to their pinned baseline without touching active or media-playing tabs.
 */
object TabAutoClearPolicy {
    const val PRIVATE_TAB_MAX_AGE_MS: Long = 24L * 60L * 60L * 1000L

    fun evaluate(
        tabs: List<BrowserTabUi>,
        activeTabId: String?,
        normalThresholdMs: Long?,
        nowMs: Long,
        privateThresholdMs: Long = PRIVATE_TAB_MAX_AGE_MS
    ): TabAutoClearPlan {
        val expiredPrivateIds = mutableListOf<String>()
        val expiredRegular = mutableListOf<BrowserTabUi>()
        val expiredPinnedOrBound = mutableListOf<BrowserTabUi>()

        for (tab in tabs) {
            val ageMs = (nowMs - tab.lastAccessedAt).coerceAtLeast(0L)
            if (tab.isPrivate) {
                // Private tabs expire unconditionally after 24 hours of inactivity (unless playing media).
                if (!tab.isPlayingMedia && ageMs >= privateThresholdMs) {
                    expiredPrivateIds += tab.id
                }
                continue
            }

            if (normalThresholdMs == null) continue
            if (tab.id == activeTabId || tab.isPlayingMedia || tab.isUnloaded) continue
            if (ageMs < normalThresholdMs) continue

            if (tab.isPinned || tab.isBoundWorkspaceTab) {
                expiredPinnedOrBound += tab
            } else {
                expiredRegular += tab
            }
        }

        return TabAutoClearPlan(
            expiredPrivateTabIds = expiredPrivateIds,
            expiredRegularTabs = expiredRegular,
            expiredPinnedOrBoundTabs = expiredPinnedOrBound
        )
    }

    fun formatAutoClearMessage(regularCount: Int, pinnedOrBoundCount: Int): String? {
        val total = regularCount + pinnedOrBoundCount
        if (total <= 0) return null
        return when {
            regularCount == 0 -> "Auto-cleared $pinnedOrBoundCount inactive pinned ${if (pinnedOrBoundCount == 1) "tab" else "tabs"}"
            else -> "Auto-cleared $total inactive ${if (total == 1) "tab" else "tabs"}"
        }
    }
}
