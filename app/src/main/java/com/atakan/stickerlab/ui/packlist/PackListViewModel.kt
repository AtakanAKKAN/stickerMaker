package com.atakan.stickerlab.ui.packlist

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atakan.stickerlab.data.PackRules
import com.atakan.stickerlab.data.StickerFiles
import com.atakan.stickerlab.data.StickerRepository
import com.atakan.stickerlab.data.db.PackWithStickers
import com.atakan.stickerlab.whatsapp.WhatsAppInstallChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PackListViewModel @Inject constructor(
    application: Application,
    private val repository: StickerRepository,
) : AndroidViewModel(application) {

    val packs: StateFlow<List<PackWithStickers>> = repository.observePacks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val whatsAppStatus: WhatsAppInstallChecker.Status =
        WhatsAppInstallChecker.status(application)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val canCreatePack: Boolean get() = PackRules.canCreatePack(packs.value.size)

    fun trayFile(pack: PackWithStickers): File = StickerFiles.stickerFile(
        getApplication(),
        pack.pack.identifier,
        pack.pack.trayFileName,
    )

    fun createPack(name: String) {
        viewModelScope.launch {
            try {
                repository.createPack(name)
            } catch (e: Exception) {
                Log.e(TAG, "Pack oluşturulamadı", e)
                _message.value = e.message ?: "Pack oluşturulamadı"
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private companion object {
        const val TAG = "PackListVM"
    }
}
