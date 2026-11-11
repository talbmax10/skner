package com.talbmax.skner.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.talbmax.skner.util.ImageTools
import java.io.File

@Composable
fun CameraScreen(mode: String, onCaptured: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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
    val imageCapture = remember { ImageCapture.Builder().build() }
    val modes = listOf("مستند", "هوية", "كتاب", "صورة")

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
                    try {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                        )
                    } catch (e: Exception) {
                        status = "تعذر فتح الكاميرا: ${e.message}"
                    }
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
            Text(
                "ضع الورقة داخل الإطار — الالتقاط يدوي في v1",
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)
            )
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            modes.forEach { m ->
                FilterChip(selected = currentMode == m, onClick = { currentMode = m }, label = { Text(m) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { flashOn = !flashOn; imageCapture.flashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF }) {
                Text(if (flashOn) "فلاش: يعمل" else "فلاش: متوقف")
            }
            Button(
                onClick = {
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
                            }
                        }
                    )
                },
                modifier = Modifier.weight(1f)
            ) { Text("التقاط") }
            OutlinedButton(onClick = onBack) { Text("رجوع") }
        }
        if (status.isNotBlank()) Text(status, Modifier.padding(8.dp))
        Text("الوضع: $currentMode", Modifier.padding(8.dp))
    }
}
