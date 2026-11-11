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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talbmax.skner.AppState
import com.talbmax.skner.currentYear
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    state: AppState,
    onScan: () -> Unit,
    onOpenDoc: (String) -> Unit,
    onCollage: () -> Unit
) {
    val docs by state.docs.collectAsState()
    var query by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf("الكل") }
    val folders = listOf("الكل", "هويات", "فواتير", "عقود")
    val filtered = docs.filter { d ->
        (folder == "الكل" || d.doc.folder == folder) &&
            (query.isBlank() || d.doc.title.contains(query, true) ||
                d.doc.tags.contains(query, true) || d.doc.ocrText.contains(query, true))
    }
    val fmt = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("سكنر", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("محلي بالكامل • بدون حساب", fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("ابحث بالاسم أو بمحتوى المستند…") },
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
                Card(modifier = Modifier.fillMaxWidth().clickable { onOpenDoc(d.doc.id) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(d.doc.title, fontWeight = FontWeight.Bold)
                        Text(
                            "${d.pages.size} صفحات • ${fmt.format(Date(d.doc.createdAt))}",
                            fontSize = 12.sp
                        )
                        if (d.doc.tags.isNotBlank()) Text("وسوم: ${d.doc.tags}", fontSize = 12.sp)
                    }
                }
            }
            if (filtered.isEmpty()) {
                item { Text("لا توجد مستندات بعد — اضغط «مسح مستند جديد».") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onScan, modifier = Modifier.weight(1f)) { Text("＋  مسح مستند جديد") }
            Button(onClick = onCollage) { Text("تلصيق الصور") }
        }
        Spacer(Modifier.height(10.dp))
        // Copyright footer — Home screen ONLY, year is dynamic.
        Text(
            "© جميع الحقوق محفوظة — طالب المولى ${currentYear()}",
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
