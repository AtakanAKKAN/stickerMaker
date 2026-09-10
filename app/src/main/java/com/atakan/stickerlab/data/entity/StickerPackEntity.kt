package com.atakan.stickerlab.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sticker_packs", indices = [Index(value = ["identifier"], unique = true)])
data class StickerPackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** WhatsApp'a verilen kimlik. UUID; isimden türetilmez, asla değişmez. */
    val identifier: String,
    val name: String,
    val publisher: String,
    val trayFileName: String,
    /** Değişiklik olduğunda artırılır; yoksa WhatsApp cache'i güncellenmez. */
    @ColumnInfo(defaultValue = "1") val imageDataVersion: Int = 1,
    @ColumnInfo(defaultValue = "0") val animated: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "stickers",
    foreignKeys = [
        ForeignKey(
            entity = StickerPackEntity::class,
            parentColumns = ["id"],
            childColumns = ["packId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("packId")],
)
data class StickerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packId: Long,
    /** filesDir/packs/<identifier>/ altındaki dosya adı: "<uuid>.webp" */
    val fileName: String,
    /** Noktalı virgülle ayrılmış emoji listesi; en az bir tane olmak zorunda. */
    val emojis: String,
    val accessibilityText: String = "",
    val position: Int = 0,
)
