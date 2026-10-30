package com.vaultos.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import android.content.Context
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "items",
    indices = [Index("sha256"), Index("category"), Index("createdAt"), Index("originalUri"), Index("archivedPath")]
)
data class VaultItem(
    @PrimaryKey val id: String,
    val name: String,
    val mimeType: String,
    val category: String,
    val source: String,
    val originalUri: String?,
    val archivedPath: String?,
    val sizeBytes: Long,
    val sha256: String?,
    val createdAt: Long,
    val modifiedAt: Long,
    val isArchived: Boolean,
    val isPinned: Boolean = false,
    /** A small preview only. The complete body is always kept in archivedPath. */
    val textContent: String? = null
)

@Entity(tableName = "search_chunks", indices = [Index("itemId")])
data class SearchChunk(
    @PrimaryKey val id: String,
    val itemId: String,
    val chunkIndex: Int,
    val content: String
)

@Dao
interface VaultDao {
    @Query("SELECT * FROM items ORDER BY isPinned DESC, createdAt DESC")
    fun observeAll(): Flow<List<VaultItem>>

    @Query("SELECT * FROM items WHERE category = :category ORDER BY createdAt DESC")
    fun observeCategory(category: String): Flow<List<VaultItem>>

    @Transaction
    @Query("""
        SELECT DISTINCT items.* FROM items
        LEFT JOIN search_chunks ON search_chunks.itemId = items.id
        WHERE items.name LIKE '%' || :query || '%'
           OR items.textContent LIKE '%' || :query || '%'
           OR items.category LIKE '%' || :query || '%'
           OR search_chunks.content LIKE '%' || :query || '%'
        ORDER BY items.isPinned DESC, items.createdAt DESC
    """)
    fun search(query: String): Flow<List<VaultItem>>

    @Query("SELECT * FROM items WHERE sha256 = :hash LIMIT 1")
    suspend fun findHash(hash: String): VaultItem?

    @Query("SELECT * FROM items WHERE originalUri = :uri LIMIT 1")
    suspend fun findByOriginalUri(uri: String): VaultItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: VaultItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChunks(chunks: List<SearchChunk>)

    @Query("DELETE FROM search_chunks WHERE itemId = :itemId")
    suspend fun deleteChunks(itemId: String)

    @Query("UPDATE items SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM items")
    fun count(): Flow<Int>

    @Query("SELECT COALESCE(SUM(sizeBytes),0) FROM items WHERE isArchived = 1")
    fun archivedBytes(): Flow<Long>

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VaultItem?

    @Query("SELECT * FROM items ORDER BY createdAt DESC")
    suspend fun allNow(): List<VaultItem>

    @Query("SELECT * FROM items WHERE category IN ('Text','Code') ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestTextItem(): VaultItem?
}

@Database(entities = [VaultItem::class, SearchChunk::class], version = 1, exportSchema = false)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun dao(): VaultDao

    companion object {
        @Volatile private var INSTANCE: VaultDatabase? = null

        fun get(context: Context): VaultDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                VaultDatabase::class.java,
                "vaultos.db"
            ).build().also { INSTANCE = it }
        }
    }
}
