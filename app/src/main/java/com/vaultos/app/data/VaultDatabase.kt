package com.vaultos.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    val textContent: String? = null,
    val isTrashed: Boolean = false,
    val trashedAt: Long? = null,
    val trashOriginalPath: String? = null
)

@Entity(tableName = "search_chunks", indices = [Index("itemId")])
data class SearchChunk(
    @PrimaryKey val id: String,
    val itemId: String,
    val chunkIndex: Int,
    val content: String
)


@Entity(tableName = "user_tags", indices = [Index(value = ["normalizedName"], unique = true)])
data class UserTag(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val createdAt: Long
)

@Entity(
    tableName = "item_tag_cross_ref",
    primaryKeys = ["itemId", "tagId"],
    indices = [Index("tagId")],
    foreignKeys = [
        ForeignKey(entity = VaultItem::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = UserTag::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class ItemTagCrossRef(val itemId: String, val tagId: String, val addedAt: Long)

@Entity(tableName = "user_collections", indices = [Index(value = ["normalizedName"], unique = true)])
data class UserCollection(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "collection_item_cross_ref",
    primaryKeys = ["collectionId", "itemId"],
    indices = [Index("itemId")],
    foreignKeys = [
        ForeignKey(entity = UserCollection::class, parentColumns = ["id"], childColumns = ["collectionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = VaultItem::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class CollectionItemCrossRef(val collectionId: String, val itemId: String, val addedAt: Long)

@Entity(tableName = "saved_searches", indices = [Index(value = ["normalizedName"], unique = true)])
data class SavedSearch(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val query: String,
    val createdAt: Long
)

@Entity(
    tableName = "item_metadata",
    indices = [Index("packageName")],
    foreignKeys = [ForeignKey(entity = VaultItem::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)]
)
data class ItemMetadata(
    @PrimaryKey val itemId: String,
    val title: String? = null,
    val author: String? = null,
    val subject: String? = null,
    val pageCount: Int? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val durationMs: Long? = null,
    val capturedAt: String? = null,
    val cameraModel: String? = null,
    val packageName: String? = null,
    val packageVersionName: String? = null,
    val packageVersionCode: Long? = null,
    val extractedAt: Long
)

@Entity(
    tableName = "clipboard_history",
    indices = [Index("capturedAt"), Index("contentHash"), Index("foregroundPackage"), Index("isPinned")]
)
data class ClipboardHistoryEntry(
    @PrimaryKey val id: String,
    val itemId: String?,
    val contentType: String,
    val preview: String,
    val foregroundPackage: String?,
    val clipLabel: String?,
    val mimeTypes: String,
    val contentHash: String?,
    val characterCount: Int,
    val byteCount: Long,
    val capturedAt: Long,
    val isPinned: Boolean = false
)

data class DuplicateGroup(val hash: String, val itemCount: Int, val totalBytes: Long)

@Dao
interface VaultDao {
    @Query("SELECT * FROM items WHERE isTrashed = 0 ORDER BY isPinned DESC, createdAt DESC")
    fun observeAll(): Flow<List<VaultItem>>

    @Query("SELECT * FROM items WHERE isTrashed = 1 GROUP BY archivedPath ORDER BY trashedAt DESC")
    fun observeTrash(): Flow<List<VaultItem>>

    @Query("SELECT * FROM items WHERE category = :category AND isTrashed = 0 ORDER BY createdAt DESC")
    fun observeCategory(category: String): Flow<List<VaultItem>>

    @Transaction
    @Query("""
        SELECT DISTINCT items.* FROM items
        LEFT JOIN search_chunks ON search_chunks.itemId = items.id
        LEFT JOIN item_metadata ON item_metadata.itemId = items.id
        LEFT JOIN item_tag_cross_ref ON item_tag_cross_ref.itemId = items.id
        LEFT JOIN user_tags ON user_tags.id = item_tag_cross_ref.tagId
        WHERE items.isTrashed = 0 AND (
              items.name LIKE '%' || :query || '%'
           OR items.textContent LIKE '%' || :query || '%'
           OR items.category LIKE '%' || :query || '%'
           OR item_metadata.title LIKE '%' || :query || '%'
           OR item_metadata.author LIKE '%' || :query || '%'
           OR item_metadata.subject LIKE '%' || :query || '%'
           OR user_tags.name LIKE '%' || :query || '%'
           OR search_chunks.content LIKE '%' || :query || '%'
        )
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

    @Query("SELECT * FROM items WHERE archivedPath = :path")
    suspend fun findByArchivedPath(path: String): List<VaultItem>

    @Query("UPDATE items SET isTrashed = 1, trashedAt = :trashedAt, archivedPath = :trashedPath, trashOriginalPath = :originalPath WHERE archivedPath = :originalPath")
    suspend fun markPathTrashed(originalPath: String, trashedPath: String, trashedAt: Long)

    @Query("UPDATE items SET isTrashed = 0, trashedAt = NULL, archivedPath = :restoredPath, trashOriginalPath = NULL WHERE archivedPath = :trashedPath AND isTrashed = 1")
    suspend fun restorePath(trashedPath: String, restoredPath: String)

    @Query("SELECT COUNT(*) FROM items WHERE isTrashed = 1")
    fun trashCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM items WHERE isTrashed = 0")
    fun count(): Flow<Int>

    @Query("SELECT COALESCE(SUM(sizeBytes),0) FROM items WHERE isArchived = 1 AND isTrashed = 0")
    fun archivedBytes(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: UserTag)

    @Query("SELECT * FROM user_tags WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun findTag(normalizedName: String): UserTag?

    @Query("SELECT * FROM user_tags ORDER BY name COLLATE NOCASE")
    fun observeTags(): Flow<List<UserTag>>

    @Query("SELECT user_tags.* FROM user_tags INNER JOIN item_tag_cross_ref ON item_tag_cross_ref.tagId = user_tags.id WHERE item_tag_cross_ref.itemId = :itemId ORDER BY user_tags.name COLLATE NOCASE")
    fun observeTagsForItem(itemId: String): Flow<List<UserTag>>

    @Query("SELECT * FROM user_tags ORDER BY name COLLATE NOCASE")
    suspend fun allTagsNow(): List<UserTag>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addItemTag(reference: ItemTagCrossRef)

    @Query("DELETE FROM item_tag_cross_ref WHERE itemId = :itemId AND tagId = :tagId")
    suspend fun removeItemTag(itemId: String, tagId: String)

    @Query("DELETE FROM user_tags WHERE id = :tagId")
    suspend fun deleteTag(tagId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCollection(collection: UserCollection)

    @Query("SELECT * FROM user_collections WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun findCollection(normalizedName: String): UserCollection?

    @Query("SELECT * FROM user_collections ORDER BY name COLLATE NOCASE")
    fun observeCollections(): Flow<List<UserCollection>>

    @Query("SELECT user_collections.* FROM user_collections INNER JOIN collection_item_cross_ref ON collection_item_cross_ref.collectionId = user_collections.id WHERE collection_item_cross_ref.itemId = :itemId ORDER BY user_collections.name COLLATE NOCASE")
    fun observeCollectionsForItem(itemId: String): Flow<List<UserCollection>>

    @Query("SELECT items.* FROM items INNER JOIN collection_item_cross_ref ON collection_item_cross_ref.itemId = items.id WHERE collection_item_cross_ref.collectionId = :collectionId AND items.isTrashed = 0 ORDER BY items.isPinned DESC, items.createdAt DESC")
    fun observeCollectionItems(collectionId: String): Flow<List<VaultItem>>

    @Query("SELECT * FROM user_collections ORDER BY name COLLATE NOCASE")
    suspend fun allCollectionsNow(): List<UserCollection>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCollectionItem(reference: CollectionItemCrossRef)

    @Query("DELETE FROM collection_item_cross_ref WHERE collectionId = :collectionId AND itemId = :itemId")
    suspend fun removeCollectionItem(collectionId: String, itemId: String)

    @Query("DELETE FROM user_collections WHERE id = :collectionId")
    suspend fun deleteCollection(collectionId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSavedSearch(search: SavedSearch)

    @Query("SELECT * FROM saved_searches ORDER BY createdAt DESC")
    fun observeSavedSearches(): Flow<List<SavedSearch>>

    @Query("SELECT * FROM saved_searches ORDER BY createdAt DESC")
    suspend fun allSavedSearchesNow(): List<SavedSearch>

    @Query("DELETE FROM saved_searches WHERE id = :searchId")
    suspend fun deleteSavedSearch(searchId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadata(metadata: ItemMetadata)

    @Query("SELECT * FROM item_metadata WHERE itemId = :itemId LIMIT 1")
    fun observeMetadata(itemId: String): Flow<ItemMetadata?>

    @Query("SELECT * FROM item_metadata")
    suspend fun allMetadataNow(): List<ItemMetadata>

    @Query("SELECT sha256 AS hash, COUNT(*) AS itemCount, SUM(sizeBytes) AS totalBytes FROM items WHERE isTrashed = 0 AND sha256 IS NOT NULL AND sha256 != '' GROUP BY sha256 HAVING COUNT(*) > 1 ORDER BY itemCount DESC, totalBytes DESC")
    fun observeDuplicateGroups(): Flow<List<DuplicateGroup>>

    @Query("SELECT * FROM items WHERE isTrashed = 0 AND sha256 = :hash ORDER BY name COLLATE NOCASE")
    fun observeDuplicateItems(hash: String): Flow<List<VaultItem>>

    @Query("""
        SELECT DISTINCT related.* FROM items AS related
        WHERE related.id != :itemId AND related.isTrashed = 0 AND (
            related.id IN (
                SELECT candidate.itemId FROM item_tag_cross_ref AS candidate
                INNER JOIN item_tag_cross_ref AS current ON current.tagId = candidate.tagId
                WHERE current.itemId = :itemId
            )
            OR related.id IN (
                SELECT candidate.itemId FROM collection_item_cross_ref AS candidate
                INNER JOIN collection_item_cross_ref AS current ON current.collectionId = candidate.collectionId
                WHERE current.itemId = :itemId
            )
            OR related.category = (SELECT category FROM items WHERE id = :itemId)
        )
        ORDER BY
            CASE
                WHEN related.id IN (SELECT candidate.itemId FROM item_tag_cross_ref AS candidate INNER JOIN item_tag_cross_ref AS current ON current.tagId = candidate.tagId WHERE current.itemId = :itemId) THEN 0
                WHEN related.id IN (SELECT candidate.itemId FROM collection_item_cross_ref AS candidate INNER JOIN collection_item_cross_ref AS current ON current.collectionId = candidate.collectionId WHERE current.itemId = :itemId) THEN 1
                ELSE 2
            END,
            related.isPinned DESC, related.createdAt DESC
        LIMIT 8
    """)
    fun observeRelatedItems(itemId: String): Flow<List<VaultItem>>

    @Query("SELECT * FROM item_tag_cross_ref")
    suspend fun allItemTagsNow(): List<ItemTagCrossRef>

    @Query("SELECT * FROM collection_item_cross_ref")
    suspend fun allCollectionItemsNow(): List<CollectionItemCrossRef>

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VaultItem?

    @Query("SELECT * FROM items ORDER BY createdAt DESC")
    suspend fun allNow(): List<VaultItem>

    @Query("SELECT * FROM clipboard_history ORDER BY isPinned DESC, capturedAt DESC")
    fun observeClipboardHistory(): Flow<List<ClipboardHistoryEntry>>

    @Query("SELECT DISTINCT clipboard_history.* FROM clipboard_history LEFT JOIN items ON items.id = clipboard_history.itemId LEFT JOIN search_chunks ON search_chunks.itemId = items.id WHERE clipboard_history.preview LIKE '%' || :query || '%' OR items.textContent LIKE '%' || :query || '%' OR search_chunks.content LIKE '%' || :query || '%' OR clipboard_history.foregroundPackage LIKE '%' || :query || '%' OR clipboard_history.clipLabel LIKE '%' || :query || '%' OR clipboard_history.mimeTypes LIKE '%' || :query || '%' ORDER BY clipboard_history.isPinned DESC, clipboard_history.capturedAt DESC")
    fun searchClipboardHistory(query: String): Flow<List<ClipboardHistoryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClipboardHistory(entry: ClipboardHistoryEntry)

    @Query("UPDATE clipboard_history SET isPinned = :pinned WHERE id = :id")
    suspend fun setClipboardPinned(id: String, pinned: Boolean)

    @Query("DELETE FROM clipboard_history WHERE id = :id")
    suspend fun deleteClipboardHistory(id: String)

    @Query("DELETE FROM clipboard_history WHERE isPinned = 0")
    suspend fun clearUnpinnedClipboardHistory()

    @Query("SELECT COUNT(*) FROM clipboard_history")
    fun clipboardHistoryCount(): Flow<Int>

    @Query("SELECT * FROM clipboard_history WHERE contentType = 'text' ORDER BY capturedAt DESC LIMIT 1")
    suspend fun latestClipboardText(): ClipboardHistoryEntry?

    @Query("SELECT * FROM items WHERE isTrashed = 0 AND category IN ('Text','Code') ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestTextItem(): VaultItem?
}

@Database(entities = [VaultItem::class, SearchChunk::class, UserTag::class, ItemTagCrossRef::class, UserCollection::class, CollectionItemCrossRef::class, SavedSearch::class, ItemMetadata::class, ClipboardHistoryEntry::class], version = 4, exportSchema = false)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun dao(): VaultDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN isTrashed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE items ADD COLUMN trashedAt INTEGER")
                db.execSQL("ALTER TABLE items ADD COLUMN trashOriginalPath TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `user_tags` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `normalizedName` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_tags_normalizedName` ON `user_tags` (`normalizedName`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `item_tag_cross_ref` (`itemId` TEXT NOT NULL, `tagId` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`itemId`, `tagId`), FOREIGN KEY(`itemId`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`tagId`) REFERENCES `user_tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_item_tag_cross_ref_tagId` ON `item_tag_cross_ref` (`tagId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `user_collections` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `normalizedName` TEXT NOT NULL, `description` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_collections_normalizedName` ON `user_collections` (`normalizedName`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `collection_item_cross_ref` (`collectionId` TEXT NOT NULL, `itemId` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`collectionId`, `itemId`), FOREIGN KEY(`collectionId`) REFERENCES `user_collections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`itemId`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_collection_item_cross_ref_itemId` ON `collection_item_cross_ref` (`itemId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `saved_searches` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `normalizedName` TEXT NOT NULL, `query` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_searches_normalizedName` ON `saved_searches` (`normalizedName`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `item_metadata` (`itemId` TEXT NOT NULL, `title` TEXT, `author` TEXT, `subject` TEXT, `pageCount` INTEGER, `widthPx` INTEGER, `heightPx` INTEGER, `durationMs` INTEGER, `capturedAt` TEXT, `cameraModel` TEXT, `packageName` TEXT, `packageVersionName` TEXT, `packageVersionCode` INTEGER, `extractedAt` INTEGER NOT NULL, PRIMARY KEY(`itemId`), FOREIGN KEY(`itemId`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_item_metadata_packageName` ON `item_metadata` (`packageName`)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS clipboard_history (id TEXT NOT NULL PRIMARY KEY, itemId TEXT, contentType TEXT NOT NULL, preview TEXT NOT NULL, foregroundPackage TEXT, clipLabel TEXT, mimeTypes TEXT NOT NULL, contentHash TEXT, characterCount INTEGER NOT NULL, byteCount INTEGER NOT NULL, capturedAt INTEGER NOT NULL, isPinned INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_clipboard_history_capturedAt ON clipboard_history(capturedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_clipboard_history_contentHash ON clipboard_history(contentHash)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_clipboard_history_foregroundPackage ON clipboard_history(foregroundPackage)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_clipboard_history_isPinned ON clipboard_history(isPinned)")
            }
        }

        @Volatile private var INSTANCE: VaultDatabase? = null

        fun get(context: Context): VaultDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                VaultDatabase::class.java,
                "vaultos.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { INSTANCE = it }
        }
    }
}
