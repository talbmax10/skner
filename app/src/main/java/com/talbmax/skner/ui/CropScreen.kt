package com.talbmax.skner.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.talbmax.skner.util.ImageTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Crop screen: the image is shown full-size; four corner handles can be dragged.
 * Confirm runs a pure-Kotlin perspective warp to the corner quad, then the
 * chosen filter, and saves the result as a new file.
 */
@Composable
fun CropScreen(imagePath: String, onDone: (String, String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var filter by remember { mutableStateOf("enhance") }
    var busy by remember { mutableStateOf(false) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    // Corners as fractions of the displayed box (tl, tr, br, bl).
    var corners by remember {
        mutableStateOf(
            listOf(Offset(0.08f, 0.08f), Offset(0.92f, 0.08f), Offset(0.92f, 0.92f), Offset(0.08f, 0.92f))
        )
    }
    var dragIndex by remember { mutableStateOf(-1) }
    val filters = listOf("enhance" to "تحسين", "original" to "أصلي", "bw" to "أبيض وأسود", "gray" to "رمادي")

    androidx.compose.runtime.LaunchedEffect(imagePath) {
        bitmap = withContext(Dispatchers.IO) { ImageTools.load(imagePath) }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("القص والتحسين — اسحب الزوايا الأربع")
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFFDFE9E9))
                .onSizeChanged { boxSize = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            if (boxSize.width == 0) return@detectDragGestures
                            val fx = pos.x / boxSize.width; val fy = pos.y / boxSize.height
                            dragIndex = corners.indices.minByOrNull { i ->
                                val c = corners[i]
                                (c.x - fx) * (c.x - fx) + (c.y - fy) * (c.y - fy)
                            } ?: -1
                        },
                        onDragEnd = { dragIndex = -1 },
                        onDrag = { change, drag ->
                            if (dragIndex >= 0 && boxSize.width > 0) {
                                val c = corners[dragIndex]
                                val nx = (c.x + drag.x / boxSize.width).coerceIn(0f, 1f)
                                val ny = (c.y + drag.y / boxSize.height).coerceIn(0f, 1f)
                                val copy = corners.toMutableList()
                                copy[dragIndex] = Offset(nx, ny)
                                corners = copy
                            }
                        }
                    )
                }
        ) {
            bitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(), contentDescription = "الصورة الملتقطة",
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
                )
            }
            // Corner markers positioned by fraction of the displayed box.
            corners.forEach { c ->
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(24.dp)
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                (c.x * boxSize.width).toInt() - 12,
                                (c.y * boxSize.height).toInt() - 12
                            )
                        }
                        .background(Color(0xFF00A99D), CircleShape)
                )
            }
        }
        Text("الزوايا: ${corners.map { "%.2f,%.2f".format(it.x, it.y) }.joinToString("  ")}")
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            filters.forEach { (key, label) ->
                FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label) })
            }
        }
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text("إعادة التقاط") }
            Button(
                enabled = bitmap != null && !busy,
                onClick = {
                    val bmp = bitmap ?: return@Button
                    busy = true
                    scope.launch(Dispatchers.IO) {
                        val w = bmp.width.toFloat(); val h = bmp.height.toFloat()
                        val warped = ImageTools.warpPerspective(
                            bmp,
                            androidx.compose.ui.geometry.Offset(corners[0].x * w, corners[0].y * h).let {
                                android.graphics.PointF(it.x, it.y)
                            },
                            android.graphics.PointF(corners[1].x * w, corners[1].y * h),
                            android.graphics.PointF(corners[2].x * w, corners[2].y * h),
                            android.graphics.PointF(corners[3].x * w, corners[3].y * h)
                        )
                        val filtered = ImageTools.applyFilter(warped, filter)
                        val out = ImageTools.newImageFile(context, "page_${System.currentTimeMillis()}.jpg")
                        val path = ImageTools.save(filtered, out)
                        withContext(Dispatchers.Main) { onDone(path, filter) }
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text(if (busy) "جارٍ الحفظ…" else "تأكيد ✓") }
        }
    }
}
