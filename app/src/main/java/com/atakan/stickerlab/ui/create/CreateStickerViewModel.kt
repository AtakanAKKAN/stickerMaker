package com.atakan.stickerlab.ui.create

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atakan.stickerlab.data.StickerRepository
import com.atakan.stickerlab.imaging.BitmapLoader
import com.atakan.stickerlab.segmentation.SubjectCutout
import com.atakan.stickerlab.ui.editor.StickerEditorState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Fotoğrafa uygulanan hiçbir işlem otomatik değil.
 *
 * Seçilen fotoğraf olduğu gibi gösterilir; kullanıcı isterse hiçbir şey yapmadan
 * kaydeder (fotoğraf sticker formuna çevrilir), isterse arka plan silme ve rötuş
 * gibi işlemleri tek tek uygular. Her işlem geri alınabilir.
 */
@HiltViewModel
class CreateStickerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: StickerRepository,
    private val loader: BitmapLoader,
    private val cutout: SubjectCutout,
) : ViewModel() {

    val packId: Long = checkNotNull(savedStateHandle["packId"])

    /** Uzun süren bir işlem devam ederken önizlemenin üstünde gösterilir. */
    enum class Busy { DownloadingModel, RemovingBackground }

    sealed interface State {
        /** Sistem Photo Picker açık; kullanıcı henüz bir şey seçmedi. */
        data object Picking : State

        data object Loading : State

        /**
         * @param original seçilen fotoğrafın dokunulmamış hâli; geri dönmek için tutuluyor.
         * @param current  şu an önizlenen ve kaydedilecek görsel.
         * @param maskEditor null değilse maske editörü tam ekran açık.
         */
        data class Editing(
            val original: Bitmap,
            val current: Bitmap,
            val backgroundRemoved: Boolean = false,
            val manuallyEdited: Boolean = false,
            val busy: Busy? = null,
            val notice: String? = null,
            val maskEditor: StickerEditorState? = null,
        ) : State {
            val modified: Boolean get() = backgroundRemoved || manuallyEdited
        }

        data object Saving : State

        data object Saved : State

        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Picking)
    val state: StateFlow<State> = _state.asStateFlow()

    fun onPhotoPicked(uri: Uri) {
        _state.value = State.Loading
        viewModelScope.launch {
            try {
                val source = withContext(Dispatchers.IO) { loader.load(uri) }
                _state.value = State.Editing(original = source, current = source)
            } catch (e: Exception) {
                Log.e(TAG, "Görsel yüklenemedi", e)
                _state.value = State.Failed(e.message ?: "Görsel yüklenemedi")
            }
        }
    }

    /** Kullanıcı istediğinde çalışır — fotoğraf seçilir seçilmez değil. */
    fun removeBackground() {
        val editing = _state.value as? State.Editing ?: return
        if (editing.backgroundRemoved || editing.busy != null) return

        viewModelScope.launch {
            try {
                if (!cutout.isModelReady()) {
                    updateEditing { it.copy(busy = Busy.DownloadingModel, notice = null) }
                    cutout.downloadModel()
                }
                updateEditing { it.copy(busy = Busy.RemovingBackground, notice = null) }

                val foreground = cutout.cutout(editing.original)
                if (foreground != null) {
                    updateEditing {
                        it.copy(current = foreground, backgroundRemoved = true, busy = null)
                    }
                } else {
                    updateEditing {
                        it.copy(
                            busy = null,
                            notice = "Belirgin bir özne bulunamadı, fotoğraf değişmedi.",
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Arka plan silinemedi", e)
                updateEditing {
                    it.copy(busy = null, notice = e.message ?: "Arka plan silinemedi.")
                }
            }
        }
    }

    fun resetToOriginal() {
        updateEditing { editing ->
            editing.copy(
                current = editing.original,
                backgroundRemoved = false,
                manuallyEdited = false,
                notice = null,
            )
        }
    }

    // --- maske editörü ---

    /**
     * Editör tam çözünürlükte değil, [MAX_EDIT_DIMENSION] ile sınırlı bir kopya
     * üzerinde çalışır: çıktı zaten 512×512, ama fırça her dokunuşta bitmap'e
     * yazdığı için büyük görselde akıcılık düşüyor.
     */
    fun openMaskEditor() {
        val editing = _state.value as? State.Editing ?: return
        if (editing.busy != null) return

        val scaled = scaleForEditing(editing.original)
        val mask = if (editing.backgroundRemoved) {
            val scaledCurrent = Bitmap.createScaledBitmap(
                editing.current,
                scaled.width,
                scaled.height,
                true,
            )
            StickerEditorState.maskFromAlpha(scaledCurrent).also {
                if (scaledCurrent != editing.current) scaledCurrent.recycle()
            }
        } else {
            StickerEditorState.opaqueMask(scaled.width, scaled.height)
        }

        updateEditing { it.copy(maskEditor = StickerEditorState(scaled, mask)) }
    }

    fun applyMaskEditor() {
        val editor = (_state.value as? State.Editing)?.maskEditor ?: return
        val result = editor.buildResult()
        updateEditing {
            it.copy(current = result, manuallyEdited = true, maskEditor = null)
        }
    }

    fun cancelMaskEditor() {
        updateEditing { it.copy(maskEditor = null) }
    }

    fun dismissNotice() = updateEditing { it.copy(notice = null) }

    fun save() {
        val editing = _state.value as? State.Editing ?: return
        if (editing.busy != null) return

        _state.value = State.Saving
        viewModelScope.launch {
            try {
                repository.addSticker(packId, editing.current)
                _state.value = State.Saved
            } catch (e: Exception) {
                Log.e(TAG, "Sticker kaydedilemedi", e)
                _state.value = State.Failed(e.message ?: "Kaydedilemedi")
            }
        }
    }

    private fun scaleForEditing(source: Bitmap): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= MAX_EDIT_DIMENSION) return source
        val ratio = MAX_EDIT_DIMENSION.toFloat() / longest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private inline fun updateEditing(transform: (State.Editing) -> State.Editing) {
        _state.update { current ->
            if (current is State.Editing) transform(current) else current
        }
    }

    private companion object {
        const val TAG = "CreateStickerVM"
        const val MAX_EDIT_DIMENSION = 1280
    }
}
