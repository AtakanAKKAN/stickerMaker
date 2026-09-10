package com.atakan.stickerlab.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Maske motorunun davranışı. Sil / geri getir / undo aynı maske üzerinde
 * çalıştığı için hepsi burada birlikte doğrulanıyor.
 */
@RunWith(AndroidJUnit4::class)
class StickerEditorStateTest {

    private val size = 200
    private val center = 100f
    private val radius = 20f

    @Test
    fun silme_fircasi_pikselleri_saydam_yapiyor() {
        val state = newState()

        state.tool = StickerEditorState.Tool.Erase
        stroke(state)

        val result = state.buildResult()
        assertEquals("fırça altındaki piksel silinmeli", 0, alphaAt(result, 100, 100))
        assertEquals("fırça dışı dokunulmamalı", 255, alphaAt(result, 10, 10))
        assertEquals("renk korunmalı", Color.RED, result.getPixel(10, 10))
    }

    @Test
    fun geri_getirme_fircasi_orijinal_pikselleri_geri_koyuyor() {
        val state = newState()

        state.tool = StickerEditorState.Tool.Erase
        stroke(state)
        state.tool = StickerEditorState.Tool.Restore
        stroke(state)

        val result = state.buildResult()
        assertEquals(255, alphaAt(result, 100, 100))
        assertEquals("geri gelen piksel orijinalin rengi olmalı", Color.RED, result.getPixel(100, 100))
    }

    /**
     * Asıl senaryo: arka plan silindikten sonra modelin yediği bir parçayı
     * fırçayla geri getirmek. Maske başlangıçta kesim sonucundan geliyor.
     */
    @Test
    fun kesim_sonrasi_maske_baslangici_dogru_ve_geri_getirilebiliyor() {
        val original = solidBitmap(Color.RED)
        // Kesim sonucu: sadece sol yarı kalmış.
        val cutout = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(cutout).drawRect(
            0f, 0f, size / 2f, size.toFloat(),
            Paint().apply { color = Color.RED },
        )

        val state = StickerEditorState(original, StickerEditorState.maskFromAlpha(cutout))

        val before = state.buildResult()
        assertEquals("sol yarı görünür kalmalı", 255, alphaAt(before, 50, 100))
        assertEquals("sağ yarı silinmiş olmalı", 0, alphaAt(before, 150, 100))

        state.tool = StickerEditorState.Tool.Restore
        state.startStroke(150f, 100f, radius)
        state.endStroke()

        val after = state.buildResult()
        assertEquals("fırçayla geri getirilmeli", 255, alphaAt(after, 150, 100))
        assertEquals(Color.RED, after.getPixel(150, 100))
    }

    @Test
    fun undo_ve_redo_calisiyor() {
        val state = newState()
        assertFalse(state.canUndo)

        state.tool = StickerEditorState.Tool.Erase
        stroke(state)
        assertTrue(state.canUndo)
        assertEquals(0, alphaAt(state.buildResult(), 100, 100))

        state.undo()
        assertEquals("undo silmeyi geri almalı", 255, alphaAt(state.buildResult(), 100, 100))
        assertFalse(state.canUndo)
        assertTrue(state.canRedo)

        state.redo()
        assertEquals("redo silmeyi tekrar uygulamalı", 0, alphaAt(state.buildResult(), 100, 100))
    }

    @Test
    fun yeni_darbe_redo_yigitini_temizliyor() {
        val state = newState()
        state.tool = StickerEditorState.Tool.Erase
        stroke(state)
        state.undo()
        assertTrue(state.canRedo)

        stroke(state)

        assertFalse("undo sonrası yeni darbe redo'yu geçersiz kılmalı", state.canRedo)
    }

    /**
     * İkinci parmak indiğinde çağrılıyor: zoom yapmak isteyen kullanıcı
     * istemeden boya bırakmamalı.
     */
    @Test
    fun iptal_edilen_darbe_iz_birakmiyor() {
        val state = newState()
        state.tool = StickerEditorState.Tool.Erase

        state.startStroke(center, center, radius)
        state.extendStroke(center + 10, center)
        state.cancelStroke()

        assertEquals("iptal edilen darbe silinmeli", 255, alphaAt(state.buildResult(), 100, 100))
        assertFalse("geçmişe yazılmamalı", state.canUndo)
    }

    // --- lasso ---

    @Test
    fun lasso_cizilen_alanin_icini_siliyor() {
        val state = newState()
        state.tool = StickerEditorState.Tool.Erase
        state.mode = StickerEditorState.Mode.Lasso

        lassoRect(state, 40f, 40f, 120f, 120f)

        val result = state.buildResult()
        assertEquals("alan içi silinmeli", 0, alphaAt(result, 80, 80))
        assertEquals("alan dışı dokunulmamalı", 255, alphaAt(result, 170, 170))
    }

