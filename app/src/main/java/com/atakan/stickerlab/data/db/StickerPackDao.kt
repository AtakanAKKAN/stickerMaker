package com.atakan.stickerlab.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.atakan.stickerlab.data.entity.StickerEntity
import com.atakan.stickerlab.data.entity.StickerPackEntity
import kotlinx.coroutines.flow.Flow

data class PackWithStickers(
    @Embedded val pack: StickerPackEntity,
    @Relation(parentColumn = "id", entityColumn = "packId")
    val stickers: List<StickerEntity>,
)

@Dao
interface StickerPackDao {

    @Insert
    suspend fun insertPack(pack: StickerPackEntity): Long

    @Insert
    suspend fun insertStickers(stickers: List<StickerEntity>)

    @Query("SELECT COUNT(*) FROM sticker_packs")
    suspend fun packCount(): Int

    @Query("SELECT * FROM sticker_packs WHERE id = :packId")
    suspend fun getPackById(packId: Long): StickerPackEntity?

    @Query("SELECT COUNT(*) FROM stickers WHERE packId = :packId")
    suspend fun stickerCount(packId: Long): Int

    @Query("SELECT * FROM stickers WHERE id IN (:stickerIds)")
    suspend fun getStickersByIds(stickerIds: List<Long>): List<StickerEntity>

    @Query("SELECT * FROM stickers WHERE packId = :packId ORDER BY position ASC, id ASC")
    suspend fun getStickersForPack(packId: Long): List<StickerEntity>

    @Query("SELECT * FROM stickers WHERE id = :stickerId")
    suspend fun getStickerById(stickerId: Long): StickerEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM stickers WHERE packId = :packId")
    suspend fun maxPosition(packId: Long): Int

    @Transaction
    @Query("SELECT * FROM sticker_packs ORDER BY createdAt ASC")
    fun observePacksWithStickers(): Flow<List<PackWithStickers>>

    @Transaction
    @Query("SELECT * FROM sticker_packs WHERE id = :packId")
    fun observePack(packId: Long): Flow<PackWithStickers?>

    // --- yazma ---

    @Query("UPDATE sticker_packs SET name = :name WHERE id = :packId")
    suspend fun renamePack(packId: Long, name: String)

    @Query("UPDATE sticker_packs SET trayFileName = :trayFileName WHERE id = :packId")
    suspend fun setTrayFileName(packId: Long, trayFileName: String)

    @Query("DELETE FROM sticker_packs WHERE id = :packId")
    suspend fun deletePack(packId: Long)

    @Query("DELETE FROM stickers WHERE id IN (:stickerIds)")
    suspend fun deleteStickers(stickerIds: List<Long>)

    @Query(
        "UPDATE stickers SET emojis = :emojis, accessibilityText = :accessibilityText " +
            "WHERE id = :stickerId"
    )
    suspend fun updateStickerMeta(stickerId: Long, emojis: String, accessibilityText: String)

    @Query("UPDATE stickers SET packId = :packId, fileName = :fileName, position = :position WHERE id = :stickerId")
    suspend fun reassignSticker(stickerId: Long, packId: Long, fileName: String, position: Int)

    // --- ContentProvider senkron okumaları (binder thread'inde çalışır) ---

    @Query("SELECT * FROM sticker_packs ORDER BY createdAt ASC")
    fun getAllPacksSync(): List<StickerPackEntity>

    @Query("SELECT * FROM sticker_packs WHERE identifier = :identifier LIMIT 1")
    fun getPackSync(identifier: String): StickerPackEntity?

    @Query(
        """
        SELECT s.* FROM stickers s
        INNER JOIN sticker_packs p ON p.id = s.packId
        WHERE p.identifier = :identifier
        ORDER BY s.position ASC, s.id ASC
        """
    )
    fun getStickersSync(identifier: String): List<StickerEntity>

    @Query("UPDATE sticker_packs SET imageDataVersion = imageDataVersion + 1 WHERE id = :packId")
    suspend fun bumpImageDataVersion(packId: Long)
}
