package com.atakan.stickerlab

import android.app.Application
import com.atakan.stickerlab.data.StickerRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class StickerLabApp : Application() {

    @Inject lateinit var repository: StickerRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        scope.launch { repository.seedIfEmpty() }
    }
}