    @Test
    fun lasso_geri_getirme_araciyla_da_calisiyor() {
        val state = newState()
        state.tool = StickerEditorState.Tool.Erase
        stroke(state)
        assertEquals(0, alphaAt(state.buildResult(), 100, 100))

        state.tool = StickerEditorState.Tool.Restore
        state.mode = StickerEditorState.Mode.Lasso
        lassoRect(state, 40f, 40f, 160f, 160f)

        val result = state.buildResult()
        assertEquals("lasso geri getirmeli", 255, alphaAt(result, 100, 100))
        assertEquals(Color.RED, result.getPixel(100, 100))
    }

    /** Lasso maskeye ancak parmak kalkınca uygulanır. */
    @Test
    fun lasso_parmak_kalkana_kadar_maskeye_dokunmuyor() {
        val state = newState()
        state.tool = StickerEditorState.Tool.Erase
        state.mode = StickerEditorState.Mode.Lasso

        // Üçgen: (40,40) - (120,40) - (120,120). İç nokta (100,60);
        // (80,80) köşegenin tam üstüne denk geldiği için kenar yumuşatma
        // yüzünden yarı saydam çıkar, ölçüm için uygun değil.
        state.startStroke(40f, 40f, 0f)
        state.extendStroke(120f, 40f)
        state.extendStroke(120f, 120f)

        assertEquals("henüz uygulanmamalı", 255, alphaAt(state.buildResult(), 100, 60))
        assertTrue("çizim hattı önizlenmeli", state.lassoPreview!!.size == 3)

        state.endStroke()

        assertEquals("parmak kalkınca uygulanmalı", 0, alphaAt(state.buildResult(), 100, 60))
        assertEquals("önizleme temizlenmeli", null, state.lassoPreview)
    }

    @Test
    fun alan_kapatmayan_lasso_yok_sayiliyor() {
        val state = newState()
        state.mode = StickerEditorState.Mode.Lasso

        state.startStroke(40f, 40f, 0f)
        state.endStroke()

        assertEquals(255, alphaAt(state.buildResult(), 40, 40))
        assertFalse("geçmişe yazılmamalı", state.canUndo)
    }

    @Test
    fun lasso_undo_ile_geri_alinabiliyor() {
        val state = newState()
        state.tool = StickerEditorState.Tool.Erase
        state.mode = StickerEditorState.Mode.Lasso
        lassoRect(state, 40f, 40f, 120f, 120f)

        state.undo()

        assertEquals(255, alphaAt(state.buildResult(), 80, 80))
    }

    // --- overlay: kalem, metin, emoji ---

    @Test
    fun kalem_overlaye_renk_ekliyor() {
        val state = newState()
        state.tool = StickerEditorState.Tool.Pen
        state.penColor = Color.BLUE
        stroke(state)

        val result = state.buildResult()
        assertEquals("kalem rengi görünmeli", Color.BLUE, result.getPixel(100, 100))
        assertEquals("dokunulmayan yer fotoğraf olmalı", Color.RED, result.getPixel(10, 10))
    }

    /**
     * Maske "fotoğrafın neresi görünsün", overlay "üstüne ne çizildi" sorusunu
     * yanıtlıyor. Silgi fotoğrafı siliyor, kalem izi üstte kalıyor.
     */
    @Test
    fun silgi_kalem_izini_silmiyor() {
        val state = newState()
        state.tool = StickerEditorState.Tool.Pen
        state.penColor = Color.BLUE
        stroke(state)

        state.tool = StickerEditorState.Tool.Erase
        stroke(state)

        val result = state.buildResult()
        assertEquals("kalem izi kalmalı", Color.BLUE, result.getPixel(100, 100))
    }

    @Test
    fun kalem_undo_ile_gidiyor() {
        val state = newState()
        state.tool = StickerEditorState.Tool.Pen
        state.penColor = Color.BLUE
        stroke(state)

        state.undo()

        assertEquals("fotoğraf geri gelmeli", Color.RED, state.buildResult().getPixel(100, 100))
    }

    @Test
    fun metin_yerlestiriliyor_ve_secili_kaliyor() {
        val state = newState()
        state.tool = StickerEditorState.Tool.Text
        state.textSizePx = 80f

        state.placeText("A", center, center)

        assertTrue("metin overlay'e çizilmeli", hasContent(state, 60, 40, 140, 120))
        assertTrue("taşınabilmesi için seçili kalmalı", state.hasSelectedStamp)
        assertTrue(state.canUndo)
    }

    /**
     * Yaşanan hata: metni taşımak için tuvale her dokunuşta yeni bir kopyası
     * ekleniyordu. Artık yerleştirilen metin seçili kalıyor ve sürüklenerek
     * taşınıyor; geçmişte tek bir kayıt oluyor.
     */
    @Test
    fun metin_tasimak_kopya_olusturmuyor() {
        val state = newState()
        state.tool = StickerEditorState.Tool.Text
        state.textSizePx = 60f
        state.placeText("A", 50f, 50f)

        state.dragSelectedStamp(50f, 50f)
        state.dragSelectedStamp(50f, 50f)

        assertFalse("ilk konumda iz kalmamalı", hasContent(state, 20, 10, 70, 60))
        assertTrue("son konumda olmalı", hasContent(state, 120, 110, 180, 160))

        state.undo()
        assertFalse("tek geri alma metni tamamen kaldırmalı", hasContent(state, 0, 0, 200, 200))
        assertFalse(state.canUndo)
    }

