package app.overbay.swiftfox.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import app.overbay.swiftfox.features.adblock.BuiltInFilterLists
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Database(
    entities = [
        SpaceEntity::class,
        EssentialEntity::class,
        PinnedFolderEntity::class,
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
        ZenPinnedItemEntity::class,
        WebAppEntity::class
    ],
    version = 10,
    exportSchema = true
)
abstract class SwiftfoxDatabase : RoomDatabase() {
    abstract fun spaceDao(): SpaceDao
    abstract fun essentialDao(): EssentialDao
    abstract fun pinnedFolderDao(): PinnedFolderDao
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
    abstract fun webAppDao(): WebAppDao

    private val seedMutex = Mutex()

    suspend fun seedDefaultsIfEmpty(seedInitialContent: Boolean = true) = seedMutex.withLock {
        if (!seedInitialContent && spaceDao().count() > 0) {
            val existingLists = filterListDao().getAll()
            if (existingLists.isNotEmpty()) {
                val upserts = BuiltInFilterLists.computeReconciliationUpserts(existingLists)
                if (upserts.isNotEmpty()) {
                    filterListDao().upsertAll(upserts)
                }
            }
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
                            sortOrder = 0
                        ),
                        SpaceEntity(
                            id = "space_work",
                            name = "Work",
                            iconKey = "work",
                            sortOrder = 1
                        ),
                        SpaceEntity(
                            id = "space_reading",
                            name = "Reading",
                            iconKey = "book",
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
            val existingLists = filterListDao().getAll()
            if (existingLists.isEmpty()) {
                if (seedInitialContent) {
                    filterListDao().upsertAll(BuiltInFilterLists.defaults)
                }
            } else {
                val upserts = BuiltInFilterLists.computeReconciliationUpserts(existingLists)
                if (upserts.isNotEmpty()) {
                    filterListDao().upsertAll(upserts)
                }
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

        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `spaces_new` (" +
                        "`id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`iconKey` TEXT NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`syncGuid` TEXT, " +
                        "`themeColorHex` TEXT, " +
                        "`zenContainerGuid` TEXT, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "INSERT INTO `spaces_new` (`id`, `name`, `iconKey`, `sortOrder`, `createdAt`, `syncGuid`, `themeColorHex`, `zenContainerGuid`) " +
                        "SELECT `id`, `name`, `iconKey`, `sortOrder`, `createdAt`, `syncGuid`, `themeColorHex`, `zenContainerGuid` FROM `spaces`"
                )
                db.execSQL("DROP TABLE `spaces`")
                db.execSQL("ALTER TABLE `spaces_new` RENAME TO `spaces`")
            }
        }

        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `web_apps` (" +
                        "`id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`shortName` TEXT NOT NULL, " +
                        "`startUrl` TEXT NOT NULL, " +
                        "`scopeUrl` TEXT NOT NULL, " +
                        "`manifestUrl` TEXT, " +
                        "`host` TEXT NOT NULL, " +
                        "`displayMode` TEXT NOT NULL, " +
                        "`openAsStandalone` INTEGER NOT NULL, " +
                        "`showSlimHeader` INTEGER NOT NULL, " +
                        "`themeColorHex` TEXT, " +
                        "`backgroundColorHex` TEXT, " +
                        "`iconUrl` TEXT, " +
                        "`iconPath` TEXT, " +
                        "`iconMaskable` INTEGER NOT NULL, " +
                        "`customIcon` TEXT, " +
                        "`shortcutsJson` TEXT NOT NULL, " +
                        "`isPwaCapable` INTEGER NOT NULL, " +
                        "`pinnedShortcutId` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`lastLaunchedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_web_apps_startUrl` ON `web_apps` (`startUrl`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_web_apps_host` ON `web_apps` (`host`)")
            }
        }

        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `web_apps` ADD COLUMN `drawBehindStatusBar` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tabs` ADD COLUMN `folderId` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tabs_folderId` ON `tabs` (`folderId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pinned_folders` (" +
                        "`id` TEXT NOT NULL, " +
                        "`spaceId` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`customIcon` TEXT, " +
                        "`isExpanded` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pinned_folders_spaceId` ON `pinned_folders` (`spaceId`)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pinned_folders_spaceId_sortOrder` ON `pinned_folders` (`spaceId`, `sortOrder`)"
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
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10
                )
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}