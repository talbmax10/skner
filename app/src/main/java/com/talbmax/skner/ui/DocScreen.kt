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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.talbmax.skner.AppState
import com.talbmax.skner.data.PageSignatureEntity
import com.talbmax.skner.data.SignatureEntity
import com.talbmax.skner.util.ImageTools
import com.talbmax.skner.util.PdfExport
import com.talbmax.skner.util.PrintHelper
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
    onCollage: () -> Unit,
    onDrawSignature: () -> Unit
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
    var ocrProgress by remember { mutableStateOf("") }
    // P0-5: multi-select for extract.
    val selectedPages = remember { mutableStateListOf<String>() }
    var deleteAfterExtract by remember { mutableStateOf(false) }
    var showMergePicker by remember { mutableStateOf(false) }
    var savedSigs by remember { mutableStateOf<List<SignatureEntity>>(emptyList()) }
    var pageSigs by remember { mutableStateOf<Map<String, List<PageSignatureEntity>>>(emptyMap()) }

    androidx.compose.runtime.LaunchedEffect(docId) {
        savedSigs = state.signaturesOnce()
    }

    fun refreshPageSigs() {
        scope.launch(Dispatchers.IO) {
            val map = mutableMapOf<String, List<PageSignatureEntity>>()
            for (p in entry?.pages.orEmpty()) {
                map[p.id] = state.pageSignaturesOnce(p.id)
            }
            withContext(Dispatchers.Main) { pageSigs = map }
        }
    }
    androidx.compose.runtime.LaunchedEffect(entry?.pages?.size) { refreshPageSigs() }

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

    /** Build the export bitmaps for the given pages: watermark + text
     *  signature + drawn signature templates placed on each page. */
    suspend fun buildExportBitmaps(pages: List<com.talbmax.skner.data.PageEntity>): List<Bitmap> =
        withContext(Dispatchers.IO) {
            pages.mapNotNull { page ->
                var bmp = ImageTools.load(page.filePath) ?: return@mapNotNull null
                bmp = ImageTools.withWatermark(bmp, watermark, signature)
                val placements = state.pageSignaturesOnce(page.id)
                if (placements.isNotEmpty()) {
                    val draws = placements.mapNotNull { ps ->
                        val sig = savedSigs.firstOrNull { it.id == ps.signatureId }
                            ?: return@mapNotNull null
                        val sigBmp = ImageTools.load(sig.filePath) ?: return@mapNotNull null
                        ImageTools.SignatureDraw(sigBmp, ps.x, ps.y, ps.scale)
                    }
                    bmp = ImageTools.withSignatures(bmp, draws)
                }
                bmp
            }
        }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(doc.title, fontWeight = FontWeight.Bold)
        Text("${entry.pages.size} صفحات • مجلد: ${doc.folder}")
        if (ocrProgress.isNotBlank()) Text(ocrProgress)
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entry.pages, key = { it.id }) { page ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val bmp = remember(page.filePath) { ImageTools.load(page.filePath, 400) }
                            bmp?.let {
                                Image(
                                    bitmap = it.asImageBitmap(), contentDescription = "صفحة",
                                    modifier = Modifier.size(64.dp, 84.dp)
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text("صفحة ${page.order + 1} • فلتر: ${page.filter}")
                                Text(
                                    if (page.ocrDone) "✓ مفهرسة OCR" else "بلا فهرسة OCR",
                                    fontSize = 12.sp
                                )
                                val sigs = pageSigs[page.id].orEmpty()
                                if (sigs.isNotEmpty()) Text("توقيع مدرج: ${sigs.size}", fontSize = 12.sp)
                            }
                            Checkbox(
                                checked = page.id in selectedPages,
                                onCheckedChange = { checked ->
                                    if (checked) selectedPages.add(page.id)
                                    else selectedPages.remove(page.id)
                                }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // P0-6: reorder.
                            OutlinedButton(onClick = {
                                scope.launch { state.movePage(docId, page.id, -1) }
                            }) { Text("↑") }
                            OutlinedButton(onClick = {
                                scope.launch { state.movePage(docId, page.id, 1) }
                            }) { Text("↓") }
                            OutlinedButton(onClick = {
                                scope.launch { state.deletePage(page.id, docId) }
                            }) { Text("حذف") }
                            // P0-8: insert a saved signature template on this page.
                            if (savedSigs.isNotEmpty()) {
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        val sig = savedSigs.first()
                                        state.placeSignature(page.id, sig.id, 0.55f, 0.82f, 0.30f)
                                        refreshPageSigs()
                                        status = "أُدرج التوقيع «${sig.name}» في الصفحة ${page.order + 1}."
                                    }
                                }) { Text("＋ توقيع") }
                            }
                            val sigs = pageSigs[page.id].orEmpty()
                            if (sigs.isNotEmpty()) {
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        state.removePageSignature(sigs.first().id)
                                        refreshPageSigs()
                                    }
                                }) { Text("✕ توقيع") }
                            }
                        }
                    }
                }
            }
        }
        // P0-5: extract selected pages.
        if (selectedPages.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        val newId = state.extractPages(
                            docId, selectedPages.toList(),
                            "${doc.title} — مستخرج", deleteAfterExtract
                        )
                        selectedPages.clear()
                        status = "تم الاستخراج إلى مستند جديد."
                    }
                }) { Text("استخراج إلى مستند جديد (${selectedPages.size})") }
                Button(onClick = {
                    scope.launch(Dispatchers.IO) {
                        val pages = entry.pages.filter { it.id in selectedPages }
                        val bmps = buildExportBitmaps(pages)
                        if (bmps.isEmpty()) {
                            withContext(Dispatchers.Main) { status = "لا توجد صفحات للتصدير." }
                            return@launch
                        }
                        val file = PdfExport.exportBitmaps(
                            context, bmps, "${doc.title}-extract.pdf", ""
                        )
                        withContext(Dispatchers.Main) {
                            status = "تم تصدير PDF للمحدد: ${file.absolutePath}"
                            selectedPages.clear()
                        }
                    }
                }) { Text("استخراج كـ PDF") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = deleteAfterExtract, onCheckedChange = { deleteAfterExtract = it })
                Text("حذف من الأصل بعد الاستخراج")
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
                    val bmps = buildExportBitmaps(entry.pages)
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
            // P0-3: batch OCR for all pages.
            Button(onClick = {
                scope.launch {
                    ocrProgress = "جارٍ فهرسة OCR… 0/${entry.pages.size}"
                    state.runBatchOcr(context, docId) { done, total ->
                        ocrProgress = "جارٍ فهرسة OCR… $done/$total"
                    }
                    ocrProgress = "اكتملت فهرسة OCR لكل الصفحات."
                    val fresh = state.docs.value.firstOrNull { it.doc.id == docId }
                    ocrText = fresh?.doc?.ocrText ?: ""
                    status = "تم استخراج النص من كل الصفحات وحفظه للبحث."
                }
            }) { Text("فهرسة OCR لكل الصفحات") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCollage) { Text("تلصيق الصور") }
            // P0-7: print.
            OutlinedButton(onClick = {
                scope.launch(Dispatchers.IO) {
                    val bmps = buildExportBitmaps(entry.pages)
                    if (bmps.isEmpty()) { withContext(Dispatchers.Main) { status = "لا توجد صفحات للطباعة." }; return@launch }
                    val file = PdfExport.exportBitmaps(context, bmps, "${doc.title}-print.pdf", "")
                    withContext(Dispatchers.Main) {
                        PrintHelper.printPdf(context, file, doc.title)
                        status = "أُرسل المستند إلى الطابعة."
                    }
                }
            }) { Text("طباعة") }
            OutlinedButton(onClick = onDrawSignature) { Text("رسم توقيع") }
            OutlinedButton(onClick = {
                scope.launch {
                    val newPin = if (doc.pin.isBlank()) "1234" else ""
                    state.updateDoc(doc.copy(pin = newPin))
                    status = if (newPin.isBlank()) "تم إلغاء القفل." else "تم القفل بالرمز 1234 (غيّره من الإعدادات لاحقاً)."
                }
            }) { Text(if (doc.pin.isBlank()) "قفل برمز" else "إلغاء القفل") }
        }
        // P0-4: merge another doc into this one.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showMergePicker = !showMergePicker }) {
                Text(if (showMergePicker) "إخفاء قائمة الدمج" else "دمج مستند آخر هنا")
            }
            OutlinedButton(onClick = onBack) { Text("رجوع") }
        }
        if (showMergePicker) {
            Text("اختر مستنداً لدمجه في هذا المستند:", fontWeight = FontWeight.Bold)
            val others = docs.filter { it.doc.id != docId }
            LazyColumn(modifier = Modifier.height(150.dp)) {
                items(others, key = { it.doc.id }) { other ->
                    Row(Modifier.fillMaxWidth().padding(4.dp)) {
                        Text("${other.doc.title} (${other.pages.size} صفحات)", modifier = Modifier.weight(1f))
                        Button(onClick = {
                            scope.launch {
                                state.mergeDocs(listOf(docId, other.doc.id), "${doc.title} + ${other.doc.title}")
                                showMergePicker = false
                                status = "تم الدمج في مستند جديد (الحد 500 صفحة)."
                            }
                        }) { Text("دمج") }
                    }
                }
            }
        }
        if (ocrText.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("النص المستخرج:", fontWeight = FontWeight.Bold)
            Text(ocrText.take(1500))
        }
        if (status.isNotBlank()) Text(status)
    }
}
