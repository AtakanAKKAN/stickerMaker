package com.atakan.stickerlab.data

import android.content.Context
import java.io.File

/**
 * Disk düzeni:
 *   filesDir/packs/<packIdentifier>/tray.png
 *   filesDir/packs/<packIdentifier>/<stickerUUID>.webp
 *
 * Sticker dosyaları pack'e aittir. Aynı görseli iki pack'te göstermek iki ayrı
 * dosya demektir; paylaşılan tek dosyaya iki pack'ten referans verilmez.
 */
object StickerFiles {

    const val TRAY_FILE_NAME = "tray.png"

    fun packsRoot(context: Context): File = File(context.filesDir, "packs")

    fun packDir(context: Context, identifier: String): File =
        File(packsRoot(context), identifier)

    fun stickerFile(context: Context, identifier: String, fileName: String): File =
        File(packDir(context, identifier), fileName)
}
