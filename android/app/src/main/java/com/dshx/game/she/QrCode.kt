package com.dshx.game.she

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * 二维码：把分享码画成图，玩家用另一台设备扫，或存成图发论坛。
 * 用 ZXing 编码，纠错级别 L（能装最多数据），整座城市也塞得下。
 */
object QrCode {

    /**
     * 生成二维码位图。
     * @param text 分享码
     * @param size 边长像素
     * @return 位图；失败返回 null
     */
    fun encode(text: String, size: Int = 720): Bitmap? {
        if (text.isEmpty()) return null
        return try {
            val hints = HashMap<EncodeHintType, Any>()
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.L
            hints[EncodeHintType.MARGIN] = 1
            val matrix: BitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
            val w = matrix.width
            val h = matrix.height
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (t: Throwable) {
            null
        }
    }

    /** 把二维码画到 Canvas 上（游戏内直接绘制用） */
    fun drawTo(canvas: Canvas, text: String, left: Float, top: Float, size: Float): Boolean {
        val bmp = encode(text, 512) ?: return false
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bmp, null, android.graphics.RectF(left, top, left + size, top + size), paint)
        bmp.recycle()
        return true
    }

    /** 从相册图片解码二维码（玩家截图分享的场景） */
    fun decodeFromUri(context: android.content.Context, uri: android.net.Uri): String? {
        return try {
            val bitmap = android.graphics.BitmapFactory.decodeStream(
                context.contentResolver.openInputStream(uri)
            ) ?: return null
            decodeBitmap(bitmap)
        } catch (t: Throwable) {
            null
        }
    }

    /** 从位图解码二维码 */
    fun decodeBitmap(bitmap: Bitmap): String? {
        return try {
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            val source = RGBLuminanceSource(w, h, pixels)
            val bmp = BinaryBitmap(HybridBinarizer(source))
            MultiFormatReader().decode(bmp).text
        } catch (t: Throwable) {
            null
        } finally {
            bitmap.recycle()
        }
    }
}
