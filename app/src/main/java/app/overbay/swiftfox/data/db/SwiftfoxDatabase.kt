package app.overbay.swiftfox.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
        UserscriptStorageEntity::class,
        SyncedRemoteTabEntity::class,
        ZenPinnedItemEntity::class
    ],
    version = 6,
    exportSchema = true
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
    abstract fun syncedRemoteTabDao(): SyncedRemoteTabDao
    abstract fun zenPinnedItemDao(): ZenPinnedItemDao

    private val seedMutex = Mutex()

    suspend fun seedDefaultsIfEmpty(seedInitialContent: Boolean = true) = seedMutex.withLock {
        if (!seedInitialContent && spaceDao().count() > 0) {
            return@withLock
        }
        withTransaction {
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
            if (seedInitialContent && essentialDao().count() == 0) {
                essentialDao().upsertAll(
                    listOf(
                        EssentialEntity(
                            id = "ess_google",
                            title = "Google",
                            url = "https://www.google.com",
                            domain = "google.com",
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
            if (seedInitialContent && filterListDao().count() == 0) {
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

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `spaces` ADD COLUMN `syncGuid` TEXT")
                db.execSQL("ALTER TABLE `spaces` ADD COLUMN `themeColorHex` TEXT")
                db.execSQL("ALTER TABLE `spaces` ADD COLUMN `zenContainerGuid` TEXT")

                db.execSQL("ALTER TABLE `essentials` ADD COLUMN `syncGuid` TEXT")
                db.execSQL("ALTER TABLE `essentials` ADD COLUMN `zenContainerGuid` TEXT")

                db.execSQL("ALTER TABLE `bookmarks` ADD COLUMN `syncGuid` TEXT")
                db.execSQL("ALTER TABLE `bookmarks` ADD COLUMN `parentGuid` TEXT")
                db.execSQL("ALTER TABLE `bookmarks` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `synced_remote_tabs` (" +
                        "`id` TEXT NOT NULL, " +
                        "`deviceId` TEXT NOT NULL, " +
                        "`deviceName` TEXT NOT NULL, " +
                        "`deviceType` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, " +
                        "`iconUrl` TEXT, " +
                        "`lastUsedAt` INTEGER NOT NULL, " +
                        "`position` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `zen_pinned_items` (" +
                        "`id` TEXT NOT NULL, " +
                        "`spaceId` TEXT NOT NULL, " +
                        "`zenWorkspaceUuid` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, " +
                        "`folderName` TEXT, " +
                        "`isPinned` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `essentials` ADD COLUMN `customIcon` TEXT")
                db.execSQL("ALTER TABLE `tabs` ADD COLUMN `customIcon` TEXT")
                db.execSQL("ALTER TABLE `tabs` ADD COLUMN `customTitle` TEXT")
                db.execSQL("ALTER TABLE `zen_pinned_items` ADD COLUMN `customIcon` TEXT")
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tabs` ADD COLUMN `essentialId` TEXT")
                db.execSQL("ALTER TABLE `tabs` ADD COLUMN `zenPinnedId` TEXT")
                db.execSQL("ALTER TABLE `tabs` ADD COLUMN `isUnloaded` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `tabs` ADD COLUMN `pinnedUrl` TEXT")
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tabs_spaceId` ON `tabs` (`spaceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tabs_essentialId` ON `tabs` (`essentialId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tabs_zenPinnedId` ON `tabs` (`zenPinnedId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tabs_isPrivate_isPinned_sortOrder` ON `tabs` (`isPrivate`, `isPinned`, `sortOrder`)")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_url` ON `bookmarks` (`url`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_createdAt` ON `bookmarks` (`createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_updatedAt_createdAt` ON `bookmarks` (`updatedAt`, `createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_syncGuid` ON `bookmarks` (`syncGuid`)")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_lastVisitedAt` ON `history` (`lastVisitedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_visitCount_lastVisitedAt` ON `history` (`visitCount`, `lastVisitedAt`)")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_createdAt` ON `downloads` (`createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_systemDownloadId` ON `downloads` (`systemDownloadId`)")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_zapped_rules_host_enabled_createdAt` ON `zapped_rules` (`host`, `enabled`, `createdAt`)")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_userscripts_enabled_installedAt` ON `userscripts` (`enabled`, `installedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_userscripts_sourceUrl` ON `userscripts` (`sourceUrl`)")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_zen_pinned_items_spaceId` ON `zen_pinned_items` (`spaceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_zen_pinned_items_sortOrder` ON `zen_pinned_items` (`sortOrder`)")
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
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6
                )
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}