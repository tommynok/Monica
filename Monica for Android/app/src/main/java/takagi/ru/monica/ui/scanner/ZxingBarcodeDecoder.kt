package takagi.ru.monica.ui.scanner

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.DecodeHintType
import com.google.zxing.FormatException
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer

/**
 * zxing 条码解码器：负责扫码栈里原本由 ML Kit 承担的识别职责，
 * 避免为内置条码模型在每个 ABI 上多打包约 3MB 的原生库。
 * 相机帧（YUV_420_888）与相册位图共用同一读取器配置，码制集合由调用方给定。
 */
internal class ZxingBarcodeDecoder(private val formats: Collection<BarcodeFormat>) {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to formats.distinct(),
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.CHARACTER_SET to "UTF-8"
            )
        )
    }

    /**
     * 解析一帧相机画面；返回码值，本帧无可读码返回 null，
     * 只有真正的异常错误才向上抛出。
     * 仅在单帧分析线程上调用，读取器内部状态因此无需加锁。
     */
    fun decodeFrame(imageProxy: ImageProxy): String? {
        val plane = imageProxy.planes[0]
        val width = imageProxy.width
        val height = imageProxy.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val buffer = plane.buffer
        // zxing 直接啃全分辨率帧时，13 种码制（含 PDF417/Aztec + TRY_HARDER + 旋转重试）
        // 单帧可达秒级，会触发会话健康策略的帧停滞重启。ML Kit 内部同样先降采样，
        // 因此拷贝 Y 平面时按 1/DECIMATION 抽稀行与列，QR/条码在该分辨率下均可识别。
        val outWidth = (width + DECIMATION - 1) / DECIMATION
        val outHeight = (height + DECIMATION - 1) / DECIMATION
        val luminance = ByteArray(outWidth * outHeight)
        val rowBuffer = ByteArray(width)
        var destinationRow = 0
        if (pixelStride == 1) {
            for (row in 0 until height step DECIMATION) {
                buffer.position(row * rowStride)
                buffer.get(rowBuffer, 0, width)
                var destination = destinationRow * outWidth
                for (column in 0 until width step DECIMATION) {
                    luminance[destination++] = rowBuffer[column]
                }
                destinationRow++
            }
        } else {
            for (row in 0 until height step DECIMATION) {
                var destination = destinationRow * outWidth
                for (column in 0 until width step DECIMATION) {
                    luminance[destination++] = buffer.get(row * rowStride + column * pixelStride)
                }
                destinationRow++
            }
        }

        var source: LuminanceSource = PlanarYUVLuminanceSource(
            luminance, outWidth, outHeight, 0, 0, outWidth, outHeight, false
        )
        val rotationQuarterTurns =
            ((imageProxy.imageInfo.rotationDegrees % 360) + 360) % 360 / 90
        repeat(rotationQuarterTurns) {
            source = rotateSourceCounterClockwise(source)
        }
        return decodeWithFallback(source)
    }

    /**
     * 解析相册位图；zxing 不读 EXIF 方向，依次尝试 4 个旋转角。
     */
    @Synchronized
    fun decodeBitmap(bitmap: Bitmap): String? {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var source: LuminanceSource = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        repeat(ROTATION_ATTEMPTS) {
            decodeWithFallback(source)?.let { return it }
            source = rotateSourceCounterClockwise(source)
        }
        return null
    }

    /**
     * LuminanceSource.rotateCounterClockwise() 基类直接抛 UnsupportedOperationException
     * （PlanarYUV/RGB 源都不支持旋转），因此在这里手动旋转亮度矩阵。
     */
    private fun rotateSourceCounterClockwise(source: LuminanceSource): LuminanceSource {
        val oldWidth = source.width
        val oldHeight = source.height
        val src = source.matrix
        val dst = ByteArray(oldWidth * oldHeight)
        val newWidth = oldHeight
        for (y2 in 0 until oldWidth) {
            val dstRow = y2 * newWidth
            val srcColumn = oldWidth - 1 - y2
            for (x2 in 0 until oldHeight) {
                dst[dstRow + x2] = src[x2 * oldWidth + srcColumn]
            }
        }
        return PlanarYUVLuminanceSource(dst, newWidth, oldWidth, 0, 0, newWidth, oldWidth, false)
    }

    private fun decodeWithFallback(source: LuminanceSource): String? {
        decode(BinaryBitmap(HybridBinarizer(source)))?.let { return it }
        return decode(BinaryBitmap(GlobalHistogramBinarizer(source)))
    }

    private fun decode(bitmap: BinaryBitmap): String? {
        return try {
            reader.decodeWithState(bitmap).text
        } catch (expected: NotFoundException) {
            null
        } catch (expected: FormatException) {
            null
        } catch (expected: ChecksumException) {
            null
        } finally {
            reader.reset()
        }
    }

    private companion object {
        private const val ROTATION_ATTEMPTS = 4
        private const val DECIMATION = 2
    }
}
