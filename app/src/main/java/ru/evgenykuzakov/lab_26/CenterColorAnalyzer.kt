package ru.evgenykuzakov.lab_26

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

class CenterColorAnalyzer(
	private val onColor: (r: Int, g: Int, b: Int) -> Unit
) : ImageAnalysis.Analyzer {

	private var lastTs = 0L
	private val minIntervalMs = 80L

	override fun analyze(image: ImageProxy) {
		val now = System.currentTimeMillis()
		if (now - lastTs < minIntervalMs) {
			image.close()
			return
		}

		val planes = image.planes
		if (image.format != android.graphics.ImageFormat.YUV_420_888 || planes.size < 3) {
			image.close()
			return
		}

		val width = image.width
		val height = image.height
		val cx = width / 2
		val cy = height / 2

		val yPlane = planes[0]
		val uPlane = planes[1]
		val vPlane = planes[2]

		val y = yAt(yPlane.buffer, yPlane.rowStride, yPlane.pixelStride, cx, cy)
		val u = uvAt(uPlane.buffer, uPlane.rowStride, uPlane.pixelStride, cx, cy)
		val v = uvAt(vPlane.buffer, vPlane.rowStride, vPlane.pixelStride, cx, cy)

		val (r, g, b) = yuvToRgb(y, u, v)
		lastTs = now
		onColor(r, g, b)

		image.close()
	}

	private fun yAt(buf: ByteBuffer, rowStride: Int, pixelStride: Int, x: Int, y: Int): Int {
		val pos = y * rowStride + x * pixelStride
		return (buf.get(pos).toInt() and 0xFF)
	}

	private fun uvAt(buf: ByteBuffer, rowStride: Int, pixelStride: Int, x: Int, y: Int): Int {
		val uvX = x / 2
		val uvY = y / 2
		val pos = uvY * rowStride + uvX * pixelStride
		return (buf.get(pos).toInt() and 0xFF)
	}

	private fun yuvToRgb(y_: Int, u_: Int, v_: Int): Triple<Int, Int, Int> {
		var y = y_ - 16
		val u = u_ - 128
		val v = v_ - 128
		if (y < 0) y = 0

		var r = (1.164f * y + 1.596f * v).toInt()
		var g = (1.164f * y - 0.392f * u - 0.813f * v).toInt()
		var b = (1.164f * y + 2.017f * u).toInt()

		r = min(255, max(0, r))
		g = min(255, max(0, g))
		b = min(255, max(0, b))
		return Triple(r, g, b)
	}
}