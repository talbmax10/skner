package com.talbmax.skner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talbmax.skner.AppState
import com.talbmax.skner.currentYear
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    state: AppState,
    onScan: () -> Unit,
    onOpenDoc: (String) -> Unit,
    onCollage: () -> Unit,
    onOpenMerged: (String) -> Unit
) {
    val docs by state.docs.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf("الكل") }
    var mergeMode by remember { mutableStateOf(false) }
    val selectedDocs = remember { mutableStateListOf<String>() }
    var status by remember { mutableStateOf("") }
    // P0-9: doc ids whose indexed page OCR matches the query.
    var ocrMatches by remember { mutableStateOf<Set<String>>(emptySet()) }
    val folders = listOf("الكل", "هويات", "فواتير", "عقود")

    androidx.compose.runtime.LaunchedEffect(query) {
        ocrMatches = if (query.isBlank()) emptySet()
        else state.searchPages(query).map { it.docId }.toSet()
    }

    val filtered = docs.filter { d ->
        (folder == "الكل" || d.doc.folder == folder) &&
            (query.isBlank() || d.doc.title.contains(query, true) ||
                d.doc.tags.contains(query, true) || d.doc.ocrText.contains(query, true) ||
                d.doc.id in ocrMatches)
    }
    val fmt = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("سكنر", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("محلي بالكامل • بدون حساب", fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("ابحث بالاسم أو بمحتوى المستند (OCR)…") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            folders.forEach { f ->
                androidx.compose.material3.FilterChip(
                    selected = folder == f,
                    onClick = { folder = f },
                    label = { Text(f) }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.doc.id }) { d ->
                Card(modifier = Modifier.fillMaxWidth().clickable {
                    if (!mergeMode) onOpenDoc(d.doc.id)
                }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (mergeMode) {
                            Checkbox(
                                checked = d.doc.id in selectedDocs,
                                onCheckedChange = { checked ->
                                    if (checked) selectedDocs.add(d.doc.id)
                                    else selectedDocs.remove(d.doc.id)
                                }
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(d.doc.title, fontWeight = FontWeight.Bold)
                            Text(
                                "${d.pages.size} صفحات • ${fmt.format(Date(d.doc.createdAt))}",
                                fontSize = 12.sp
                            )
                            if (d.doc.tags.isNotBlank()) Text("وسوم: ${d.doc.tags}", fontSize = 12.sp)
                            if (d.doc.id in ocrMatches && query.isNotBlank()) {
                                Text("✓ مطابقة في نص صفحة (OCR)", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            if (filtered.isEmpty()) {
                item { Text("لا توجد مستندات بعد — اضغط «مسح مستند جديد».") }
            }
        }
        // P0-4: merge selected docs.
        if (mergeMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = selectedDocs.size >= 2,
                    onClick = {
                        scope.launch {
                            val titles = docs.filter { it.doc.id in selectedDocs }
                                .joinToString(" + ") { it.doc.title }
                            val newId = state.mergeDocs(selectedDocs.toList(), titles)
                            selectedDocs.clear()
                            mergeMode = false
                            status = "تم الدمج في مستند جديد."
                            onOpenMerged(newId)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("دمج المحدد (${selectedDocs.size})") }
                OutlinedButton(onClick = { mergeMode = false; selectedDocs.clear() }) { Text("إلغاء") }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onScan, modifier = Modifier.weight(1f)) { Text("＋  مسح مستند جديد") }
            Button(onClick = onCollage) { Text("تلصيق الصور") }
            OutlinedButton(onClick = { mergeMode = !mergeMode }) {
                Text(if (mergeMode) "إنهاء الدمج" else "دمج مستندات")
            }
        }
        if (status.isNotBlank()) Text(status)
        Spacer(Modifier.height(10.dp))
        // Copyright footer — Home screen ONLY, year is dynamic.
        Text(
            "© جميع الحقوق محفوظة — طالب المولى ${currentYear()}",
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
