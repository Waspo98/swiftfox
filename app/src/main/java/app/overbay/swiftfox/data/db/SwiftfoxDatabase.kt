package app.overbay.swiftfox.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SpaceEntity::class,
        EssentialEntity::class,
        TabEntity::class,
        BookmarkEntity::class,
        HistoryEntity::class,
        SiteOverrideEntity::class,
        DownloadRecordEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SwiftfoxDatabase : RoomDatabase() {
    abstract fun spaceDao(): SpaceDao
    abstract fun essentialDao(): EssentialDao
    abstract fun tabDao(): TabDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun siteOverrideDao(): SiteOverrideDao
    abstract fun downloadRecordDao(): DownloadRecordDao

    suspend fun seedDefaultsIfEmpty() {
        if (spaceDao().getAll().isEmpty()) {
            spaceDao().upsertAll(
                listOf(
                    SpaceEntity(
                        id = DEFAULT_SPACE_ID,
                        name = "Personal",
                        iconKey = "home",
                        containerProfileName = null,
                        sortOrder = 0
                    ),
                    SpaceEntity(
                        id = "space_work",
                        name = "Work",
                        iconKey = "work",
                        containerProfileName = "space_work",
                        sortOrder = 1
                    ),
                    SpaceEntity(
                        id = "space_reading",
                        name = "Reading",
                        iconKey = "book",
                        containerProfileName = null,
                        sortOrder = 2
                    )
                )
            )
        }
        if (essentialDao().count() == 0) {
            essentialDao().upsertAll(
                listOf(
                    EssentialEntity(
                        id = "ess_overbay",
                        title = "Overbay",
                        url = "https://login.overbay.app",
                        domain = "overbay.app",
                        sortOrder = 0
                    ),
                    EssentialEntity(
                        id = "ess_github",
                        title = "GitHub",
                        url = "https://github.com",
                        domain = "github.com",
                        sortOrder = 1
                    ),
                    EssentialEntity(
                        id = "ess_youtube",
                        title = "YouTube",
                        url = "https://m.youtube.com",
                        domain = "youtube.com",
                        sortOrder = 2
                    ),
                    EssentialEntity(
                        id = "ess_reddit",
                        title = "Reddit",
                        url = "https://www.reddit.com",
                        domain = "reddit.com",
                        sortOrder = 3
                    ),
                    EssentialEntity(
                        id = "ess_wiki",
                        title = "Wikipedia",
                        url = "https://en.wikipedia.org",
                        domain = "wikipedia.org",
                        sortOrder = 4
                    ),
                    EssentialEntity(
                        id = "ess_hn",
                        title = "Hacker News",
                        url = "https://news.ycombinator.com",
                        domain = "news.ycombinator.com",
                        sortOrder = 5
                    )
                )
            )
        }
    }

    companion object {
        const val DEFAULT_SPACE_ID = "space_personal"

        @Volatile
        private var instance: SwiftfoxDatabase? = null

        fun getInstance(context: Context): SwiftfoxDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SwiftfoxDatabase::class.java,
                    "swiftfox.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