    @Test
    fun iki_parmak_secili_metni_boyutlandiriyor() {
        val state = newState()
        state.tool = StickerEditorState.Tool.Text
        state.textSizePx = 60f
        state.placeText("A", center, center)
        val before = state.selectedStampBounds()!!
        val beforeWidth = before[2] - before[0]

        state.scaleSelectedStamp(2f)

        val after = state.selectedStampBounds()!!
        assertTrue(
            "metin büyümeliydi: $beforeWidth -> ${after[2] - after[0]}",
            after[2] - after[0] > beforeWidth,
        )
        assertEquals("slider da güncel değeri göstermeli", 120f, state.textSizePx, 0.5f)
    }

    /** Metin aracından çıkınca seçim bırakılır; iki parmak yine tuvali yakınlaştırır. */
    @Test
    fun arac_degisince_secim_birakiliyor() {
        val state = newState()
        state.tool = StickerEditorState.Tool.Text
        state.placeText("A", center, center)
        assertTrue(state.hasSelectedStamp)

        state.tool = StickerEditorState.Tool.Erase

        assertFalse(state.hasSelectedStamp)
        assertTrue("metin silinmemeli, sadece seçim bırakılmalı", hasContent(state, 0, 0, 200, 200))
    }

    @Test
    fun birakilan_metne_dokununca_tekrar_seciliyor() {
        val state = newState()
        state.tool = StickerEditorState.Tool.Text
        state.textSizePx = 80f
        state.placeText("A", center, center)
        state.deselectStamp()
        assertFalse(state.hasSelectedStamp)

        val selected = state.selectStampAt(center, center)

        assertTrue("metnin üstüne dokunmak onu seçmeli", selected)
        assertTrue(state.hasSelectedStamp)
    }

    @Test
    fun bosluga_dokununca_secim_birakiliyor() {
        val state = newState()
        state.tool = StickerEditorState.Tool.Text
        state.textSizePx = 40f
        state.placeText("A", center, center)

        val selected = state.selectStampAt(5f, 5f)

        assertFalse("boşlukta metin yok", selected)
        assertFalse("seçim bırakılmalı", state.hasSelectedStamp)
    }

    /** Üst üste binen metinlerde en son eklenen (en üstte çizilen) kazanır. */
    @Test
    fun ust_uste_binen_metinlerde_ustteki_seciliyor() {
        val state = newState()
        state.tool = StickerEditorState.Tool.Text
        state.textSizePx = 80f
        state.placeText("A", center, center)
        state.placeText("B", center, center)
        state.deselectStamp()

        state.selectStampAt(center, center)
        state.dragSelectedStamp(0f, 0f)
        state.undo()

        // Ustteki ("B") secildiyse undo onu kaldirir, alttaki "A" kalir.
        assertTrue("alttaki metin durmalı", hasContent(state, 0, 0, 200, 200))
    }

    @Test
    fun secilen_metnin_boyutu_slidera_yansiyor() {
        val state = newState()
        state.tool = StickerEditorState.Tool.Text
        state.textSizePx = 60f
        state.placeText("A", center, center)
        state.deselectStamp()
        state.textSizePx = 200f

        state.selectStampAt(center, center)

        assertEquals("slider seçilen metnin boyutunu göstermeli", 60f, state.textSizePx, 0.5f)
    }

    @Test
    fun bos_metin_yerlestirilmiyor() {
        val state = newState()
        state.tool = StickerEditorState.Tool.Text

        state.placeText("", center, center)

        assertFalse(state.canUndo)
        assertFalse(state.hasSelectedStamp)
    }

    @Test
    fun dokunulmamis_editor_kirli_degil() {
        val state = newState()

        assertFalse(state.isDirty)

        stroke(state)
        assertTrue(state.isDirty)
    }

    // --- yardımcılar ---

    private fun newState(): StickerEditorState {
        val original = solidBitmap(Color.RED)
        return StickerEditorState(original, StickerEditorState.opaqueMask(size, size))
    }

    private fun stroke(state: StickerEditorState) {
        state.startStroke(center, center, radius)
        state.endStroke()
    }

    /** Lasso ile dikdörtgen bir alan çizer. */
    private fun lassoRect(state: StickerEditorState, left: Float, top: Float, right: Float, bottom: Float) {
        state.startStroke(left, top, 0f)
        state.extendStroke(right, top)
        state.extendStroke(right, bottom)
        state.extendStroke(left, bottom)
        state.endStroke()
    }

    private fun solidBitmap(color: Int): Bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private fun alphaAt(bitmap: Bitmap, x: Int, y: Int): Int = Color.alpha(bitmap.getPixel(x, y))

    /** Overlay'de verilen dikdörtgen içinde saydam olmayan piksel var mı. */
    private fun hasContent(
        state: StickerEditorState,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): Boolean {
        for (y in top until bottom) {
            for (x in left until right) {
                if (Color.alpha(state.overlay.getPixel(x, y)) > 16) return true
            }
        }
        return false
    }
}
