package com.atakan.stickerlab.di

import android.content.Context
import androidx.room.Room
import com.atakan.stickerlab.data.db.AppDatabase
import com.atakan.stickerlab.data.db.StickerPackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides
    fun provideStickerPackDao(db: AppDatabase): StickerPackDao = db.stickerPackDao()
}
