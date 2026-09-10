package com.atakan.stickerlab.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.atakan.stickerlab.data.entity.StickerEntity
import com.atakan.stickerlab.data.entity.StickerPackEntity

@Database(
    entities = [StickerPackEntity::class, StickerEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stickerPackDao(): StickerPackDao

    companion object {
        const val NAME = "stickerlab.db"
    }
}
