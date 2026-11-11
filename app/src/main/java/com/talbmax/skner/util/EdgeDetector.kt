package com.talbmax.skner.util

import kotlin.math.abs

/**
 * P0-1: lightweight document-presence check for auto-capture.
 * Works on the camera Y plane (grayscale): downsamples to a small grid and
 * measures Sobel edge density. A document page on a surface produces many
 * strong straight edges; an empty/blurry frame produces very few.
 * Pure Kotlin, no libraries. Returns edge ratio in [0, 1].
 */
object EdgeDetector {
    fun edgeRatio(yPlane: ByteArray, rowStride: Int, width: Int, height: Int): Float {
        val gw = 80; val gh = 60
        val grid = FloatArray(gw * gh)
        for (gy in 0 until gh) {
            val sy = (gy * height) / gh
            for (gx in 0 until gw) {
                val sx = (gx * width) / gw
                grid[gy * gw + gx] = (yPlane[sy * rowStride + sx].toInt() and 0xFF).toFloat()
            }
        }
        var edges = 0
        var total = 0
        for (y in 1 until gh - 1) {
            for (x in 1 until gw - 1) {
                val gx = -grid[(y - 1) * gw + x - 1] - 2f * grid[y * gw + x - 1] - grid[(y + 1) * gw + x - 1] +
                    grid[(y - 1) * gw + x + 1] + 2f * grid[y * gw + x + 1] + grid[(y + 1) * gw + x + 1]
                val gy = -grid[(y - 1) * gw + x - 1] - 2f * grid[(y - 1) * gw + x] - grid[(y - 1) * gw + x + 1] +
                    grid[(y + 1) * gw + x - 1] + 2f * grid[(y + 1) * gw + x] + grid[(y + 1) * gw + x + 1]
                if (abs(gx) + abs(gy) > 90f) edges++
                total++
            }
        }
        return if (total == 0) 0f else edges.toFloat() / total
    }
}
