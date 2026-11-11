package com.talbmax.skner.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PointF
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

object ImageTools {
    fun load(path: String, maxSide: Int = 2000): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        var sample = 1
        val biggest = max(opts.outWidth, opts.outHeight)
        while (biggest / sample > maxSide) sample *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(path, o2)
    }

    fun save(bmp: Bitmap, file: File): String {
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return file.absolutePath
    }

    /** Filters: original / bw (black-white) / gray / enhance (contrast+sharpness look). */
    fun applyFilter(src: Bitmap, filter: String): Bitmap {
        if (filter == "original") return src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint()
        val m = ColorMatrix()
        when (filter) {
            "bw" -> {
                m.setSaturation(0f)
                val contrast = ColorMatrix(floatArrayOf(
                    2f, 0f, 0f, 0f, -160f,
                    0f, 2f, 0f, 0f, -160f,
                    0f, 0f, 2f, 0f, -160f,
                    0f, 0f, 0f, 1f, 0f))
                m.postConcat(contrast)
            }
            "gray" -> m.setSaturation(0f)
            "enhance" -> {
                val c = ColorMatrix(floatArrayOf(
                    1.25f, 0f, 0f, 0f, -12f,
                    0f, 1.25f, 0f, 0f, -12f,
                    0f, 0f, 1.25f, 0f, -12f,
                    0f, 0f, 0f, 1f, 0f))
                m.postConcat(c)
            }
        }
        paint.colorFilter = ColorMatrixColorFilter(m)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /** Perspective warp from 4 source corners (tl, tr, br, bl in image coords)
     *  to a rectangle, pure-Kotlin homography with bilinear sampling. */
    fun warpPerspective(src: Bitmap, tl: PointF, tr: PointF, br: PointF, bl: PointF): Bitmap {
        val w = max(dist(tl, tr), dist(bl, br)).toInt().coerceAtLeast(8)
        val h = max(dist(tl, bl), dist(tr, br)).toInt().coerceAtLeast(8)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val srcPx = IntArray(src.width * src.height)
        src.getPixels(srcPx, 0, src.width, 0, 0, src.width, src.height)
        val outPx = IntArray(w * h)
        val hMat = homography(
            floatArrayOf(0f, 0f, w.toFloat(), 0f, w.toFloat(), h.toFloat(), 0f, h.toFloat()),
            floatArrayOf(tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y)
        ) ?: return src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val d = hMat[6] * x + hMat[7] * y + 1f
                val sx = (hMat[0] * x + hMat[1] * y + hMat[2]) / d
                val sy = (hMat[3] * x + hMat[4] * y + hMat[5]) / d
                outPx[y * w + x] = sampleBilinear(srcPx, src.width, src.height, sx, sy)
            }
        }
        out.setPixels(outPx, 0, w, 0, 0, w, h)
        return out
    }

    private fun dist(a: PointF, b: PointF): Float {
        val dx = a.x - b.x; val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /** Solve homography mapping dst points -> src points (8 unknowns, Gaussian elim). */
    private fun homography(dst: FloatArray, src: FloatArray): FloatArray? {
        // Unknowns h0..h7 (h8 = 1). For each pair: x' = (h0*x + h1*y + h2)/(h6*x + h7*y + 1)
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val x = dst[i * 2].toDouble(); val y = dst[i * 2 + 1].toDouble()
            val u = src[i * 2].toDouble(); val v = src[i * 2 + 1].toDouble()
            a[i * 2] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
            a[i * 2 + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
        }
        for (col in 0 until 8) {
            var piv = col
            for (r in col + 1 until 8) if (kotlin.math.abs(a[r][col]) > kotlin.math.abs(a[piv][col])) piv = r
            if (kotlin.math.abs(a[piv][col]) < 1e-9) return null
            val tmp = a[col]; a[col] = a[piv]; a[piv] = tmp
            val div = a[col][col]
            for (c in col until 9) a[col][c] /= div
            for (r in 0 until 8) {
                if (r == col) continue
                val f = a[r][col]
                for (c in col until 9) a[r][c] -= f * a[col][c]
            }
        }
        return FloatArray(8) { a[it][8].toFloat() } + floatArrayOf(1f)
    }

    private fun sampleBilinear(px: IntArray, w: Int, h: Int, fx: Float, fy: Float): Int {
        if (fx < 0 || fy < 0 || fx > w - 1 || fy > h - 1) return -0x1
        val x0 = fx.toInt().coerceIn(0, w - 2); val y0 = fy.toInt().coerceIn(0, h - 2)
        val dx = (fx - x0).coerceIn(0f, 1f); val dy = (fy - y0).coerceIn(0f, 1f)
        fun at(x: Int, y: Int) = px[y * w + x]
        fun lerp(a: Int, b: Int, t: Float) = (a + (b - a) * t).toInt()
        val c00 = at(x0, y0); val c10 = at(x0 + 1, y0); val c01 = at(x0, y0 + 1); val c11 = at(x0 + 1, y0 + 1)
        val r = lerp(lerp(c00 shr 16 and 255, c10 shr 16 and 255, dx), lerp(c01 shr 16 and 255, c11 shr 16 and 255, dx), dy)
        val g = lerp(lerp(c00 shr 8 and 255, c10 shr 8 and 255, dx), lerp(c01 shr 8 and 255, c11 shr 8 and 255, dx), dy)
        val b = lerp(lerp(c00 and 255, c10 and 255, dx), lerp(c01 and 255, c11 and 255, dx), dy)
        return -0x1000000 or (r shl 16) or (g shl 8) or b
    }

    /** Draw watermark text diagonally + signature line on a copy of the bitmap. */
    fun withWatermark(src: Bitmap, watermark: String, signature: String): Bitmap {
        if (watermark.isBlank() && signature.isBlank()) return src
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint().apply {
            color = 0x66000000; textSize = src.width / 12f; isAntiAlias = true
        }
        if (watermark.isNotBlank()) {
            canvas.save(); canvas.rotate(-30f, src.width / 2f, src.height / 2f)
            canvas.drawText(watermark, src.width * 0.08f, src.height / 2f, paint)
            canvas.restore()
        }
        if (signature.isNotBlank()) {
            paint.textSize = src.width / 18f
            canvas.drawText("✍ $signature", src.width * 0.05f, src.height * 0.94f, paint)
        }
        return out
    }

    /** Build one collage page from several bitmaps in a grid layout. */
    fun collage(bitmaps: List<Bitmap>, cols: Int, outW: Int = 1240, outH: Int = 1754): Bitmap {
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out); canvas.drawColor(-0x1)
        val rows = (bitmaps.size + cols - 1) / cols
        val pad = 24
        val cw = (outW - pad * (cols + 1)) / cols
        val ch = (outH - pad * (rows + 1)) / rows
        bitmaps.forEachIndexed { i, bmp ->
            val col = i % cols; val row = i / cols
            val left = pad + col * (cw + pad); val top = pad + row * (ch + pad)
            val scale = min(cw.toFloat() / bmp.width, ch.toFloat() / bmp.height)
            val dw = (bmp.width * scale).toInt(); val dh = (bmp.height * scale).toInt()
            val scaled = Bitmap.createScaledBitmap(bmp, dw, dh, true)
            canvas.drawBitmap(scaled, (left + (cw - dw) / 2).toFloat(), (top + (ch - dh) / 2).toFloat(), null)
        }
        return out
    }

    fun newImageFile(context: Context, name: String): File {
        val dir = File(context.filesDir, "pages").apply { mkdirs() }
        return File(dir, name)
    }
}
