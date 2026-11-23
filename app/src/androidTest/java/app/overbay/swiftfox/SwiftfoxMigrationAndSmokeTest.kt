package app.overbay.swiftfox

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.overbay.swiftfox.data.db.SwiftfoxDatabase
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
            .addMigrations(SwiftfoxDatabase.MIGRATION_1_2, SwiftfoxDatabase.MIGRATION_2_3)
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
        } finally {
            migrated.close()
            context.deleteDatabase(dbName)
        }
    }
}
