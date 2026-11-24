package app.overbay.swiftfox.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SpaceDao {
    @Query("SELECT * FROM spaces ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<SpaceEntity>>

    @Query("SELECT * FROM spaces ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAll(): List<SpaceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(space: SpaceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(spaces: List<SpaceEntity>)

    @Query("UPDATE spaces SET syncGuid = NULL, zenContainerGuid = NULL WHERE syncGuid IS NOT NULL")
    suspend fun clearSyncMetadata()

    @Query("DELETE FROM spaces WHERE id = :spaceId")
    suspend fun deleteById(spaceId: String)
}

@Dao
interface EssentialDao {
    @Query("SELECT * FROM essentials ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<EssentialEntity>>

    @Query("SELECT * FROM essentials ORDER BY sortOrder ASC")
    suspend fun getAll(): List<EssentialEntity>

    @Query("SELECT COUNT(*) FROM essentials")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(essential: EssentialEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(essentials: List<EssentialEntity>)

    @Query("UPDATE essentials SET syncGuid = NULL, zenContainerGuid = NULL WHERE syncGuid IS NOT NULL")
    suspend fun clearSyncMetadata()

    @Query("DELETE FROM essentials WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface TabDao {
    @Query("SELECT * FROM tabs WHERE isPrivate = 0 ORDER BY isPinned DESC, sortOrder ASC, lastAccessedAt DESC")
    suspend fun getNormalTabs(): List<TabEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tab: TabEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tabs: List<TabEntity>)

    @Query("UPDATE tabs SET spaceId = :newSpaceId WHERE spaceId = :oldSpaceId AND isPrivate = 0")
    suspend fun reassignSpace(oldSpaceId: String, newSpaceId: String)

    @Query("DELETE FROM tabs WHERE id = :tabId")
    suspend fun deleteById(tabId: String)

    @Query("DELETE FROM tabs WHERE spaceId = :spaceId AND isPrivate = 0")
    suspend fun deleteBySpace(spaceId: String)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    suspend fun getAll(): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE url = :url")
    suspend fun findAllByUrl(url: String): List<BookmarkEntity>

    @Query(
        "SELECT * FROM bookmarks WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY createdAt DESC LIMIT :limit"
    )
    suspend fun search(query: String, limit: Int = 6): List<BookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bookmark: BookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(bookmarks: List<BookmarkEntity>)

    @Query("UPDATE bookmarks SET syncGuid = NULL, parentGuid = NULL WHERE syncGuid IS NOT NULL")
    suspend fun clearSyncMetadata()

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY lastVisitedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY lastVisitedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 500): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE url IN (:urls)")
    suspend fun findByUrls(urls: List<String>): List<HistoryEntity>

    @Query(
        "SELECT * FROM history WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY visitCount DESC, lastVisitedAt DESC LIMIT :limit"
    )
    suspend fun search(query: String, limit: Int = 8): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: HistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<HistoryEntity>)

    @Query("DELETE FROM history WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}

@Dao
interface SiteOverrideDao {
    @Query("SELECT * FROM site_overrides ORDER BY host ASC")
    fun observeAll(): Flow<List<SiteOverrideEntity>>

    @Query("SELECT * FROM site_overrides ORDER BY host ASC")
    suspend fun getAll(): List<SiteOverrideEntity>

    @Query("SELECT * FROM site_overrides WHERE host = :host LIMIT 1")
    suspend fun getByHost(host: String): SiteOverrideEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(override: SiteOverrideEntity)

    @Query("DELETE FROM site_overrides WHERE host = :host")
    suspend fun deleteByHost(host: String)
}

@Dao
interface DownloadRecordDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: DownloadRecordEntity)

    @Delete
    suspend fun delete(record: DownloadRecordEntity)

    @Query("DELETE FROM downloads")
    suspend fun clearAll()
}

@Dao
interface FilterListDao {
    @Query("SELECT * FROM filter_lists ORDER BY isBuiltIn DESC, name ASC")
    fun observeAll(): Flow<List<FilterListEntity>>

    @Query("SELECT * FROM filter_lists ORDER BY isBuiltIn DESC, name ASC")
    suspend fun getAll(): List<FilterListEntity>

    @Query("SELECT * FROM filter_lists WHERE enabled = 1 ORDER BY isBuiltIn DESC, name ASC")
    suspend fun getEnabled(): List<FilterListEntity>

    @Query("SELECT * FROM filter_lists WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FilterListEntity?

    @Query("SELECT COUNT(*) FROM filter_lists")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(list: FilterListEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(lists: List<FilterListEntity>)

    @Query("DELETE FROM filter_lists WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ZappedRuleDao {
    @Query("SELECT * FROM zapped_rules ORDER BY host ASC, createdAt DESC")
    fun observeAll(): Flow<List<ZappedRuleEntity>>

    @Query("SELECT * FROM zapped_rules WHERE host = :host ORDER BY createdAt ASC")
    suspend fun getForHost(host: String): List<ZappedRuleEntity>

    @Query("SELECT * FROM zapped_rules WHERE host = :host AND enabled = 1 ORDER BY createdAt ASC")
    suspend fun getEnabledForHost(host: String): List<ZappedRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: ZappedRuleEntity)

    @Query("DELETE FROM zapped_rules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM zapped_rules WHERE host = :host")
    suspend fun deleteByHost(host: String)
}

@Dao
interface UserscriptDao {
    @Query("SELECT * FROM userscripts ORDER BY installedAt DESC")
    fun observeAll(): Flow<List<UserscriptEntity>>

    @Query("SELECT * FROM userscripts ORDER BY installedAt DESC")
    suspend fun getAll(): List<UserscriptEntity>

    @Query("SELECT * FROM userscripts WHERE enabled = 1 ORDER BY installedAt ASC")
    suspend fun getEnabled(): List<UserscriptEntity>

    @Query("SELECT * FROM userscripts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): UserscriptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(script: UserscriptEntity)

    @Query("DELETE FROM userscripts WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface UserscriptStorageDao {
    @Query("SELECT * FROM userscript_storage WHERE scriptId = :scriptId")
    suspend fun getAllForScript(scriptId: String): List<UserscriptStorageEntity>

    @Query("SELECT * FROM userscript_storage WHERE scriptId = :scriptId AND storageKey = :key LIMIT 1")
    suspend fun getValue(scriptId: String, key: String): UserscriptStorageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: UserscriptStorageEntity)

    @Query("DELETE FROM userscript_storage WHERE scriptId = :scriptId AND storageKey = :key")
    suspend fun deleteKey(scriptId: String, key: String)

    @Query("DELETE FROM userscript_storage WHERE scriptId = :scriptId")
    suspend fun deleteAllForScript(scriptId: String)
}

@Dao
interface SyncedRemoteTabDao {
    @Query("SELECT * FROM synced_remote_tabs ORDER BY deviceName ASC, position ASC")
    fun observeAll(): Flow<List<SyncedRemoteTabEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tabs: List<SyncedRemoteTabEntity>)

    @Query("DELETE FROM synced_remote_tabs")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(tabs: List<SyncedRemoteTabEntity>) {
        clearAll()
        if (tabs.isNotEmpty()) upsertAll(tabs)
    }
}

@Dao
interface ZenPinnedItemDao {
    @Query("SELECT * FROM zen_pinned_items ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<ZenPinnedItemEntity>>

    @Query("SELECT * FROM zen_pinned_items ORDER BY sortOrder ASC")
    suspend fun getAll(): List<ZenPinnedItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ZenPinnedItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ZenPinnedItemEntity>)

    @Query("DELETE FROM zen_pinned_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM zen_pinned_items WHERE spaceId = :spaceId")
    suspend fun deleteBySpace(spaceId: String)

    @Query("DELETE FROM zen_pinned_items")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<ZenPinnedItemEntity>) {
        clearAll()
        if (items.isNotEmpty()) upsertAll(items)
    }
}
