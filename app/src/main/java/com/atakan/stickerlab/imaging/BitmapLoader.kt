package com.atakan.stickerlab.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Galeriden seçilen görseli belleğe alır.
 *
 * İki tuzak var:
 *  - Modern telefon fotoğrafları 50+ MP olabiliyor; tam boy decode OOM demek.
 *    Çıktı zaten 512×512 olacağı için [MAX_DIMENSION] yeterli.
 *  - Kamera fotoğrafları döndürülmüş piksellerle değil, EXIF etiketiyle geliyor.
 *    ML Kit'e yan yatmış görsel verilirse özneyi bulamıyor.
 */
@Singleton
class BitmapLoader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun load(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // inJustDecodeBounds modunda decodeStream her zaman null döner ve boyutları
        // options'a yazar — dönüş değerine bakmak yanlış, bounds'a bakılır.
        openStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw LoadException("Görsel çözülemedi (boyut okunamadı): $uri")
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = openStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw LoadException("Görsel çözülemedi: $uri")

        return applyExifRotation(uri, decoded)
    }

    // openInputStream kaynaga gore ya null doner ya da IOException firlatir;
    // ikisi de ayni kullanici hatasi, tek tipe indirgeniyor.
    private fun openStream(uri: Uri): InputStream = try {
        context.contentResolver.openInputStream(uri)
            ?: throw LoadException("Görsel açılamadı: $uri")
    } catch (e: IOException) {
        throw LoadException("Görsel açılamadı: $uri")
    }

    /** İki katları hâlinde küçültür; sonuç en az [MAX_DIMENSION] kalır. */
    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= MAX_DIMENSION || height / (sample * 2) >= MAX_DIMENSION) {
            sample *= 2
        }
        return sample
    }

    private fun applyExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            openStream(uri).use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private companion object {
        const val MAX_DIMENSION = 2048
    }
}

class LoadException(message: String) : Exception(message)
