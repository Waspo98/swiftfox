package app.overbay.swiftfox.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SpaceEntity::class,
        EssentialEntity::class,
        TabEntity::class,
        BookmarkEntity::class,
        HistoryEntity::class,
        SiteOverrideEntity::class,
        DownloadRecordEntity::class,
        FilterListEntity::class,
        ZappedRuleEntity::class,
        UserscriptEntity::class,
        UserscriptStorageEntity::class
    ],
    version = 2,
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
    abstract fun filterListDao(): FilterListDao
    abstract fun zappedRuleDao(): ZappedRuleDao
    abstract fun userscriptDao(): UserscriptDao
    abstract fun userscriptStorageDao(): UserscriptStorageDao

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
        if (filterListDao().count() == 0) {
            filterListDao().upsertAll(
                listOf(
                    FilterListEntity(
                        id = "fl_easylist",
                        name = "EasyList",
                        url = "https://easylist.to/easylist/easylist.txt",
                        enabled = true,
                        isBuiltIn = true
                    ),
                    FilterListEntity(
                        id = "fl_easyprivacy",
                        name = "EasyPrivacy",
                        url = "https://easylist.to/easylist/easyprivacy.txt",
                        enabled = true,
                        isBuiltIn = true
                    ),
                    FilterListEntity(
                        id = "fl_ubo_filters",
                        name = "uBlock Origin Filters",
                        url = "https://ublockorigin.github.io/uAssetsCDN/filters/filters.min.txt",
                        enabled = true,
                        isBuiltIn = true
                    ),
                    FilterListEntity(
                        id = "fl_ubo_badware",
                        name = "uBlock Badware Risks",
                        url = "https://ublockorigin.github.io/uAssetsCDN/filters/badware.min.txt",
                        enabled = true,
                        isBuiltIn = true
                    ),
                    FilterListEntity(
                        id = "fl_ubo_annoyances",
                        name = "uBlock Annoyances",
                        url = "https://ublockorigin.github.io/uAssetsCDN/filters/annoyances-others.min.txt",
                        enabled = false,
                        isBuiltIn = true
                    )
                )
            )
        }
    }

    companion object {
        const val DEFAULT_SPACE_ID = "space_personal"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `site_overrides` ADD COLUMN `adblockDisabled` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `site_overrides` ADD COLUMN `cookiesEnabled` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `site_overrides` ADD COLUMN `customUserAgent` TEXT")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `filter_lists` (" +
                        "`id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, " +
                        "`isBuiltIn` INTEGER NOT NULL, " +
                        "`ruleCount` INTEGER NOT NULL, " +
                        "`lastUpdatedAt` INTEGER NOT NULL, " +
                        "`etag` TEXT, " +
                        "`lastError` TEXT, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `zapped_rules` (" +
                        "`id` TEXT NOT NULL, " +
                        "`host` TEXT NOT NULL, " +
                        "`selector` TEXT NOT NULL, " +
                        "`label` TEXT NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `userscripts` (" +
                        "`id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`namespace` TEXT NOT NULL, " +
                        "`version` TEXT NOT NULL, " +
                        "`description` TEXT NOT NULL, " +
                        "`sourceUrl` TEXT, " +
                        "`code` TEXT NOT NULL, " +
                        "`matchesJson` TEXT NOT NULL, " +
                        "`excludesJson` TEXT NOT NULL, " +
                        "`runAt` TEXT NOT NULL, " +
                        "`grantsJson` TEXT NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, " +
                        "`installedAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `userscript_storage` (" +
                        "`scriptId` TEXT NOT NULL, " +
                        "`storageKey` TEXT NOT NULL, " +
                        "`valueJson` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`scriptId`, `storageKey`))"
                )
            }
        }

        @Volatile
        private var instance: SwiftfoxDatabase? = null

        fun getInstance(context: Context): SwiftfoxDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SwiftfoxDatabase::class.java,
                    "swiftfox.db"
                ).addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}