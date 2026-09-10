package com.atakan.stickerlab.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.atakan.stickerlab.data.db.PackWithStickers
import com.atakan.stickerlab.data.db.StickerPackDao
import com.atakan.stickerlab.data.entity.StickerEntity
import com.atakan.stickerlab.data.entity.StickerPackEntity
import com.atakan.stickerlab.export.StickerExporter
import com.atakan.stickerlab.provider.StickerContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StickerRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: StickerPackDao,
    private val exporter: StickerExporter,
) {

    fun observePacks(): Flow<List<PackWithStickers>> = dao.observePacksWithStickers()

    fun observePack(packId: Long): Flow<PackWithStickers?> = dao.observePack(packId)

    suspend fun packCount(): Int = dao.packCount()

    // --- pack yaşam döngüsü ---

    suspend fun createPack(name: String, publisher: String = DEFAULT_PUBLISHER): Long {
        val count = dao.packCount()
        require(PackRules.canCreatePack(count)) {
            "En fazla ${PackRules.MAX_PACKS} pack olabilir."
        }
        val identifier = UUID.randomUUID().toString()
        StickerFiles.packDir(context, identifier).mkdirs()
        val packId = dao.insertPack(
            StickerPackEntity(
                identifier = identifier,
                name = name.trim().ifEmpty { "Yeni pack" },
                publisher = publisher,
                trayFileName = StickerFiles.TRAY_FILE_NAME,
            )
        )
        notifyWhatsApp()
        return packId
    }

    /**
     * Pack adı değişiyor ama [StickerPackEntity.identifier] değişmiyor — WhatsApp
     * pack'i identifier ile tanıyor, isimden türetilseydi her yeniden adlandırma
     * pack'i sıfırlardı.
     */
    suspend fun renamePack(packId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        dao.renamePack(packId, trimmed)
        invalidatePack(packId)
    }

    suspend fun deletePack(packId: Long) {
        val pack = dao.getPackById(packId) ?: return
        dao.deletePack(packId) // sticker satırları CASCADE ile gider
        StickerFiles.packDir(context, pack.identifier).deleteRecursively()
        notifyWhatsApp()
        Log.i(TAG, "Pack silindi: ${pack.identifier}")
    }

    // --- sticker ekleme ---

    /**
     * Kesilmiş görseli pack'e sticker olarak ekler.
     *
     * Sıra önemli: önce dosya yazılır, sonra DB kaydı oluşur. Ters sırada bir hata
     * olursa DB'de dosyası olmayan sticker kalır ve WhatsApp pack'i sessizce reddeder.
     */
    suspend fun addSticker(packId: Long, cutout: Bitmap) {
        val pack = dao.getPackById(packId) ?: throw IllegalArgumentException("Pack yok: $packId")
        val existing = dao.stickerCount(packId)
        require(PackRules.canAddSticker(existing)) {
            "Pack dolu: $existing/${PackRules.MAX_STICKERS}"
        }

        val dir = StickerFiles.packDir(context, pack.identifier)
        val fileName = "${UUID.randomUUID()}.webp"
        exporter.exportSticker(cutout, File(dir, fileName))

        // Pack'in ilk sticker'ı tray ikonu da olur; kullanıcı sonradan değiştirebilir.
        if (existing == 0) {
            exporter.exportTray(cutout, File(dir, StickerFiles.TRAY_FILE_NAME))
        }

        dao.insertStickers(
            listOf(
                StickerEntity(
                    packId = packId,
                    fileName = fileName,
                    emojis = DEFAULT_EMOJI,
                    position = dao.maxPosition(packId) + 1,
                )
            )
        )
        invalidatePack(packId)
        Log.i(TAG, "Sticker eklendi: ${pack.identifier}/$fileName")
    }

    // --- sticker düzenleme ---

    suspend fun updateStickerMeta(stickerId: Long, emojis: String, accessibilityText: String) {
        val sticker = dao.getStickerById(stickerId) ?: return
        // WhatsApp her sticker için en az bir emoji istiyor.
        val safeEmojis = emojis.ifBlank { DEFAULT_EMOJI }
        dao.updateStickerMeta(stickerId, safeEmojis, accessibilityText)
        invalidatePack(sticker.packId)
    }

    suspend fun deleteStickers(stickerIds: List<Long>) {
        if (stickerIds.isEmpty()) return
        val stickers = dao.getStickersByIds(stickerIds)
        val affectedPacks = stickers.map { it.packId }.distinct()

        stickers.forEach { sticker ->
            val pack = dao.getPackById(sticker.packId) ?: return@forEach
            StickerFiles.stickerFile(context, pack.identifier, sticker.fileName).delete()
        }
        dao.deleteStickers(stickerIds)
        affectedPacks.forEach { invalidatePack(it) }
    }

    /** Seçilen sticker'dan 96×96 tray ikonu üretir. */
    suspend fun setTrayFromSticker(stickerId: Long) {
        val sticker = dao.getStickerById(stickerId) ?: return
        val pack = dao.getPackById(sticker.packId) ?: return
        val source = StickerFiles.stickerFile(context, pack.identifier, sticker.fileName)
        val bitmap = BitmapFactory.decodeFile(source.absolutePath) ?: return
        try {
            exporter.exportTray(
                bitmap,
                File(StickerFiles.packDir(context, pack.identifier), StickerFiles.TRAY_FILE_NAME),
            )
            dao.setTrayFileName(pack.id, StickerFiles.TRAY_FILE_NAME)
            invalidatePack(pack.id)
        } finally {
            bitmap.recycle()
        }
    }

    // --- taşıma / kopyalama ---

    /**
     * Sticker'ları başka bir pack'e taşır.
     *
     * Bu işlem **iki pack'i birden** etkiliyor; ikisinin de sürümü artmalı ve
     * WhatsApp'a haber verilmeli, yoksa taşınan sticker eski pack'te görünmeye
     * devam eder.
     */
    suspend fun moveStickers(stickerIds: List<Long>, targetPackId: Long) {
        val stickers = dao.getStickersByIds(stickerIds)
        if (stickers.isEmpty()) return
        val sourcePackId = stickers.first().packId
        require(stickers.all { it.packId == sourcePackId }) {
            "Taşıma tek bir kaynak pack'ten yapılır."
        }
        if (sourcePackId == targetPackId) return

        val sourceCount = dao.stickerCount(sourcePackId)
        require(PackRules.canMoveOut(sourceCount, stickers.size)) {
            PackRules.moveBlockedReason(sourceCount, stickers.size).orEmpty()
        }
        val target = dao.getPackById(targetPackId)
            ?: throw IllegalArgumentException("Hedef pack yok: $targetPackId")
        val targetCount = dao.stickerCount(targetPackId)
        require(PackRules.canAccept(targetCount, stickers.size)) {
            "Hedef pack dolu: $targetCount/${PackRules.MAX_STICKERS}"
        }

        val source = dao.getPackById(sourcePackId)
            ?: throw IllegalArgumentException("Kaynak pack yok: $sourcePackId")
        var position = dao.maxPosition(targetPackId)

        stickers.forEach { sticker ->
            val from = StickerFiles.stickerFile(context, source.identifier, sticker.fileName)
            val newFileName = "${UUID.randomUUID()}.webp"
            val to = StickerFiles.stickerFile(context, target.identifier, newFileName)
            to.parentFile?.mkdirs()
            if (!from.renameTo(to)) {
                from.copyTo(to, overwrite = true)
                from.delete()
            }
            position++
            dao.reassignSticker(sticker.id, targetPackId, newFileName, position)
        }

        invalidatePack(sourcePackId)
        invalidatePack(targetPackId)
        Log.i(TAG, "${stickers.size} sticker taşındı: ${source.identifier} -> ${target.identifier}")
    }

    /**
     * Sticker'ları başka bir pack'e kopyalar.
     *
     * Aynı görseli iki pack'te göstermek **iki ayrı WebP dosyası** demek; tek
     * dosyaya iki pack'ten referans verilmiyor.
     */
    suspend fun copyStickers(stickerIds: List<Long>, targetPackId: Long) {
        val stickers = dao.getStickersByIds(stickerIds)
        if (stickers.isEmpty()) return
        val target = dao.getPackById(targetPackId)
            ?: throw IllegalArgumentException("Hedef pack yok: $targetPackId")
        val targetCount = dao.stickerCount(targetPackId)
        require(PackRules.canAccept(targetCount, stickers.size)) {
            "Hedef pack dolu: $targetCount/${PackRules.MAX_STICKERS}"
        }

        var position = dao.maxPosition(targetPackId)
        val copies = stickers.mapNotNull { sticker ->
            val sourcePack = dao.getPackById(sticker.packId) ?: return@mapNotNull null
            val from = StickerFiles.stickerFile(context, sourcePack.identifier, sticker.fileName)
            val newFileName = "${UUID.randomUUID()}.webp"
            val to = StickerFiles.stickerFile(context, target.identifier, newFileName)
            to.parentFile?.mkdirs()
            from.copyTo(to, overwrite = true)
            position++
            StickerEntity(
                packId = targetPackId,
                fileName = newFileName,
                emojis = sticker.emojis,
                accessibilityText = sticker.accessibilityText,
                position = position,
            )
        }
        dao.insertStickers(copies)

        // Kaynak pack değişmedi; sadece hedefin sürümü artıyor.
        invalidatePack(targetPackId)
        Log.i(TAG, "${copies.size} sticker kopyalandı -> ${target.identifier}")
    }

    // --- seed ---

    /**
     * İlk açılışta assets/seed altındaki dummy dosyalarla tek pack oluşturur.
     * ContentProvider entegrasyonunu doğrulamak için duruyor; gerçek sticker
     * üretimi [addSticker] üzerinden.
     */
    suspend fun seedIfEmpty() {
        if (dao.packCount() > 0) return

        val identifier = UUID.randomUUID().toString()
        val dir = StickerFiles.packDir(context, identifier)
        if (!dir.mkdirs() && !dir.isDirectory) {
            Log.e(TAG, "Pack klasörü oluşturulamadı: $dir")
            return
        }

        copyAsset("seed/${StickerFiles.TRAY_FILE_NAME}", File(dir, StickerFiles.TRAY_FILE_NAME))

        val stickerFileNames = SEED_STICKERS.map { assetName ->
            val fileName = "${UUID.randomUUID()}.webp"
            copyAsset("seed/$assetName", File(dir, fileName))
            fileName
        }

        val packId = dao.insertPack(
            StickerPackEntity(
                identifier = identifier,
                name = "Deneme Paketi",
                publisher = DEFAULT_PUBLISHER,
                trayFileName = StickerFiles.TRAY_FILE_NAME,
            )
        )
        dao.insertStickers(
            stickerFileNames.mapIndexed { index, fileName ->
                StickerEntity(
                    packId = packId,
                    fileName = fileName,
                    emojis = SEED_EMOJIS[index],
                    accessibilityText = "Deneme sticker ${index + 1}",
                    position = index,
                )
            }
        )
        notifyWhatsApp()
        Log.i(TAG, "Seed pack oluşturuldu: $identifier")
    }

    /**
     * Bir pack'in içeriği değiştiğinde çağrılır. imageDataVersion artırılmadan
     * notifyChange çağrılırsa WhatsApp eski görselleri göstermeye devam eder.
     */
    suspend fun invalidatePack(packId: Long) {
        dao.bumpImageDataVersion(packId)
        notifyWhatsApp()
    }

    private fun notifyWhatsApp() {
        context.contentResolver.notifyChange(StickerContract.AUTHORITY_URI, null)
    }

    private fun copyAsset(assetPath: String, target: File) {
        context.assets.open(assetPath).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private companion object {
        const val TAG = "StickerRepository"

        const val DEFAULT_PUBLISHER = "Atakan"

        /** WhatsApp en az bir emoji istiyor; kullanıcı sticker detayından değiştirir. */
        const val DEFAULT_EMOJI = "😀"

        val SEED_STICKERS = listOf("sticker_1.webp", "sticker_2.webp", "sticker_3.webp")
        val SEED_EMOJIS = listOf("😀", "🔥;👍", "🎉")
    }
}
