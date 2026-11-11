package com.talbmax.skner.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

object PdfExport {
    /** Export bitmaps as an A4-ish PDF. Returns the output file. */
    fun exportBitmaps(context: Context, bitmaps: List<Bitmap>, fileName: String, watermark: String = ""): File {
        val dir = File(context.getExternalFilesDir(null), "Skenr").apply { mkdirs() }
        val out = File(dir, fileName)
        val doc = PdfDocument()
        val pageW = 595; val pageH = 842
        bitmaps.forEachIndexed { index, bmp0 ->
            var bmp = bmp0
            if (watermark.isNotBlank()) bmp = ImageTools.withWatermark(bmp, watermark, "")
            val info = PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create()
            val page = doc.startPage(info)
            val canvas: Canvas = page.canvas
            val scale = minOf(pageW.toFloat() / bmp.width, pageH.toFloat() / bmp.height)
            val dw = (bmp.width * scale).toInt(); val dh = (bmp.height * scale).toInt()
            val scaled = Bitmap.createScaledBitmap(bmp, dw, dh, true)
            canvas.drawBitmap(scaled, ((pageW - dw) / 2).toFloat(), ((pageH - dh) / 2).toFloat(), Paint())
            doc.finishPage(page)
        }
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
        return out
    }
}
