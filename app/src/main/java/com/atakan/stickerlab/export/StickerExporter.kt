package com.atakan.stickerlab.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kaynak görseli WhatsApp'ın kabul ettiği dosyalara çevirir.
 *
 * İki iş yapar ve ikisi de kayıpsız değildir:
 *  1. İçeriği saydam kenarlarından kırpıp 512×512 (ya da 96×96) tuvale ortalar.
 *  2. Dosyayı boyut limitinin altına sığdırır.
 *
 * Sabit bir WebP quality değeri kullanılamaz: aynı quality, düz renkli bir
 * sticker'da 8 KB, detaylı bir fotoğrafta 300 KB üretebiliyor. Limitin altına
 * sığan **en yüksek** quality binary search ile bulunuyor.
 */
@Singleton
class StickerExporter @Inject constructor() {

    data class Result(
        val file: File,
        val byteCount: Int,
        /** Sticker için kullanılan WebP quality; tray (PNG) için null. */
        val quality: Int?,
    )

    /**
     * [source]'u 512×512 WebP olarak [target]'a yazar.
     *
     * @throws ExportException limitin altına sığdırılamazsa.
     */
    fun exportSticker(source: Bitmap, target: File): Result {
        val canvas = fitToCanvas(
            source = source,
            canvasSize = StickerFormat.STICKER_SIZE,
            padding = StickerFormat.STICKER_PADDING,
        )
        return try {
            val limit = StickerFormat.STICKER_MAX_BYTES - StickerFormat.SIZE_SAFETY_MARGIN
            val encoded = encodeWithinLimit(canvas, limit)
                ?: throw ExportException(
                    "Görsel quality=0'da bile $limit bayta sığmadı " +
                        "(${source.width}×${source.height})"
                )
            writeAtomically(target, encoded.bytes)
            Log.d(TAG, "sticker -> ${target.name} ${encoded.bytes.size}B q=${encoded.quality}")
            Result(target, encoded.bytes.size, encoded.quality)
        } finally {
            canvas.recycle()
        }
    }

    /**
     * Tray ikonu: 96×96 PNG.
     *
     * Burada binary search gerekmiyor — 96×96 ARGB'nin sıkıştırılmamış hali bile
     * 36 KB (96·96·4), yani PNG hiçbir görselde 50 KB limitini aşamaz.
     */
    fun exportTray(source: Bitmap, target: File): Result {
        val canvas = fitToCanvas(
            source = source,
            canvasSize = StickerFormat.TRAY_SIZE,
            padding = StickerFormat.TRAY_PADDING,
        )
        return try {
            val bytes = ByteArrayOutputStream().use { out ->
                canvas.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
            if (bytes.size > StickerFormat.TRAY_MAX_BYTES) {
                // Teorik olarak ulaşılamaz; sessizce bozuk pack üretmektense patla.
                throw ExportException("Tray ${bytes.size} bayt, limit ${StickerFormat.TRAY_MAX_BYTES}")
            }
            writeAtomically(target, bytes)
            Log.d(TAG, "tray -> ${target.name} ${bytes.size}B")
            Result(target, bytes.size, quality = null)
        } finally {
            canvas.recycle()
        }
    }

    // --- iç kısım ---

    private class Encoded(val bytes: ByteArray, val quality: Int)

    /**
     * Limitin altına sığan en yüksek quality'yi bulur.
     *
     * Önce quality=100 denenir: kesilmiş sticker'ların çoğu saydam alan ağırlıklı
     * olduğu için genelde ilk denemede sığar ve arama hiç çalışmaz.
     */
    private fun encodeWithinLimit(bitmap: Bitmap, limit: Int): Encoded? {
        val best = compress(bitmap, MAX_QUALITY)
        if (best.size <= limit) return Encoded(best, MAX_QUALITY)

        var low = 0
        var high = MAX_QUALITY - 1
        var found: Encoded? = null
        while (low <= high) {
            val mid = (low + high) / 2
            val bytes = compress(bitmap, mid)
            if (bytes.size <= limit) {
                found = Encoded(bytes, mid)
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            bitmap.compress(webpFormat, quality, out)
            out.toByteArray()
        }

    /**
     * İçeriği saydam kenarlarından kırpar, oranını koruyarak [canvasSize]
     * tuvaline sığdırır ve ortalar. Sonuç her zaman tam olarak kare.
     */
    private fun fitToCanvas(source: Bitmap, canvasSize: Int, padding: Int): Bitmap {
        val content = opaqueBounds(source)
        val available = canvasSize - 2 * padding
        val scale = minOf(
            available.toFloat() / content.width(),
            available.toFloat() / content.height(),
        )
        val drawWidth = (content.width() * scale).toInt().coerceAtLeast(1)
        val drawHeight = (content.height() * scale).toInt().coerceAtLeast(1)
        val left = (canvasSize - drawWidth) / 2
        val top = (canvasSize - drawHeight) / 2

        val output = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(
            source,
            content,
            Rect(left, top, left + drawWidth, top + drawHeight),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        return output
    }

    /**
     * Görselin saydam olmayan kısmını çevreleyen dikdörtgen.
     *
     * ML Kit'in çıktısı genelde tuvalin ortasında küçük bir nesne oluyor; kırpmazsak
     * 512×512'nin büyük kısmı boşa gidiyor ve sticker sohbette minik görünüyor.
     */
    private fun opaqueBounds(source: Bitmap): Rect {
        if (!source.hasAlpha()) return Rect(0, 0, source.width, source.height)

        val width = source.width
        val height = source.height
        val row = IntArray(width)
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1

        for (y in 0 until height) {
            source.getPixels(row, 0, width, 0, y, width, 1)
            var rowMinX = -1
            var rowMaxX = -1
            for (x in 0 until width) {
                if ((row[x] ushr 24) >= StickerFormat.ALPHA_THRESHOLD) {
                    if (rowMinX == -1) rowMinX = x
                    rowMaxX = x
                }
            }
            if (rowMaxX != -1) {
                if (rowMinX < minX) minX = rowMinX
                if (rowMaxX > maxX) maxX = rowMaxX
                if (y < minY) minY = y
                maxY = y
            }
        }

        // Tamamen saydam görsel: kırpacak içerik yok, tuvalin tamamı kullanılır.
        if (maxX == -1) return Rect(0, 0, width, height)
        return Rect(minX, minY, maxX + 1, maxY + 1)
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(target)) {
            temp.delete()
            throw ExportException("Dosya yazılamadı: $target")
        }
    }

    private companion object {
        const val TAG = "StickerExporter"
        const val MAX_QUALITY = 100

        /** WEBP_LOSSY API 30'da geldi; minSdk 26 olduğu için eski sabit gerekiyor. */
        val webpFormat: Bitmap.CompressFormat =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
    }
}

class ExportException(message: String) : Exception(message)
