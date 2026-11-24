package app.overbay.swiftfox

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.overbay.swiftfox.data.db.FilterListEntity
import app.overbay.swiftfox.data.db.SwiftfoxDatabase
import app.overbay.swiftfox.data.db.SyncedRemoteTabEntity
import app.overbay.swiftfox.data.db.UserscriptEntity
import app.overbay.swiftfox.data.db.ZappedRuleEntity
import app.overbay.swiftfox.data.db.ZenPinnedItemEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SwiftfoxMigrationAndSmokeTest {

    @Test
    fun packageIdentityMatchesStandardOrBetaChannel() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(
            "Unexpected package name: ${context.packageName}",
            context.packageName == "app.overbay.swiftfox" ||
                context.packageName == "app.overbay.swiftfox.beta"
        )
    }

    @Test
    fun roomDatabaseMigratesCleanlyFromV1ToV3() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "swiftfox_migration_test.db"
        context.deleteDatabase(dbName)

        // Seed a real v1 schema matching Swiftfox M1 (version = 1)
        val v1Helper = object : SQLiteOpenHelper(context, dbName, null, 1) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `spaces` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `iconKey` TEXT NOT NULL, " +
                        "`containerProfileName` TEXT, `sortOrder` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `essentials` (" +
                        "`id` TEXT NOT NULL, `spaceId` TEXT, `title` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, `domain` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tabs` (" +
                        "`id` TEXT NOT NULL, `spaceId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, `isPrivate` INTEGER NOT NULL, `isPinned` INTEGER NOT NULL, " +
                        "`siteModeOverride` TEXT, `lastAccessedAt` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bookmarks` (" +
                        "`id` TEXT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, " +
                        "`folder` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `history` (" +
                        "`url` TEXT NOT NULL, `title` TEXT NOT NULL, `visitCount` INTEGER NOT NULL, " +
                        "`lastVisitedAt` INTEGER NOT NULL, PRIMARY KEY(`url`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `site_overrides` (" +
                        "`host` TEXT NOT NULL, `siteModeOverride` TEXT, `httpsOnlyBypass` INTEGER NOT NULL, " +
                        "`externalAppPolicyOverride` TEXT, `allowCamera` INTEGER, `allowMic` INTEGER, " +
                        "`allowLocation` INTEGER, `allowNotifications` INTEGER, `allowProtectedMedia` INTEGER, " +
                        "`javascriptEnabled` INTEGER NOT NULL, `webDarkModeOverride` INTEGER, " +
                        "PRIMARY KEY(`host`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `downloads` (" +
                        "`id` TEXT NOT NULL, `fileName` TEXT NOT NULL, `url` TEXT NOT NULL, " +
                        "`mimeType` TEXT NOT NULL, `systemDownloadId` INTEGER NOT NULL, " +
                        "`status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "INSERT INTO `spaces` (`id`, `name`, `iconKey`, `containerProfileName`, `sortOrder`, `createdAt`) " +
                        "VALUES ('space_personal', 'Personal', 'home', NULL, 0, 1000)"
                )
                db.execSQL(
                    "INSERT INTO `bookmarks` (`id`, `title`, `url`, `folder`, `createdAt`) " +
                        "VALUES ('bm_1', 'Example', 'https://example.com', 'Bookmarks', 1000)"
                )
                db.execSQL(
                    "INSERT INTO `site_overrides` (`host`, `siteModeOverride`, `httpsOnlyBypass`, " +
                        "`externalAppPolicyOverride`, `allowCamera`, `allowMic`, `allowLocation`, " +
                        "`allowNotifications`, `allowProtectedMedia`, `javascriptEnabled`, `webDarkModeOverride`) " +
                        "VALUES ('example.com', 'DESKTOP', 0, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1)"
                )
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        v1Helper.writableDatabase.close()
        v1Helper.close()

        val migrated = Room.databaseBuilder(context, SwiftfoxDatabase::class.java, dbName)
            .addMigrations(
                SwiftfoxDatabase.MIGRATION_1_2,
                SwiftfoxDatabase.MIGRATION_2_3,
                SwiftfoxDatabase.MIGRATION_3_4,
                SwiftfoxDatabase.MIGRATION_4_5
            )
            .build()
        try {
            val spaces = migrated.spaceDao().getAll()
            assertEquals(1, spaces.size)
            assertEquals("Personal", spaces.first().name)

            val bookmark = migrated.bookmarkDao().findByUrl("https://example.com")
            assertNotNull(bookmark)
            assertEquals(0L, bookmark?.updatedAt)

            val siteOverride = migrated.siteOverrideDao().getByHost("example.com")
            assertNotNull(siteOverride)
            assertEquals(false, siteOverride?.adblockDisabled)
            assertEquals(true, siteOverride?.cookiesEnabled)

            // Verify post-migration inserts and queries succeed on M2 and M3 tables
            migrated.filterListDao().upsert(
                FilterListEntity(id = "fl_test", name = "Test List", url = "https://example.com/list.txt")
            )
            assertEquals("Test List", migrated.filterListDao().getById("fl_test")?.name)

            migrated.zappedRuleDao().upsert(
                ZappedRuleEntity(id = "zap_1", host = "example.com", selector = "div.banner", label = "Banner")
            )
            assertEquals(1, migrated.zappedRuleDao().getEnabledForHost("example.com").size)

            migrated.userscriptDao().upsert(
                UserscriptEntity(id = "us_1", name = "Demo Script", code = "console.log(1);")
            )
            assertEquals("Demo Script", migrated.userscriptDao().getById("us_1")?.name)

            migrated.syncedRemoteTabDao().replaceAll(
                listOf(
                    SyncedRemoteTabEntity(
                        id = "rt_1",
                        deviceId = "dev_1",
                        deviceName = "Desktop",
                        title = "Swiftfox",
                        url = "https://overbay.app"
                    )
                )
            )
            assertEquals(1, migrated.syncedRemoteTabDao().observeAll().first().size)

            migrated.zenPinnedItemDao().replaceAll(
                listOf(
                    ZenPinnedItemEntity(
                        id = "zp_1",
                        spaceId = "space_personal",
                        zenWorkspaceUuid = "zen-ws-1",
                        title = "Docs",
                        url = "https://developer.android.com"
                    )
                )
            )
            assertEquals(1, migrated.zenPinnedItemDao().observeAll().first().size)
        } finally {
            migrated.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun roomDatabaseMigratesCleanlyFromV2ToV3() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "swiftfox_migration_v2_v3_test.db"
        context.deleteDatabase(dbName)

        // Seed a real v2 schema matching Swiftfox M2 (version = 2)
        val v2Helper = object : SQLiteOpenHelper(context, dbName, null, 2) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `spaces` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `iconKey` TEXT NOT NULL, " +
                        "`containerProfileName` TEXT, `sortOrder` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `essentials` (" +
                        "`id` TEXT NOT NULL, `spaceId` TEXT, `title` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, `domain` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tabs` (" +
                        "`id` TEXT NOT NULL, `spaceId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, `isPrivate` INTEGER NOT NULL, `isPinned` INTEGER NOT NULL, " +
                        "`siteModeOverride` TEXT, `lastAccessedAt` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bookmarks` (" +
                        "`id` TEXT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, " +
                        "`folder` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `history` (" +
                        "`url` TEXT NOT NULL, `title` TEXT NOT NULL, `visitCount` INTEGER NOT NULL, " +
                        "`lastVisitedAt` INTEGER NOT NULL, PRIMARY KEY(`url`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `site_overrides` (" +
                        "`host` TEXT NOT NULL, `siteModeOverride` TEXT, `httpsOnlyBypass` INTEGER NOT NULL, " +
                        "`externalAppPolicyOverride` TEXT, `allowCamera` INTEGER, `allowMic` INTEGER, " +
                        "`allowLocation` INTEGER, `allowNotifications` INTEGER, `allowProtectedMedia` INTEGER, " +
                        "`javascriptEnabled` INTEGER NOT NULL, `webDarkModeOverride` INTEGER, " +
                        "`adblockDisabled` INTEGER NOT NULL DEFAULT 0, " +
                        "`cookiesEnabled` INTEGER NOT NULL DEFAULT 1, " +
                        "`customUserAgent` TEXT, PRIMARY KEY(`host`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `downloads` (" +
                        "`id` TEXT NOT NULL, `fileName` TEXT NOT NULL, `url` TEXT NOT NULL, " +
                        "`mimeType` TEXT NOT NULL, `systemDownloadId` INTEGER NOT NULL, " +
                        "`status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `filter_lists` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, `isBuiltIn` INTEGER NOT NULL, " +
                        "`ruleCount` INTEGER NOT NULL, `lastUpdatedAt` INTEGER NOT NULL, " +
                        "`etag` TEXT, `lastError` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `zapped_rules` (" +
                        "`id` TEXT NOT NULL, `host` TEXT NOT NULL, `selector` TEXT NOT NULL, " +
                        "`label` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `userscripts` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `namespace` TEXT NOT NULL, " +
                        "`version` TEXT NOT NULL, `description` TEXT NOT NULL, `sourceUrl` TEXT, " +
                        "`code` TEXT NOT NULL, `matchesJson` TEXT NOT NULL, `excludesJson` TEXT NOT NULL, " +
                        "`runAt` TEXT NOT NULL, `grantsJson` TEXT NOT NULL, `enabled` INTEGER NOT NULL, " +
                        "`installedAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `userscript_storage` (" +
                        "`scriptId` TEXT NOT NULL, `storageKey` TEXT NOT NULL, `valueJson` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`scriptId`, `storageKey`))"
                )
                db.execSQL(
                    "INSERT INTO `spaces` (`id`, `name`, `iconKey`, `containerProfileName`, `sortOrder`, `createdAt`) " +
                        "VALUES ('space_work', 'Work', 'work', 'space_work', 1, 2000)"
                )
                db.execSQL(
                    "INSERT INTO `filter_lists` (`id`, `name`, `url`, `enabled`, `isBuiltIn`, `ruleCount`, `lastUpdatedAt`, `etag`, `lastError`) " +
                        "VALUES ('fl_easylist', 'EasyList', 'https://easylist.to/easylist/easylist.txt', 1, 1, 50000, 1700000000, '\"v1\"', NULL)"
                )
                db.execSQL(
                    "INSERT INTO `zapped_rules` (`id`, `host`, `selector`, `label`, `enabled`, `createdAt`) " +
                        "VALUES ('z1', 'news.example.com', 'aside.promo', 'Promo', 1, 2000)"
                )
                db.execSQL(
                    "INSERT INTO `userscripts` (`id`, `name`, `namespace`, `version`, `description`, `sourceUrl`, `code`, `matchesJson`, `excludesJson`, `runAt`, `grantsJson`, `enabled`, `installedAt`, `updatedAt`) " +
                        "VALUES ('u1', 'Tweaks', 'local', '1.0', 'Desc', NULL, 'console.log(1);', '[\"https://example.com/*\"]', '[]', 'document-end', '[]', 1, 2000, 2000)"
                )
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        v2Helper.writableDatabase.close()
        v2Helper.close()

        val migrated = Room.databaseBuilder(context, SwiftfoxDatabase::class.java, dbName)
            .addMigrations(
                SwiftfoxDatabase.MIGRATION_1_2,
                SwiftfoxDatabase.MIGRATION_2_3,
                SwiftfoxDatabase.MIGRATION_3_4,
                SwiftfoxDatabase.MIGRATION_4_5
            )
            .build()
        try {
            val workSpace = migrated.spaceDao().getAll().single()
            assertEquals("Work", workSpace.name)
            assertEquals(null, workSpace.syncGuid)
            assertEquals(null, workSpace.themeColorHex)

            val fl = migrated.filterListDao().getById("fl_easylist")
            assertNotNull(fl)
            assertEquals(50000, fl?.ruleCount)

            val zapped = migrated.zappedRuleDao().getEnabledForHost("news.example.com")
            assertEquals(1, zapped.size)
            assertEquals("aside.promo", zapped.single().selector)

            val script = migrated.userscriptDao().getById("u1")
            assertNotNull(script)
            assertEquals("Tweaks", script?.name)

            migrated.syncedRemoteTabDao().replaceAll(
                listOf(
                    SyncedRemoteTabEntity(
                        id = "rt_v2",
                        deviceId = "dev_zen",
                        deviceName = "Zen Desktop",
                        title = "Android",
                        url = "https://developer.android.com"
                    )
                )
            )
            assertEquals("Zen Desktop", migrated.syncedRemoteTabDao().observeAll().first().single().deviceName)

            migrated.zenPinnedItemDao().replaceAll(
                listOf(
                    ZenPinnedItemEntity(
                        id = "zp_v2",
                        spaceId = "space_work",
                        zenWorkspaceUuid = "ws-work",
                        title = "GitHub",
                        url = "https://github.com"
                    )
                )
            )
            assertEquals("GitHub", migrated.zenPinnedItemDao().observeAll().first().single().title)
        } finally {
            migrated.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun roomDatabaseMigratesCleanlyFromV3ToV4() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "swiftfox_migration_v3_v4_test.db"
        context.deleteDatabase(dbName)

        val v3Helper = object : SQLiteOpenHelper(context, dbName, null, 3) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `spaces` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `iconKey` TEXT NOT NULL, " +
                        "`containerProfileName` TEXT, `sortOrder` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `syncGuid` TEXT, `themeColorHex` TEXT, " +
                        "`zenContainerGuid` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `essentials` (" +
                        "`id` TEXT NOT NULL, `spaceId` TEXT, `title` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, `domain` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                        "`syncGuid` TEXT, `zenContainerGuid` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tabs` (" +
                        "`id` TEXT NOT NULL, `spaceId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, `isPrivate` INTEGER NOT NULL, `isPinned` INTEGER NOT NULL, " +
                        "`siteModeOverride` TEXT, `lastAccessedAt` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bookmarks` (" +
                        "`id` TEXT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, " +
                        "`folder` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `syncGuid` TEXT, " +
                        "`parentGuid` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `history` (" +
                        "`url` TEXT NOT NULL, `title` TEXT NOT NULL, `visitCount` INTEGER NOT NULL, " +
                        "`lastVisitedAt` INTEGER NOT NULL, PRIMARY KEY(`url`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `site_overrides` (" +
                        "`host` TEXT NOT NULL, `siteModeOverride` TEXT, `httpsOnlyBypass` INTEGER NOT NULL, " +
                        "`externalAppPolicyOverride` TEXT, `allowCamera` INTEGER, `allowMic` INTEGER, " +
                        "`allowLocation` INTEGER, `allowNotifications` INTEGER, `allowProtectedMedia` INTEGER, " +
                        "`javascriptEnabled` INTEGER NOT NULL, `webDarkModeOverride` INTEGER, " +
                        "`adblockDisabled` INTEGER NOT NULL, `cookiesEnabled` INTEGER NOT NULL, " +
                        "`customUserAgent` TEXT, PRIMARY KEY(`host`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `downloads` (" +
                        "`id` TEXT NOT NULL, `fileName` TEXT NOT NULL, `url` TEXT NOT NULL, " +
                        "`mimeType` TEXT NOT NULL, `systemDownloadId` INTEGER NOT NULL, " +
                        "`status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `filter_lists` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, `isBuiltIn` INTEGER NOT NULL, " +
                        "`ruleCount` INTEGER NOT NULL, `lastUpdatedAt` INTEGER NOT NULL, " +
                        "`etag` TEXT, `lastError` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `zapped_rules` (" +
                        "`id` TEXT NOT NULL, `host` TEXT NOT NULL, `selector` TEXT NOT NULL, " +
                        "`label` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `userscripts` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `namespace` TEXT NOT NULL, " +
                        "`version` TEXT NOT NULL, `description` TEXT NOT NULL, `sourceUrl` TEXT, " +
                        "`code` TEXT NOT NULL, `matchesJson` TEXT NOT NULL, `excludesJson` TEXT NOT NULL, " +
                        "`runAt` TEXT NOT NULL, `grantsJson` TEXT NOT NULL, `enabled` INTEGER NOT NULL, " +
                        "`installedAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `userscript_storage` (" +
                        "`scriptId` TEXT NOT NULL, `storageKey` TEXT NOT NULL, `valueJson` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`scriptId`, `storageKey`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `synced_remote_tabs` (" +
                        "`id` TEXT NOT NULL, `deviceId` TEXT NOT NULL, `deviceName` TEXT NOT NULL, " +
                        "`deviceType` TEXT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, " +
                        "`iconUrl` TEXT, `lastUsedAt` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `zen_pinned_items` (" +
                        "`id` TEXT NOT NULL, `spaceId` TEXT NOT NULL, `zenWorkspaceUuid` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, `url` TEXT NOT NULL, `folderName` TEXT, " +
                        "`isPinned` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "INSERT INTO `essentials` (`id`, `spaceId`, `title`, `url`, `domain`, `sortOrder`, `syncGuid`, `zenContainerGuid`) " +
                        "VALUES ('ess_1', NULL, 'Overbay', 'https://overbay.app', 'overbay.app', 0, NULL, NULL)"
                )
                db.execSQL(
                    "INSERT INTO `tabs` (`id`, `spaceId`, `title`, `url`, `isPrivate`, `isPinned`, `siteModeOverride`, `lastAccessedAt`, `sortOrder`) " +
                        "VALUES ('tab_1', 'space_personal', 'Overbay', 'https://overbay.app', 0, 1, NULL, 3000, 0)"
                )
                db.execSQL(
                    "INSERT INTO `zen_pinned_items` (`id`, `spaceId`, `zenWorkspaceUuid`, `title`, `url`, `folderName`, `isPinned`, `sortOrder`) " +
                        "VALUES ('zp_v3', 'space_personal', 'ws_1', 'Docs', 'https://kotlinlang.org', NULL, 1, 0)"
                )
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        v3Helper.writableDatabase.close()
        v3Helper.close()

        val migrated = Room.databaseBuilder(context, SwiftfoxDatabase::class.java, dbName)
            .addMigrations(
                SwiftfoxDatabase.MIGRATION_1_2,
                SwiftfoxDatabase.MIGRATION_2_3,
                SwiftfoxDatabase.MIGRATION_3_4,
                SwiftfoxDatabase.MIGRATION_4_5
            )
            .build()
        try {
            val ess = migrated.essentialDao().getAll().single()
            assertEquals("Overbay", ess.title)
            assertEquals(null, ess.customIcon)

            val tab = migrated.tabDao().getNormalTabs().single()
            assertEquals(true, tab.isPinned)
            assertEquals(null, tab.customIcon)
            assertEquals(null, tab.customTitle)

            val updatedTab = tab.copy(customIcon = "emoji:🦊", customTitle = "Home")
            migrated.tabDao().upsert(updatedTab)
            val reloaded = migrated.tabDao().getNormalTabs().single()
            assertEquals("emoji:🦊", reloaded.customIcon)
            assertEquals("Home", reloaded.customTitle)

            val zp = migrated.zenPinnedItemDao().getAll().single()
            assertEquals(null, zp.customIcon)
        } finally {
            migrated.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun roomDatabaseMigratesCleanlyFromV4ToV5() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "swiftfox_migration_v4_v5_test.db"
        context.deleteDatabase(dbName)

        val v4Helper = object : SQLiteOpenHelper(context, dbName, null, 4) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `spaces` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `iconKey` TEXT NOT NULL, " +
                        "`containerProfileName` TEXT, `sortOrder` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `syncGuid` TEXT, `themeColorHex` TEXT, " +
                        "`zenContainerGuid` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `essentials` (" +
                        "`id` TEXT NOT NULL, `spaceId` TEXT, `title` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, `domain` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                        "`syncGuid` TEXT, `zenContainerGuid` TEXT, `customIcon` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tabs` (" +
                        "`id` TEXT NOT NULL, `spaceId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, `isPrivate` INTEGER NOT NULL, `isPinned` INTEGER NOT NULL, " +
                        "`siteModeOverride` TEXT, `lastAccessedAt` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, `customIcon` TEXT, `customTitle` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bookmarks` (" +
                        "`id` TEXT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, " +
                        "`folder` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `syncGuid` TEXT, " +
                        "`parentGuid` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `history` (" +
                        "`url` TEXT NOT NULL, `title` TEXT NOT NULL, `visitCount` INTEGER NOT NULL, " +
                        "`lastVisitedAt` INTEGER NOT NULL, PRIMARY KEY(`url`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `site_overrides` (" +
                        "`host` TEXT NOT NULL, `siteModeOverride` TEXT, `httpsOnlyBypass` INTEGER NOT NULL, " +
                        "`externalAppPolicyOverride` TEXT, `allowCamera` INTEGER, `allowMic` INTEGER, " +
                        "`allowLocation` INTEGER, `allowNotifications` INTEGER, `allowProtectedMedia` INTEGER, " +
                        "`javascriptEnabled` INTEGER NOT NULL, `webDarkModeOverride` INTEGER, " +
                        "`adblockDisabled` INTEGER NOT NULL, `cookiesEnabled` INTEGER NOT NULL, " +
                        "`customUserAgent` TEXT, PRIMARY KEY(`host`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `downloads` (" +
                        "`id` TEXT NOT NULL, `fileName` TEXT NOT NULL, `url` TEXT NOT NULL, " +
                        "`mimeType` TEXT NOT NULL, `systemDownloadId` INTEGER NOT NULL, " +
                        "`status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `filter_lists` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, `isBuiltIn` INTEGER NOT NULL, " +
                        "`ruleCount` INTEGER NOT NULL, `lastUpdatedAt` INTEGER NOT NULL, " +
                        "`etag` TEXT, `lastError` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `zapped_rules` (" +
                        "`id` TEXT NOT NULL, `host` TEXT NOT NULL, `selector` TEXT NOT NULL, " +
                        "`label` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `userscripts` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `namespace` TEXT NOT NULL, " +
                        "`version` TEXT NOT NULL, `description` TEXT NOT NULL, `sourceUrl` TEXT, " +
                        "`code` TEXT NOT NULL, `matchesJson` TEXT NOT NULL, `excludesJson` TEXT NOT NULL, " +
                        "`runAt` TEXT NOT NULL, `grantsJson` TEXT NOT NULL, `enabled` INTEGER NOT NULL, " +
                        "`installedAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `userscript_storage` (" +
                        "`scriptId` TEXT NOT NULL, `storageKey` TEXT NOT NULL, `valueJson` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`scriptId`, `storageKey`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `synced_remote_tabs` (" +
                        "`id` TEXT NOT NULL, `deviceId` TEXT NOT NULL, `deviceName` TEXT NOT NULL, " +
                        "`deviceType` TEXT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, " +
                        "`iconUrl` TEXT, `lastUsedAt` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `zen_pinned_items` (" +
                        "`id` TEXT NOT NULL, `spaceId` TEXT NOT NULL, `zenWorkspaceUuid` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, `url` TEXT NOT NULL, `folderName` TEXT, " +
                        "`isPinned` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `customIcon` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "INSERT INTO `tabs` (`id`, `spaceId`, `title`, `url`, `isPrivate`, `isPinned`, " +
                        "`siteModeOverride`, `lastAccessedAt`, `sortOrder`, `customIcon`, `customTitle`) " +
                        "VALUES ('tab_v4', 'space_personal', 'Overbay', 'https://overbay.app', 0, 1, NULL, 4000, 0, NULL, 'Home')"
                )
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        v4Helper.writableDatabase.close()
        v4Helper.close()

        val migrated = Room.databaseBuilder(context, SwiftfoxDatabase::class.java, dbName)
            .addMigrations(
                SwiftfoxDatabase.MIGRATION_1_2,
                SwiftfoxDatabase.MIGRATION_2_3,
                SwiftfoxDatabase.MIGRATION_3_4,
                SwiftfoxDatabase.MIGRATION_4_5
            )
            .build()
        try {
            val tab = migrated.tabDao().getNormalTabs().single()
            assertEquals("tab_v4", tab.id)
            assertEquals(true, tab.isPinned)
            assertEquals(null, tab.essentialId)
            assertEquals(null, tab.zenPinnedId)
            assertEquals(false, tab.isUnloaded)
            assertEquals(null, tab.pinnedUrl)

            val updated = tab.copy(
                essentialId = "ess_google",
                isUnloaded = true,
                pinnedUrl = "https://overbay.app"
            )
            migrated.tabDao().upsert(updated)
            val reloaded = migrated.tabDao().getNormalTabs().single()
            assertEquals("ess_google", reloaded.essentialId)
            assertEquals(true, reloaded.isUnloaded)
            assertEquals("https://overbay.app", reloaded.pinnedUrl)
        } finally {
            migrated.close()
            context.deleteDatabase(dbName)
        }
    }
}
