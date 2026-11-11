package com.talbmax.skner.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    val filter: String,
    // P0-3: per-page OCR index (batch OCR stores text here for search).
    val ocrText: String = "",
    val ocrDone: Boolean = false
)

/** A saved hand-drawn signature template (transparent PNG file). P0-8. */
@Entity(tableName = "signatures")
data class SignatureEntity(
    @PrimaryKey val id: String,
    val name: String,
    val filePath: String,
    val createdAt: Long
)

/** Placement of a signature template on one page (fractions of page size). P0-8. */
@Entity(tableName = "page_signatures")
data class PageSignatureEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    val signatureId: String,
    val x: Float,
    val y: Float,
    val scale: Float
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

    @Query("UPDATE pages SET `order` = :newOrder WHERE id = :pageId")
    suspend fun setPageOrder(pageId: String, newOrder: Int)

    @Query("UPDATE pages SET ocrText = :text, ocrDone = 1 WHERE id = :pageId")
    suspend fun setPageOcr(pageId: String, text: String)

    /** P0-9: search indexed page OCR text. */
    @Query("SELECT * FROM pages WHERE ocrText LIKE '%' || :q || '%'")
    suspend fun searchPages(q: String): List<PageEntity>

    @Query("SELECT * FROM signatures ORDER BY createdAt DESC")
    suspend fun signaturesOnce(): List<SignatureEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSignature(sig: SignatureEntity)

    @Query("DELETE FROM signatures WHERE id = :id")
    suspend fun deleteSignature(id: String)

    @Query("SELECT * FROM page_signatures WHERE pageId = :pageId")
    suspend fun pageSignaturesOnce(pageId: String): List<PageSignatureEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPageSignature(ps: PageSignatureEntity)

    @Query("DELETE FROM page_signatures WHERE id = :id")
    suspend fun deletePageSignature(id: String)

    @Query("DELETE FROM page_signatures WHERE pageId = :pageId")
    suspend fun deletePageSignaturesOf(pageId: String)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE pages ADD COLUMN ocrText TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE pages ADD COLUMN ocrDone INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS signatures (" +
                "id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                "filePath TEXT NOT NULL, createdAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS page_signatures (" +
                "id TEXT NOT NULL PRIMARY KEY, pageId TEXT NOT NULL, " +
                "signatureId TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, scale REAL NOT NULL)"
        )
    }
}

@Database(
    entities = [DocEntity::class, PageEntity::class, SignatureEntity::class, PageSignatureEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun docDao(): DocDao
}
