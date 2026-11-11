package com.talbmax.skner.util

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** On-device OCR (Tesseract, Arabic + English). Returns recognised text or an error string. */
object Ocr {
    @Volatile private var api: TessBaseAPI? = null
    @Volatile private var ready = false
    @Volatile private var error: String? = null

    fun init(context: Context): Boolean {
        if (ready) return true
        if (error != null) return false
        return try {
            val dir = File(context.filesDir, "tessdata")
            if (!File(dir, "ara.traineddata").exists() || !File(dir, "eng.traineddata").exists()) {
                dir.mkdirs()
                context.assets.list("tessdata")?.forEach { name ->
                    context.assets.open("tessdata/$name").use { input ->
                        File(dir, name).outputStream().use { input.copyTo(it) }
                    }
                }
            }
            val a = TessBaseAPI()
            // datapath must be the parent of tessdata/
            a.init(context.filesDir.absolutePath, "ara+eng", TessBaseAPI.OEM_LSTM_ONLY)
            a.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            api = a; ready = true; true
        } catch (e: Exception) {
            error = e.message; false
        }
    }

    suspend fun recognise(context: Context, bmp: Bitmap): String = withContext(Dispatchers.IO) {
        if (!init(context)) return@withContext ""
        return@withContext try {
            api?.setImage(bmp)
            api?.getUTF8Text() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun status(): String = if (ready) "ready" else "unavailable: ${error ?: "not initialised"}"
}
