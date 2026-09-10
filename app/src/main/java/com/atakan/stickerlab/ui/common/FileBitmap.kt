package com.atakan.stickerlab.ui.common

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Diskteki sticker/tray dosyasını küçültülmüş hâlde okur.
 *
 * [version] pack'in `imageDataVersion`'ı gibi bir değer olmalı: dosya adı aynı
 * kalıp içeriği değiştiğinde (tray yenilendiğinde) yeniden okunmasını sağlıyor.
 */
@Composable
fun rememberFileBitmap(
    file: File,
    version: Any? = null,
    maxSize: Int = 256,
): ImageBitmap? = produceState<ImageBitmap?>(null, file.path, version, maxSize) {
    value = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxSize)
        }
        BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
    }
}.value

@Composable
fun StickerThumbnail(
    file: File,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    version: Any? = null,
) {
    val bitmap = rememberFileBitmap(file, version)
    Box(
        // Saydamlığın görünmesi için düz zemin.
        modifier = modifier.background(THUMBNAIL_BACKGROUND),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun sampleSizeFor(width: Int, height: Int, maxSize: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var sample = 1
    while (width / (sample * 2) >= maxSize || height / (sample * 2) >= maxSize) {
        sample *= 2
    }
    return sample
}

private val THUMBNAIL_BACKGROUND = Color(0xFFEDEDED)
