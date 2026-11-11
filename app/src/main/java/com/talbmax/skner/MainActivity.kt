package com.talbmax.skner

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
import com.talbmax.skner.data.PageEntity
import com.talbmax.skner.ui.CameraScreen
import com.talbmax.skner.ui.CollageScreen
import com.talbmax.skner.ui.CropScreen
import com.talbmax.skner.ui.DocScreen
import com.talbmax.skner.ui.HomeScreen
import com.talbmax.skner.ui.SkenrTheme
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
}

fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "skner.db").build()
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
            onCollage = { screen = Screen.Collage }
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
            onCollage = { screen = Screen.Collage }
        )
        is Screen.Collage -> CollageScreen(onBack = { screen = Screen.Home })
    }
}
