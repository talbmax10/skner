package com.talbmax.skner.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.talbmax.skner.AppState
import com.talbmax.skner.util.ImageTools
import com.talbmax.skner.util.Ocr
import com.talbmax.skner.util.PdfExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun DocScreen(
    state: AppState,
    docId: String,
    onBack: () -> Unit,
    onAddPage: () -> Unit,
    onCollage: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val docs by state.docs.collectAsState()
    val entry = docs.firstOrNull { it.doc.id == docId }
    var status by remember { mutableStateOf("") }
    var ocrText by remember { mutableStateOf("") }
    var watermark by remember { mutableStateOf("") }
    var signature by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }
    var unlocked by remember { mutableStateOf(false) }
    var titleEdit by remember { mutableStateOf("") }

    if (entry == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("المستند غير موجود.")
            Button(onClick = onBack) { Text("رجوع") }
        }
        return
    }
    val doc = entry.doc
    // PIN lock: if a PIN is set and not yet unlocked this session, ask for it.
    if (doc.pin.isNotBlank() && !unlocked) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("هذا المستند مقفل برمز.", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = pinInput, onValueChange = { pinInput = it }, label = { Text("أدخل الرمز") })
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                if (pinInput == doc.pin) unlocked = true else status = "رمز خاطئ."
            }) { Text("فتح") }
            OutlinedButton(onClick = onBack) { Text("رجوع") }
            if (status.isNotBlank()) Text(status)
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(doc.title, fontWeight = FontWeight.Bold)
        Text("${entry.pages.size} صفحات • مجلد: ${doc.folder}")
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entry.pages, key = { it.id }) { page ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val bmp = remember(page.filePath) { ImageTools.load(page.filePath, 400) }
                        bmp?.let {
                            Image(bitmap = it.asImageBitmap(), contentDescription = "صفحة",
                                modifier = Modifier.size(64, 84))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("صفحة ${page.order + 1} • فلتر: ${page.filter}")
                            OutlinedButton(onClick = {
                                scope.launch { state.deletePage(page.id, docId) }
                            }) { Text("حذف الصفحة") }
                        }
                    }
                }
            }
        }
        OutlinedTextField(value = titleEdit, onValueChange = { titleEdit = it },
            label = { Text("إعادة تسمية (اكتب الاسم الجديد ثم اضغط تطبيق)") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (titleEdit.isNotBlank()) scope.launch { state.updateDoc(doc.copy(title = titleEdit)) }
            }) { Text("تطبيق الاسم") }
            Button(onClick = onAddPage) { Text("＋ إضافة صفحة") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = watermark, onValueChange = { watermark = it },
            label = { Text("علامة مائية (اختياري)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = signature, onValueChange = { signature = it },
            label = { Text("توقيع — اكتب اسمك (اختياري)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch(Dispatchers.IO) {
                    val bmps = entry.pages.mapNotNull { ImageTools.load(it.filePath) }
                        .map { ImageTools.withWatermark(it, watermark, signature) }
                    if (bmps.isEmpty()) { withContext(Dispatchers.Main) { status = "لا توجد صفحات للتصدير." }; return@launch }
                    val file = PdfExport.exportBitmaps(context, bmps, "${doc.title}.pdf", "")
                    val uri = FileProvider.getUriForFile(context, "com.talbmax.skner.fileprovider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    withContext(Dispatchers.Main) {
                        status = "تم تصدير PDF: ${file.absolutePath}"
                        context.startActivity(Intent.createChooser(intent, "مشاركة PDF"))
                    }
                }
            }) { Text("تصدير PDF") }
            Button(onClick = {
                scope.launch(Dispatchers.IO) {
                    val first = entry.pages.firstOrNull()?.let { ImageTools.load(it.filePath) }
                    if (first == null) { withContext(Dispatchers.Main) { status = "لا توجد صفحات." }; return@launch }
                    val text = Ocr.recognise(context, first)
                    withContext(Dispatchers.Main) {
                        ocrText = text.ifBlank { "لم يتم استخراج نص (تأكد من وضوح الصورة)." }
                        scope.launch { state.setOcr(docId, text) }
                        status = "تم استخراج النص من الصفحة الأولى."
                    }
                }
            }) { Text("استخراج النص OCR") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCollage) { Text("تلصيق الصور") }
            OutlinedButton(onClick = {
                scope.launch {
                    val newPin = if (doc.pin.isBlank()) "1234" else ""
                    state.updateDoc(doc.copy(pin = newPin))
                    status = if (newPin.isBlank()) "تم إلغاء القفل." else "تم القفل بالرمز 1234 (غيّره من الإعدادات لاحقاً)."
                }
            }) { Text(if (doc.pin.isBlank()) "قفل برمز" else "إلغاء القفل") }
            OutlinedButton(onClick = onBack) { Text("رجوع") }
        }
        if (ocrText.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("النص المستخرج:", fontWeight = FontWeight.Bold)
            Text(ocrText)
        }
        if (status.isNotBlank()) Text(status)
    }
}
