package app.overbay.swiftfox.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "spaces")
data class SpaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconKey: String = "fox",
    val containerProfileName: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "essentials")
data class EssentialEntity(
    @PrimaryKey val id: String,
    val spaceId: String? = null,
    val title: String,
    val url: String,
    val domain: String,
    val sortOrder: Int = 0
)

@Entity(tableName = "tabs")
data class TabEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val title: String,
    val url: String,
    val isPrivate: Boolean = false,
    val isPinned: Boolean = false,
    val siteModeOverride: String? = null,
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val folder: String = "Bookmarks",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
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
)

@Entity(tableName = "downloads")
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

@Entity(tableName = "zapped_rules")
data class ZappedRuleEntity(
    @PrimaryKey val id: String,
    val host: String,
    val selector: String,
    val label: String = "",
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "userscripts")
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
