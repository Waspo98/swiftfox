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
    val webDarkModeOverride: Boolean? = null
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
