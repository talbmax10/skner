package com.talbmax.skner.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.talbmax.skner.util.EdgeDetector
import com.talbmax.skner.util.ImageTools
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * P0-1: auto-capture with a real state machine.
 * SEARCH: waiting for document edges + a steady phone.
 * COUNTDOWN: edges + steady for 800ms → 1s visual countdown.
 * CAPTURE: take the photo, back to SEARCH.
 * Any movement during the countdown cancels back to SEARCH.
 * Stability comes from the accelerometer; edges come from a small Sobel
 * pass over the preview Y plane (pure Kotlin, no new libraries).
 */
@Composable
fun CameraScreen(mode: String, onCaptured: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }
    var currentMode by remember { mutableStateOf(mode) }
    var flashOn by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var autoCapture by remember { mutableStateOf(true) }
    var captureState by remember { mutableStateOf("SEARCH") }
    var countdown by remember { mutableStateOf(0) }
    var hint by remember { mutableStateOf("وجّه الكاميرا نحو الورقة") }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val modes = listOf("مستند", "هوية", "كتاب", "صورة")
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // Shared mutable flags read by the analyzer + sensor (kept outside Compose
    // state to avoid recomposing on every frame).
    val flags = remember {
        object {
            @Volatile var stableSince: Long = 0L
            @Volatile var lastMovement: Long = System.currentTimeMillis()
            @Volatile var hasEdges: Boolean = false
            @Volatile var lastAnalysis: Long = 0L
        }
    }

    // Accelerometer → stability. Any significant motion resets the timer.
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var lastMag = 9.81f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent?) {
                if (e == null) return
                val mag = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
                if (abs(mag - lastMag) > 0.35f) {
                    flags.lastMovement = System.currentTimeMillis()
                }
                lastMag = mag
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    fun takePhoto() {
        val file = ImageTools.newImageFile(context, "scan_${System.currentTimeMillis()}.jpg")
        val opts = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(
            opts, ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(o: ImageCapture.OutputFileResults) {
                    onCaptured(file.absolutePath)
                }
                override fun onError(e: ImageCaptureException) {
                    status = "خطأ بالالتقاط: ${e.message}"
                    captureState = "SEARCH"
                }
            }
        )
    }

    // Auto-capture driver: runs while the screen is visible.
    androidx.compose.runtime.LaunchedEffect(autoCapture) {
        while (true) {
            delay(120)
            if (!autoCapture || !granted) continue
            val now = System.currentTimeMillis()
            val steady = now - flags.lastMovement > 800
            val ready = flags.hasEdges && steady
            when (captureState) {
                "SEARCH" -> {
                    hint = if (!flags.hasEdges) "ابحث عن المستند — ضع الورقة داخل الإطار"
                    else if (!steady) "ثبّت الهاتف… لا تتحرك"
                    else "تم العثور على المستند"
                    if (ready) {
                        captureState = "COUNTDOWN"
                        flags.stableSince = now
                        countdown = 1
                        hint = "التقاط خلال 1…"
                    }
                }
                "COUNTDOWN" -> {
                    if (!ready) {
                        captureState = "SEARCH"
                        countdown = 0
                        hint = "تحرك الهاتف — أعد التثبيت"
                    } else if (now - flags.stableSince >= 1000) {
                        captureState = "CAPTURING"
                        hint = "تم الالتقاط ✓"
                        takePhoto()
                    }
                }
            }
        }
    }

    if (!granted) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("يحتاج التطبيق إذن الكاميرا لمسح المستندات.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("منح إذن الكاميرا") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onBack) { Text("رجوع") }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val provider = ProcessCameraProvider.getInstance(ctx).get()
                    val preview = Preview.Builder().build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { image: ImageProxy ->
                        try {
                            val now = System.currentTimeMillis()
                            if (now - flags.lastAnalysis > 150) {
                                flags.lastAnalysis = now
                                val yPlane = image.planes[0].buffer
                                val bytes = ByteArray(yPlane.remaining())
                                yPlane.get(bytes)
                                val ratio = EdgeDetector.edgeRatio(
                                    bytes, image.planes[0].rowStride, image.width, image.height
                                )
                                // A page on a surface: edge ratio roughly 0.02–0.6.
                                val edges = ratio in 0.02f..0.6f
                                if (edges != flags.hasEdges) {
                                    mainHandler.post { flags.hasEdges = edges }
                                }
                            }
                        } catch (e: Exception) {
                            // Never crash the camera on an analysis failure.
                        } finally {
                            image.close()
                        }
                    }
                    try {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                            preview, imageCapture, analysis
                        )
                    } catch (e: Exception) {
                        status = "تعذر فتح الكاميرا: ${e.message}"
                    }
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
            Text(
                hint,
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)
            )
            if (captureState == "COUNTDOWN") {
                Text(
                    "1…",
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            modes.forEach { m ->
                FilterChip(selected = currentMode == m, onClick = { currentMode = m }, label = { Text(m) })
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("التقاط تلقائي")
            Switch(checked = autoCapture, onCheckedChange = {
                autoCapture = it
                captureState = "SEARCH"
                hint = if (it) "وجّه الكاميرا نحو الورقة" else "الالتقاط اليدوي مفعّل"
            })
            Spacer(Modifier.weight(1f))
            Text("الحالة: $captureState")
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                flashOn = !flashOn
                imageCapture.flashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
            }) {
                Text(if (flashOn) "فلاش: يعمل" else "فلاش: متوقف")
            }
            Button(
                onClick = { takePhoto() },
                modifier = Modifier.weight(1f)
            ) { Text("التقاط يدوي") }
            OutlinedButton(onClick = onBack) { Text("رجوع") }
        }
        if (status.isNotBlank()) Text(status, Modifier.padding(8.dp))
        Text("الوضع: $currentMode", Modifier.padding(8.dp))
    }
}
