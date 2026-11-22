package app.overbay.swiftfox.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("DELETE FROM spaces WHERE id = :spaceId")
    suspend fun deleteById(spaceId: String)
}

@Dao
interface EssentialDao {
    @Query("SELECT * FROM essentials ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<EssentialEntity>>

    @Query("SELECT COUNT(*) FROM essentials")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(essential: EssentialEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(essentials: List<EssentialEntity>)

    @Query("DELETE FROM essentials WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface TabDao {
    @Query("SELECT * FROM tabs WHERE isPrivate = 0 ORDER BY isPinned DESC, sortOrder ASC, lastAccessedAt DESC")
    fun observeNormalTabs(): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs WHERE isPrivate = 0 ORDER BY isPinned DESC, sortOrder ASC, lastAccessedAt DESC")
    suspend fun getNormalTabs(): List<TabEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tab: TabEntity)

    @Query("DELETE FROM tabs WHERE id = :tabId")
    suspend fun deleteById(tabId: String)

    @Query("DELETE FROM tabs WHERE spaceId = :spaceId AND isPrivate = 0")
    suspend fun deleteBySpace(spaceId: String)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): BookmarkEntity?

    @Query(
        "SELECT * FROM bookmarks WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY createdAt DESC LIMIT :limit"
    )
    suspend fun search(query: String, limit: Int = 6): List<BookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY lastVisitedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<HistoryEntity>>

    @Query(
        "SELECT * FROM history WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY visitCount DESC, lastVisitedAt DESC LIMIT :limit"
    )
    suspend fun search(query: String, limit: Int = 8): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: HistoryEntity)

    @Query("DELETE FROM history WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}

@Dao
interface SiteOverrideDao {
    @Query("SELECT * FROM site_overrides ORDER BY host ASC")
    fun observeAll(): Flow<List<SiteOverrideEntity>>

    @Query("SELECT * FROM site_overrides WHERE host = :host LIMIT 1")
    suspend fun getByHost(host: String): SiteOverrideEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(override: SiteOverrideEntity)

    @Query("DELETE FROM site_overrides WHERE host = :host")
    suspend fun deleteByHost(host: String)

    @Query("DELETE FROM site_overrides")
    suspend fun clearAll()
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
