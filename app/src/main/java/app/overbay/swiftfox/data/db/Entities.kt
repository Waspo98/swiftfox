package app.overbay.swiftfox.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "spaces")
data class SpaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconKey: String = "fox",
    val containerProfileName: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val syncGuid: String? = null,
    val themeColorHex: String? = null,
    val zenContainerGuid: String? = null
)

@Entity(tableName = "essentials")
data class EssentialEntity(
    @PrimaryKey val id: String,
    val spaceId: String? = null,
    val title: String,
    val url: String,
    val domain: String,
    val sortOrder: Int = 0,
    val syncGuid: String? = null,
    val zenContainerGuid: String? = null,
    val customIcon: String? = null
)

@Entity(
    tableName = "tabs",
    indices = [
        Index(value = ["spaceId"], name = "index_tabs_spaceId"),
        Index(value = ["essentialId"], name = "index_tabs_essentialId"),
        Index(value = ["zenPinnedId"], name = "index_tabs_zenPinnedId"),
        Index(value = ["isPrivate", "isPinned", "sortOrder"], name = "index_tabs_isPrivate_isPinned_sortOrder")
    ]
)
data class TabEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val title: String,
    val url: String,
    val isPrivate: Boolean = false,
    val isPinned: Boolean = false,
    val siteModeOverride: String? = null,
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
    val customIcon: String? = null,
    val customTitle: String? = null,
    val essentialId: String? = null,
    val zenPinnedId: String? = null,
    val isUnloaded: Boolean = false,
    val pinnedUrl: String? = null
)

@Entity(
    tableName = "bookmarks",
    indices = [
        Index(value = ["url"], name = "index_bookmarks_url"),
        Index(value = ["createdAt"], name = "index_bookmarks_createdAt"),
        Index(value = ["updatedAt", "createdAt"], name = "index_bookmarks_updatedAt_createdAt"),
        Index(value = ["syncGuid"], name = "index_bookmarks_syncGuid")
    ]
)
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val folder: String = "Bookmarks",
    val createdAt: Long = System.currentTimeMillis(),
    val syncGuid: String? = null,
    val parentGuid: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "history",
    indices = [
        Index(value = ["lastVisitedAt"], name = "index_history_lastVisitedAt"),
        Index(value = ["visitCount", "lastVisitedAt"], name = "index_history_visitCount_lastVisitedAt")
    ]
)
data class HistoryEntity(
    @PrimaryKey val url: String,
    val title: String,
    val visitCount: Int = 1,
    val lastVisitedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "site_overrides")
data class SiteOverrideEntity(
    @PrimaryKey val host: String,
    val siteModeOverride: String? = null,
    val httpsOnlyBypass: Boolean = false,
    val externalAppPolicyOverride: String? = null,
    val allowCamera: Boolean? = null,
    val allowMic: Boolean? = null,
    val allowLocation: Boolean? = null,
    val allowNotifications: Boolean? = null,
    val allowProtectedMedia: Boolean? = null,
    val javascriptEnabled: Boolean = true,
    val webDarkModeOverride: Boolean? = null,
    val adblockDisabled: Boolean = false,
    val cookiesEnabled: Boolean = true,
    val customUserAgent: String? = null
) {
    fun isDefault(): Boolean =
        siteModeOverride == null &&
            !httpsOnlyBypass &&
            externalAppPolicyOverride == null &&
            allowCamera == null &&
            allowMic == null &&
            allowLocation == null &&
            allowNotifications == null &&
            allowProtectedMedia == null &&
            javascriptEnabled &&
            webDarkModeOverride == null &&
            !adblockDisabled &&
            cookiesEnabled &&
            customUserAgent.isNullOrBlank()
}

@Entity(
    tableName = "downloads",
    indices = [
        Index(value = ["createdAt"], name = "index_downloads_createdAt"),
        Index(value = ["systemDownloadId"], name = "index_downloads_systemDownloadId")
    ]
)
data class DownloadRecordEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val url: String,
    val mimeType: String,
    val systemDownloadId: Long = -1L,
    val status: String = "Downloading",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "filter_lists")
data class FilterListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val isBuiltIn: Boolean = false,
    val ruleCount: Int = 0,
    val lastUpdatedAt: Long = 0L,
    val etag: String? = null,
    val lastError: String? = null
)

@Entity(
    tableName = "zapped_rules",
    indices = [
        Index(value = ["host", "enabled", "createdAt"], name = "index_zapped_rules_host_enabled_createdAt")
    ]
)
data class ZappedRuleEntity(
    @PrimaryKey val id: String,
    val host: String,
    val selector: String,
    val label: String = "",
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "userscripts",
    indices = [
        Index(value = ["enabled", "installedAt"], name = "index_userscripts_enabled_installedAt"),
        Index(value = ["sourceUrl"], name = "index_userscripts_sourceUrl")
    ]
)
data class UserscriptEntity(
    @PrimaryKey val id: String,
    val name: String,
    val namespace: String = "",
    val version: String = "1.0",
    val description: String = "",
    val sourceUrl: String? = null,
    val code: String,
    val matchesJson: String = "[]",
    val excludesJson: String = "[]",
    val runAt: String = "document-end",
    val grantsJson: String = "[]",
    val enabled: Boolean = true,
    val installedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Lightweight projection omitting the up-to-2MB `code` column so Room `CursorWindow` never
 * overflows on `observeAll()` and `BrowserUiState` doesn't pin multi-MB JS sources in RAM.
 */
data class UserscriptSummary(
    val id: String,
    val name: String,
    val namespace: String,
    val version: String,
    val description: String,
    val sourceUrl: String?,
    val matchesJson: String,
    val excludesJson: String,
    val runAt: String,
    val grantsJson: String,
    val enabled: Boolean,
    val installedAt: Long,
    val updatedAt: Long
) {
    fun toMetadataEntity(): UserscriptEntity = UserscriptEntity(
        id = id,
        name = name,
        namespace = namespace,
        version = version,
        description = description,
        sourceUrl = sourceUrl,
        code = "",
        matchesJson = matchesJson,
        excludesJson = excludesJson,
        runAt = runAt,
        grantsJson = grantsJson,
        enabled = enabled,
        installedAt = installedAt,
        updatedAt = updatedAt
    )
}

@Entity(
    tableName = "userscript_storage",
    primaryKeys = ["scriptId", "storageKey"]
)
data class UserscriptStorageEntity(
    val scriptId: String,
    val storageKey: String,
    val valueJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "synced_remote_tabs")
data class SyncedRemoteTabEntity(
    @PrimaryKey val id: String,
    val deviceId: String,
    val deviceName: String,
    val deviceType: String = "DESKTOP",
    val title: String,
    val url: String,
    val iconUrl: String? = null,
    val lastUsedAt: Long = 0L,
    val position: Int = 0
)

@Entity(
    tableName = "zen_pinned_items",
    indices = [
        Index(value = ["spaceId"], name = "index_zen_pinned_items_spaceId"),
        Index(value = ["sortOrder"], name = "index_zen_pinned_items_sortOrder")
    ]
)
data class ZenPinnedItemEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val zenWorkspaceUuid: String,
    val title: String,
    val url: String,
    val folderName: String? = null,
    val isPinned: Boolean = true,
    val sortOrder: Int = 0,
    val customIcon: String? = null
)
