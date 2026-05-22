package com.ncclab.tsubaki.data.scanner.strategy

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.ncclab.tsubaki.data.model.ScanResult
import java.util.EnumMap

class ZXingScannerStrategy : ScannerStrategy {
    private var yuvBytes: ByteArray? = null

    private val reader = MultiFormatReader().apply {
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
            put(
                DecodeHintType.POSSIBLE_FORMATS, listOf(
                    BarcodeFormat.QR_CODE,
                    BarcodeFormat.CODE_128,
                    BarcodeFormat.EAN_13,
                    BarcodeFormat.DATA_MATRIX
                )
            )
            // 启用更激进的解码尝试，对小/模糊/倾斜的码更友好
            put(DecodeHintType.TRY_HARDER, true)
            put(DecodeHintType.CHARACTER_SET, "UTF-8")
        }
        setHints(hints)
    }

    override fun analyze(imageProxy: ImageProxy, onResult: (List<ScanResult>) -> Unit) {
        try {
            val rawSource = imageProxy.toLuminanceSource() ?: return
            // QR 码自身可在任意角度被解出；1D 码再尝试一次旋转 90° 的版本
            val result = tryDecode(rawSource) ?: tryDecode(rawSource.rotateCounterClockwise())
            if (result != null) {
                Log.d("ZXingScanner", "Decoded: ${result.text} (${result.barcodeFormat})")
                onResult(
                    listOf(
                        ScanResult(
                            rawValue = result.text,
                            format = result.barcodeFormat.toString(),
                            timestamp = System.currentTimeMillis()
                        )
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("ZXingScanner", "ZXing decoding failed", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun tryDecode(source: com.google.zxing.LuminanceSource): com.google.zxing.Result? {
        // 先正常尝试，再尝试反色（白底黑码 vs 黑底白码）
        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
        } catch (_: NotFoundException) {
            try {
                reader.decodeWithState(BinaryBitmap(HybridBinarizer(source.invert())))
            } catch (_: NotFoundException) {
                null
            } catch (_: Exception) {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            reader.reset()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun analyzeImage(bitmap: Bitmap, onResult: (List<ScanResult>) -> Unit) {
        try {
            val softwareBitmap = when {
                bitmap.config == Bitmap.Config.HARDWARE -> {
                    val newBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(newBitmap)
                    canvas.drawBitmap(bitmap, 0f, 0f, null)
                    newBitmap
                }
                bitmap.config == null -> bitmap.copy(Bitmap.Config.ARGB_8888, false)
                else -> bitmap
            }

            val width = softwareBitmap.width
            val height = softwareBitmap.height
            val pixels = IntArray(width * height)
            softwareBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val luminanceSource = RGBLuminanceSource(width, height, pixels)
            val result = tryDecode(luminanceSource)

            if (softwareBitmap !== bitmap) {
                softwareBitmap.recycle()
            }

            if (result != null) {
                onResult(
                    listOf(
                        ScanResult(
                            rawValue = result.text,
                            format = result.barcodeFormat.toString(),
                            timestamp = System.currentTimeMillis()
                        )
                    )
                )
            } else {
                onResult(emptyList())
            }
        } catch (e: Exception) {
            Log.e("ZXingScanner", "ZXing bitmap decoding failed", e)
            onResult(emptyList())
        }
    }

    override fun release() {
        reader.reset()
    }

    /**
     * 关键修复：尊重 Y 平面的 rowStride。在多数设备上 rowStride > width，
     * 直接拷贝整个 buffer 会导致图像水平错位，ZXing 完全识别不到。
     */
    private fun ImageProxy.toLuminanceSource(): PlanarYUVLuminanceSource? {
        val yPlane = planes.firstOrNull() ?: return null
        val yBuffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride
        val w = width
        val h = height

        val needed = w * h
        val bytes = yuvBytes.let {
            if (it == null || it.size < needed) ByteArray(needed).also { newBytes -> yuvBytes = newBytes } else it
        }

        if (rowStride == w && pixelStride == 1) {
            // 紧凑布局，可直接拷贝
            yBuffer.get(bytes, 0, needed)
        } else {
            // 按行拷贝，剔除每行末尾的 padding
            val rowBuffer = ByteArray(rowStride)
            var offset = 0
            for (row in 0 until h) {
                yBuffer.position(row * rowStride)
                val toRead = minOf(rowStride, yBuffer.remaining())
                yBuffer.get(rowBuffer, 0, toRead)
                if (pixelStride == 1) {
                    System.arraycopy(rowBuffer, 0, bytes, offset, w)
                } else {
                    var col = 0
                    var src = 0
                    while (col < w) {
                        bytes[offset + col] = rowBuffer[src]
                        col++
                        src += pixelStride
                    }
                }
                offset += w
            }
        }

        return PlanarYUVLuminanceSource(
            bytes,
            w,
            h,
            0,
            0,
            w,
            h,
            false
        )
    }
}
