package com.talbmax.skner.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.talbmax.skner.util.ImageTools
import com.talbmax.skner.util.PdfExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Collage (تلصيق الصور): pick 2+ images, choose a grid layout, export as one
 * page (PDF or JPG). Free in this app.
 */
@Composable
fun CollageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var cols by remember { mutableStateOf(2) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { picked -> if (picked.isNotEmpty()) uris = uris + picked }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("تلصيق الصور — اجمع عدة صور في صفحة واحدة")
        Spacer(Modifier.height(12.dp))
        Button(onClick = { picker.launch("image/*") }) { Text("اختيار صور من المعرض") }
        Spacer(Modifier.height(8.dp))
        Text("الصور المختارة: ${uris.size} (اختر صورتين على الأقل)")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(uris) { uri ->
                val bmp = remember(uri) {
                    context.contentResolver.openInputStream(uri)?.use {
                        android.graphics.BitmapFactory.decodeStream(it)
                    }
                }
                bmp?.let {
                    Image(bitmap = it.asImageBitmap(), contentDescription = "صورة",
                        modifier = Modifier.size(90, 120))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("التخطيط:")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(2 to "2×2", 1 to "1×2", 3 to "1×3").forEach { (c, label) ->
                FilterChip(selected = cols == c, onClick = { cols = c }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = uris.size >= 2 && !busy,
                onClick = {
                    busy = true
                    scope.launch(Dispatchers.IO) {
                        val bmps = uris.mapNotNull { uri ->
                            context.contentResolver.openInputStream(uri)?.use {
                                android.graphics.BitmapFactory.decodeStream(it)
                            }
                        }
                        if (bmps.size < 2) {
                            withContext(Dispatchers.Main) { status = "اختر صورتين على الأقل."; busy = false }
                            return@launch
                        }
                        val page = ImageTools.collage(bmps, cols)
                        val pdf = PdfExport.exportBitmaps(
                            context, listOf(page), "collage_${System.currentTimeMillis()}.pdf"
                        )
                        val jpg = ImageTools.newImageFile(context, "collage_${System.currentTimeMillis()}.jpg")
                        ImageTools.save(page, jpg)
                        withContext(Dispatchers.Main) {
                            status = "تم التصدير:\nPDF: ${pdf.absolutePath}\nJPG: ${jpg.absolutePath}"
                            busy = false
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text(if (busy) "جارٍ التصدير…" else "تصدير الصفحة ✓") }
            OutlinedButton(onClick = onBack) { Text("رجوع") }
        }
        if (status.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(status)
        }
        Spacer(Modifier.height(8.dp))
        Text("ملاحظة: تلصيق الصور مجاني هنا — كان مدفوعاً في التطبيق الأصلي.",
            modifier = Modifier.fillMaxWidth())
    }
}
