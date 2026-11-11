package com.talbmax.skner.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "docs")
data class DocEntity(
    @PrimaryKey val id: String,
    val title: String,
    val folder: String,
    val tags: String,
    val note: String,
    val createdAt: Long,
    val pin: String,
    val ocrText: String
)

@Entity(tableName = "pages")
data class PageEntity(
    @PrimaryKey val id: String,
    val docId: String,
    val filePath: String,
    val order: Int,
    val filter: String
)

@Dao
interface DocDao {
    @Query("SELECT * FROM docs ORDER BY createdAt DESC")
    fun allDocs(): Flow<List<DocEntity>>

    @Query("SELECT * FROM docs WHERE id = :id LIMIT 1")
    suspend fun docById(id: String): DocEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(doc: DocEntity)

    @Query("DELETE FROM docs WHERE id = :id")
    suspend fun deleteDoc(id: String)

    @Query("SELECT * FROM pages WHERE docId = :docId ORDER BY `order` ASC")
    fun pagesOf(docId: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE docId = :docId ORDER BY `order` ASC")
    suspend fun pagesOfOnce(docId: String): List<PageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPage(page: PageEntity)

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun deletePage(id: String)

    @Query("DELETE FROM pages WHERE docId = :docId")
    suspend fun deletePagesOf(docId: String)
}

@Database(entities = [DocEntity::class, PageEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun docDao(): DocDao
}
