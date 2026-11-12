package com.talbmax.skner.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.talbmax.skner.AppState
import com.talbmax.skner.data.SignatureEntity
import com.talbmax.skner.util.ImageTools
import kotlinx.coroutines.launch
import java.io.FileOutputStream

/**
 * P0-8: draw a signature with a finger, save it as a transparent PNG
 * template, and manage saved templates. Placement onto pages happens in
 * the Document screen.
 */
@Composable
fun SignatureScreen(state: AppState, docId: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Each stroke is a list of finger points (kept as data, so saving to PNG
    // needs no Compose-Path conversion).
    val strokes = remember { mutableStateListOf<MutableList<Offset>>() }
    var currentStroke by remember { mutableStateOf<MutableList<Offset>?>(null) }
    var name by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf<List<SignatureEntity>>(emptyList()) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        saved = state.signaturesOnce()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("رسم توقيع — ارسم بإصبعك داخل المربع")
        Spacer(Modifier.height(8.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(ComposeColor.White)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            val stroke = mutableListOf(pos)
                            currentStroke = stroke
                            strokes.add(stroke)
                        },
                        onDragEnd = { currentStroke = null },
                        onDrag = { change, _ ->
                            currentStroke?.add(change.position)
                        }
                    )
                }
        ) {
            for (points in strokes) {
                if (points.size < 2) continue
                val p = ComposePath().apply {
                    moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
                }
                drawPath(
                    p, color = ComposeColor.Black,
                    style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { strokes.clear(); currentStroke = null }) { Text("مسح الرسم") }
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("اسم التوقيع") }, modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(
            enabled = strokes.isNotEmpty(),
            onClick = {
                // Render the finger strokes to a transparent PNG (main thread:
                // Compose paths live in snapshot state).
                val w = 900; val h = 400
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                val paint = Paint().apply {
                    color = Color.BLACK
                    strokeWidth = 14f
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    isAntiAlias = true
                }
                val scaleX = w / 1000f
                val scaleY = h / 400f
                for (points in strokes) {
                    if (points.size < 2) continue
                    val ap = android.graphics.Path()
                    ap.moveTo(points[0].x * scaleX, points[0].y * scaleY)
                    for (i in 1 until points.size) {
                        ap.lineTo(points[i].x * scaleX, points[i].y * scaleY)
                    }
                    canvas.drawPath(ap, paint)
                }
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val file = ImageTools.newImageFile(
                        context, "sig_${System.currentTimeMillis()}.png"
                    )
                    FileOutputStream(file).use {
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    val sigName = name.ifBlank { "توقيع ${saved.size + 1}" }
                    state.saveSignature(sigName, file.absolutePath)
                    saved = state.signaturesOnce()
                    strokes.clear()
                    currentStroke = null
                    status = "تم حفظ التوقيع «$sigName»."
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("حفظ التوقيع") }
        if (status.isNotBlank()) Text(status)
        Spacer(Modifier.height(12.dp))
        Text("القوالب المحفوظة (${saved.size}):")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(saved, key = { it.id }) { sig ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(sig.name, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = {
                            scope.launch {
                                state.deleteSignature(sig.id)
                                saved = state.signaturesOnce()
                            }
                        }) { Text("حذف") }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("رجوع") }
    }
}
