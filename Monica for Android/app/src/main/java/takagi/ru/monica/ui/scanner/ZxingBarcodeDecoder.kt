package takagi.ru.monica.ui.scanner

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.DecodeHintType
import com.google.zxing.FormatException
import com.google.zxing.InvertedLuminanceSource
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer

/**
 * zxing 条码解码器：负责扫码栈里原本由 ML Kit 承担的识别职责，
 * 避免为内置条码模型在每个 ABI 上多打包约 3MB 的原生库。
 * 相机帧（YUV_420_888）与相册位图共用同一读取器配置，码制集合由调用方给定。
 *
 * ML Kit 有两项隐性识别能力在替换时容易丢失，这里显式补齐：
 * 1. 反色二维码（暗底亮码，如深色主题下的登录码）——旧 zxing 方案的"正反混合扫描"覆盖的正是这个场景；
 * 2. 远距离小码——在抽稀解码面之外保留全分辨率兜底。
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
        val rotationQuarterTurns =
            ((imageProxy.imageInfo.rotationDegrees % 360) + 360) % 360 / 90

        // 第一级：抽稀解码面（对齐 ML Kit 内部降采样行为），常规尺寸的码在此秒出。
        val outWidth = (width + DECIMATION - 1) / DECIMATION
        val outHeight = (height + DECIMATION - 1) / DECIMATION
        val decimated = copyLuminance(plane.buffer, width, height, plane.rowStride, plane.pixelStride, DECIMATION)
        val decimatedSource = rotated(
            PlanarYUVLuminanceSource(decimated, outWidth, outHeight, 0, 0, outWidth, outHeight, false),
            rotationQuarterTurns
        )
        decodeWithFallback(decimatedSource)?.let { return it }

        // 第二级：第一级落空时才构建的全分辨率亮度面，覆盖画面中占比很小的码。
        val full = copyLuminance(plane.buffer, width, height, plane.rowStride, plane.pixelStride, 1)
        val fullSource = rotated(
            PlanarYUVLuminanceSource(full, width, height, 0, 0, width, height, false),
            rotationQuarterTurns
        )
        return decodeWithFallback(fullSource)
    }

    /**
     * 从 Y 平面拷贝出紧凑亮度矩阵；step > 1 时按行/列抽稀。
     * buffer 需在调用期间保持有效（imageProxy 尚未 close）。
     */
    private fun copyLuminance(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
        step: Int
    ): ByteArray {
        val outWidth = (width + step - 1) / step
        val outHeight = (height + step - 1) / step
        val luminance = ByteArray(outWidth * outHeight)
        val rowBuffer = ByteArray(width)
        var destinationRow = 0
        if (pixelStride == 1) {
            for (row in 0 until height step step) {
                buffer.position(row * rowStride)
                buffer.get(rowBuffer, 0, width)
                var destination = destinationRow * outWidth
                for (column in 0 until width step step) {
                    luminance[destination++] = rowBuffer[column]
                }
                destinationRow++
            }
        } else {
            for (row in 0 until height step step) {
                var destination = destinationRow * outWidth
                for (column in 0 until width step step) {
                    luminance[destination++] = buffer.get(row * rowStride + column * pixelStride)
                }
                destinationRow++
            }
        }
        return luminance
    }

    /** 解析相册位图；zxing 不读 EXIF 方向，依次尝试 4 个旋转角。 */
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
     * 解码阶梯：Hybrid 二值化（正色 → 反色）→ GlobalHistogram 兜底（正色 → 反色）。
     * 反色扫描覆盖暗底亮码；GlobalHistogram 对小模块和一维码与 Hybrid 互补。
     */
    internal fun decodeWithFallback(source: LuminanceSource): String? {
        decode(BinaryBitmap(HybridBinarizer(source)))?.let { return it }
        decode(BinaryBitmap(HybridBinarizer(InvertedLuminanceSource(source))))?.let { return it }
        decode(BinaryBitmap(GlobalHistogramBinarizer(source)))?.let { return it }
        return decode(BinaryBitmap(GlobalHistogramBinarizer(InvertedLuminanceSource(source))))
    }

    private fun rotated(source: LuminanceSource, quarterTurns: Int): LuminanceSource {
        var result = source
        repeat(quarterTurns) { result = rotateSourceCounterClockwise(result) }
        return result
    }

    /**
     * LuminanceSource.rotateCounterClockwise() 基类直接抛 UnsupportedOperationException
     * （PlanarYUV/RGB 源都不支持旋转），因此在这里手动旋转亮度矩阵。
     */
    internal fun rotateSourceCounterClockwise(source: LuminanceSource): LuminanceSource {
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
