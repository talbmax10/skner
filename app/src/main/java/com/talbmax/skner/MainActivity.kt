package com.talbmax.skner

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.room.Room
import com.talbmax.skner.data.AppDatabase
import com.talbmax.skner.data.DocEntity
import com.talbmax.skner.data.MIGRATION_1_2
import com.talbmax.skner.data.PageEntity
import com.talbmax.skner.data.PageSignatureEntity
import com.talbmax.skner.data.SignatureEntity
import com.talbmax.skner.ui.CameraScreen
import com.talbmax.skner.ui.CollageScreen
import com.talbmax.skner.ui.CropScreen
import com.talbmax.skner.ui.DocScreen
import com.talbmax.skner.ui.HomeScreen
import com.talbmax.skner.ui.SignatureScreen
import com.talbmax.skner.ui.SkenrTheme
import com.talbmax.skner.util.ImageTools
import com.talbmax.skner.util.Ocr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

sealed class Screen {
    object Home : Screen()
    data class Camera(val docId: String?, val mode: String) : Screen()
    data class Crop(val imagePath: String, val docId: String?) : Screen()
    data class Doc(val docId: String) : Screen()
    object Collage : Screen()
    data class Signature(val docId: String?) : Screen()
}

data class DocWithPages(val doc: DocEntity, val pages: List<PageEntity>)

class AppState(val db: AppDatabase, val scope: CoroutineScope) {
    private val _docs = MutableStateFlow<List<DocWithPages>>(emptyList())
    val docs: StateFlow<List<DocWithPages>> = _docs.asStateFlow()

    suspend fun refresh() {
        val list = db.docDao().allDocs().first()
        _docs.value = list.map { d -> DocWithPages(d, db.docDao().pagesOfOnce(d.id)) }
    }

    fun refreshAsync() = scope.launch { refresh() }

    suspend fun createDoc(title: String, folder: String): String {
        val id = UUID.randomUUID().toString()
        db.docDao().upsert(
            DocEntity(id, title, folder, "", "", System.currentTimeMillis(), "", "")
        )
        refresh()
        return id
    }

    suspend fun addPage(docId: String, path: String, filter: String): String {
        val pages = db.docDao().pagesOfOnce(docId)
        val id = UUID.randomUUID().toString()
        db.docDao().upsertPage(PageEntity(id, docId, path, pages.size, filter))
        refresh()
        return id
    }

    suspend fun deleteDoc(docId: String) {
        db.docDao().deletePagesOf(docId)
        db.docDao().deleteDoc(docId)
        refresh()
    }

    suspend fun deletePage(pageId: String, docId: String) {
        db.docDao().deletePage(pageId)
        db.docDao().deletePageSignaturesOf(pageId)
        renumber(docId)
        refresh()
    }

    suspend fun updateDoc(doc: DocEntity) {
        db.docDao().upsert(doc)
        refresh()
    }

    suspend fun setOcr(docId: String, text: String) {
        val d = db.docDao().docById(docId) ?: return
        db.docDao().upsert(d.copy(ocrText = text))
        refresh()
    }

    /** P0-6: move a page up (-1) or down (+1) and persist the new order. */
    suspend fun movePage(docId: String, pageId: String, direction: Int) {
        val pages = db.docDao().pagesOfOnce(docId)
        val idx = pages.indexOfFirst { it.id == pageId }
        val swap = idx + direction
        if (idx < 0 || swap < 0 || swap >= pages.size) return
        val a = pages[idx]; val b = pages[swap]
        db.docDao().setPageOrder(a.id, b.order)
        db.docDao().setPageOrder(b.id, a.order)
        refresh()
    }

    private suspend fun renumber(docId: String) {
        val pages = db.docDao().pagesOfOnce(docId)
        pages.forEachIndexed { i, p ->
            if (p.order != i) db.docDao().setPageOrder(p.id, i)
        }
    }

    /** P0-4: merge several docs into one new doc (max 500 pages). */
    suspend fun mergeDocs(docIds: List<String>, title: String): String {
        val newId = createDoc(title, "الكل")
        var order = 0
        for (docId in docIds) {
            val pages = db.docDao().pagesOfOnce(docId)
            for (p in pages) {
                if (order >= 500) break
                db.docDao().upsertPage(
                    PageEntity(UUID.randomUUID().toString(), newId, p.filePath, order++, p.filter)
                )
            }
            if (order >= 500) break
        }
        refresh()
        return newId
    }

