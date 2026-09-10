package com.atakan.stickerlab.ui.packdetail

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.atakan.stickerlab.data.PackRules
import com.atakan.stickerlab.data.StickerFiles
import com.atakan.stickerlab.data.StickerRepository
import com.atakan.stickerlab.data.db.PackWithStickers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PackDetailViewModel @Inject constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val repository: StickerRepository,
) : AndroidViewModel(application) {

    val packId: Long = checkNotNull(savedStateHandle["packId"])

    val pack: StateFlow<PackWithStickers?> = repository.observePack(packId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Taşıma/kopyalama hedefi olabilecek diğer packler. */
    val otherPacks: StateFlow<List<PackWithStickers>> = repository.observePacks()
        .map { packs -> packs.filter { it.pack.id != packId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val selectionMode: Boolean get() = _selection.value.isNotEmpty()

    fun stickerFile(fileName: String): File {
        val identifier = pack.value?.pack?.identifier ?: return File("")
        return StickerFiles.stickerFile(getApplication(), identifier, fileName)
    }

    // --- seçim ---

    fun toggleSelection(stickerId: Long) {
        _selection.value = _selection.value.let { current ->
            if (stickerId in current) current - stickerId else current + stickerId
        }
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    /**
     * Seçili sticker'lar hedef pack'e taşınabilir mi. Taşıma kaynağı bozabildiği
     * için buton doğrudan kapatılıyor; sebebi [moveBlockedReason] söylüyor.
     */
    fun canMoveTo(target: PackWithStickers): Boolean {
        val count = _selection.value.size
        val sourceCount = pack.value?.stickers?.size ?: return false
        return PackRules.canMoveOut(sourceCount, count) &&
            PackRules.canAccept(target.stickers.size, count)
    }

    fun canCopyTo(target: PackWithStickers): Boolean =
        PackRules.canAccept(target.stickers.size, _selection.value.size)

    fun moveBlockedReason(target: PackWithStickers): String? {
        val count = _selection.value.size
        val sourceCount = pack.value?.stickers?.size ?: return null
        PackRules.moveBlockedReason(sourceCount, count)?.let { return it }
        if (!PackRules.canAccept(target.stickers.size, count)) {
            return "Hedef pakette yer yok (${target.stickers.size}/${PackRules.MAX_STICKERS})."
        }
        return null
    }

    // --- işlemler ---

    fun move(targetPackId: Long) = run("Taşındı") {
        repository.moveStickers(_selection.value.toList(), targetPackId)
    }

    fun copy(targetPackId: Long) = run("Kopyalandı") {
        repository.copyStickers(_selection.value.toList(), targetPackId)
    }

    fun deleteSelected() = run("Silindi") {
        repository.deleteStickers(_selection.value.toList())
    }

    fun setTray(stickerId: Long) = run("Tray ikonu güncellendi") {
        repository.setTrayFromSticker(stickerId)
    }

    fun updateEmojis(stickerId: Long, emojis: String, accessibilityText: String) =
        run(null) { repository.updateStickerMeta(stickerId, emojis, accessibilityText) }

    fun rename(name: String) = run(null) { repository.renamePack(packId, name) }

    fun deletePack(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.deletePack(packId)
                onDone()
            } catch (e: Exception) {
                Log.e(TAG, "Pack silinemedi", e)
                _message.value = e.message
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private inline fun run(
        successMessage: String?,
        crossinline block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                block()
                clearSelection()
                if (successMessage != null) _message.value = successMessage
            } catch (e: Exception) {
                Log.e(TAG, "İşlem başarısız", e)
                _message.value = e.message ?: "İşlem başarısız"
            }
        }
    }

    private companion object {
        const val TAG = "PackDetailVM"
    }
}
