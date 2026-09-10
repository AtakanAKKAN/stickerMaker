package com.atakan.stickerlab.ui.editor

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Jest katmanı. Motor testleri (`StickerEditorStateTest`) doğru API'nin doğru
 * çalıştığını gösteriyor; buradaki testler **parmağın** doğru API'yi çağırdığını
 * gösteriyor — bildirilen "metni taşımaya çalışınca kopyalanıyor" hatası tam
 * olarak bu katmandaydı.
 *
 * Compose'un kendi dokunuş enjeksiyonunu kullanıyor, sistemin `input` servisini
 * değil; MIUI'nin engellediği izinlere takılmıyor.
 */
@RunWith(AndroidJUnit4::class)
class StickerEditorGestureTest {

    @get:Rule
    val rule = createComposeRule()

    private fun editorState(): StickerEditorState {
        val original = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(Color.RED) }
        return StickerEditorState(original, StickerEditorState.opaqueMask(400, 400))
    }

    private fun show(state: StickerEditorState) {
        rule.setContent {
            StickerEditorScreen(state = state, onApply = {}, onCancel = {})
        }
    }

    @Test
    fun secili_metni_surukleyince_kopyasi_olusmuyor() {
        val state = editorState()
        state.tool = StickerEditorState.Tool.Text
        show(state)

        rule.runOnIdle { state.placeText("A", 200f, 200f) }

        rule.onNodeWithTag(EDITOR_CANVAS_TAG).performTouchInput {
            down(center)
            moveTo(center + Offset(60f, 60f))
            up()
        }

        rule.runOnIdle {
            // Tek geri alma metni tamamen kaldırmalı: sürükleme yeni bir kayıt
            // oluşturmuş olsaydı bir kopya daha kalırdı.
            state.undo()
            assertFalse("sürükleme geçmişe fazladan kayıt eklememeli", state.canUndo)
        }
    }

    @Test
    fun secili_metin_varken_surukleme_metni_tasiyor() {
        val state = editorState()
        state.tool = StickerEditorState.Tool.Text
        show(state)

        rule.runOnIdle { state.placeText("A", 200f, 200f) }
        val before = rule.runOnIdle { state.selectedStampBounds()!!.copyOf() }

        rule.onNodeWithTag(EDITOR_CANVAS_TAG).performTouchInput {
            down(center)
            moveTo(center + Offset(80f, 0f))
            up()
        }

        rule.runOnIdle {
            val after = state.selectedStampBounds()!!
            assertNotEquals("metin sağa kaymalıydı", before[0], after[0])
            assertTrue("sağa doğru gitmeliydi", after[0] > before[0])
        }
    }

    /**
     * Metin seçili değilken tuval eskisi gibi davranmalı: tek parmak çizer.
     */
    @Test
    fun metin_secili_degilken_tek_parmak_maskeye_ciziyor() {
        val state = editorState()
        state.tool = StickerEditorState.Tool.Erase
        show(state)

        rule.onNodeWithTag(EDITOR_CANVAS_TAG).performTouchInput {
            down(center)
            moveTo(center + Offset(40f, 40f))
            up()
        }

        rule.runOnIdle {
            assertTrue("silme darbesi kaydedilmeliydi", state.canUndo)
        }
    }

    /** Seçim bırakıldıktan sonra metne dokunmak onu geri seçmeli. */
    @Test
    fun birakilan_metne_dokununca_tekrar_seciliyor() {
        val state = editorState()
        state.tool = StickerEditorState.Tool.Text
        show(state)

        // Görsel 400x400 ve tuvale ortalanıyor; görselin ortası ekranın ortası.
        rule.runOnIdle {
            state.textSizePx = 120f
            state.placeText("A", 200f, 200f)
            state.deselectStamp()
        }

        rule.onNodeWithTag(EDITOR_CANVAS_TAG).performTouchInput {
            down(center)
            up()
        }

        rule.runOnIdle {
            assertTrue("metne dokunmak onu seçmeliydi", state.hasSelectedStamp)
        }
    }

    @Test
    fun bosluga_dokununca_secim_birakiliyor() {
        val state = editorState()
        state.tool = StickerEditorState.Tool.Text
        show(state)

        rule.runOnIdle {
            state.textSizePx = 60f
            state.placeText("A", 200f, 200f)
        }

        rule.onNodeWithTag(EDITOR_CANVAS_TAG).performTouchInput {
            down(topLeft + Offset(8f, 8f))
            up()
        }

        rule.runOnIdle {
            assertFalse("boşluğa dokunmak seçimi bırakmalı", state.hasSelectedStamp)
        }
    }

    @Test
    fun metin_araci_secili_ama_metin_yokken_tuval_bos_kaliyor() {
        val state = editorState()
        state.tool = StickerEditorState.Tool.Text
        show(state)

        rule.onNodeWithTag(EDITOR_CANVAS_TAG).performTouchInput {
            down(center)
            moveTo(center + Offset(40f, 40f))
            up()
        }

        rule.runOnIdle {
            assertFalse("metin aracı tuvale çizmemeli", state.canUndo)
        }
    }
}