    /** P0-5: extract selected pages into a new doc; optionally delete from the original. */
    suspend fun extractPages(
        docId: String,
        pageIds: List<String>,
        newTitle: String,
        deleteFromOriginal: Boolean
    ): String {
        val pages = db.docDao().pagesOfOnce(docId).filter { it.id in pageIds }
        val newId = createDoc(newTitle, "الكل")
        pages.forEachIndexed { i, p ->
            db.docDao().upsertPage(
                PageEntity(UUID.randomUUID().toString(), newId, p.filePath, i, p.filter)
            )
        }
        if (deleteFromOriginal) {
            for (p in pages) {
                db.docDao().deletePage(p.id)
                db.docDao().deletePageSignaturesOf(p.id)
            }
            renumber(docId)
        }
        refresh()
        return newId
    }

    /** P0-3: batch OCR for every page of a doc that is not indexed yet. */
    suspend fun runBatchOcr(
        context: Context,
        docId: String,
        onProgress: (done: Int, total: Int) -> Unit
    ) {
        val pages = db.docDao().pagesOfOnce(docId)
        var done = 0
        val texts = mutableListOf<String>()
        for (p in pages) {
            if (!p.ocrDone) {
                val bmp: Bitmap? = ImageTools.load(p.filePath)
                val text = if (bmp != null) Ocr.recognise(context, bmp) else ""
                db.docDao().setPageOcr(p.id, text)
            }
            val fresh = db.docDao().pagesOfOnce(docId).firstOrNull { it.id == p.id }
            if (!fresh?.ocrText.isNullOrBlank()) texts.add(fresh!!.ocrText)
            done++
            onProgress(done, pages.size)
        }
        val d = db.docDao().docById(docId)
        if (d != null) db.docDao().upsert(d.copy(ocrText = texts.joinToString("\n")))
        refresh()
    }

    /** P0-9: find pages whose indexed OCR text matches the query. */
    suspend fun searchPages(q: String): List<PageEntity> =
        if (q.isBlank()) emptyList() else db.docDao().searchPages(q)

    // ---- Signatures (P0-8) ----
    suspend fun signaturesOnce() = db.docDao().signaturesOnce()

    suspend fun saveSignature(name: String, filePath: String): String {
        val id = UUID.randomUUID().toString()
        db.docDao().upsertSignature(SignatureEntity(id, name, filePath, System.currentTimeMillis()))
        return id
    }

    suspend fun deleteSignature(id: String) {
        db.docDao().deleteSignature(id)
    }

    suspend fun pageSignaturesOnce(pageId: String) = db.docDao().pageSignaturesOnce(pageId)

    suspend fun placeSignature(pageId: String, signatureId: String, x: Float, y: Float, scale: Float) {
        val id = UUID.randomUUID().toString()
        db.docDao().upsertPageSignature(PageSignatureEntity(id, pageId, signatureId, x, y, scale))
    }

    suspend fun removePageSignature(id: String) {
        db.docDao().deletePageSignature(id)
    }
}

fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "skner.db")
            .addMigrations(MIGRATION_1_2)
            .build()
        setContent {
            SkenrTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SkenrApp(db)
                }
            }
        }
    }
}

@Composable
fun SkenrApp(db: AppDatabase) {
    val scope = rememberCoroutineScope()
    val state = remember { AppState(db, scope) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    androidx.compose.runtime.LaunchedEffect(Unit) { state.refresh() }

    when (val s = screen) {
        is Screen.Home -> HomeScreen(
            state = state,
            onScan = { screen = Screen.Camera(null, "مستند") },
            onOpenDoc = { screen = Screen.Doc(it) },
            onCollage = { screen = Screen.Collage },
            onOpenMerged = { screen = Screen.Doc(it) }
        )
        is Screen.Camera -> CameraScreen(
            mode = s.mode,
            onCaptured = { path -> screen = Screen.Crop(path, s.docId) },
            onBack = { screen = Screen.Home }
        )
        is Screen.Crop -> CropScreen(
            imagePath = s.imagePath,
            onDone = { path, filter ->
                scope.launch {
                    val docId = s.docId ?: state.createDoc("مستند ${currentYear()}", "الكل")
                    state.addPage(docId, path, filter)
                    screen = Screen.Doc(docId)
                }
            },
            onBack = { screen = Screen.Home }
        )
        is Screen.Doc -> DocScreen(
            state = state,
            docId = s.docId,
            onBack = { screen = Screen.Home },
            onAddPage = { screen = Screen.Camera(s.docId, "مستند") },
            onCollage = { screen = Screen.Collage },
            onDrawSignature = { screen = Screen.Signature(s.docId) }
        )
        is Screen.Collage -> CollageScreen(onBack = { screen = Screen.Home })
        is Screen.Signature -> SignatureScreen(
            state = state,
            docId = s.docId,
            onBack = { screen = if (s.docId != null) Screen.Doc(s.docId) else Screen.Home }
        )
    }
}
