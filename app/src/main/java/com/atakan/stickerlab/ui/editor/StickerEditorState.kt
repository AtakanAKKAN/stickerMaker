package com.atakan.stickerlab.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Sticker düzenleme motoru. İki katman üzerinde çalışır:
 *
 *  - **Maske**: orijinal fotoğrafın hangi pikselinin görüneceğini söyler. Sil ve
 *    geri getir araçları burayı boyar; geri getirmenin arka planı gerçekten geri
 *    getirebilmesi buna bağlı.
 *  - **Overlay**: orijinalde olmayan pikseller — renkli kalem, metin, emoji.
 *    Maskeden bağımsız, en üstte duruyor.
 *
 * Sonuç = (orijinal ∩ maske) + overlay.
 *
 * Silgi overlay'e dokunmuyor: maske "fotoğrafın neresi görünsün", overlay
 * "üstüne ne çizildi" sorusunu yanıtlıyor. Kalem hatası geri alma ile düzeltilir.
 *
 * Tüm işlemler tek bir sıralı listede duruyor; undo listenin sonunu atıp
 * katmanları baştan çiziyor. Snapshot tutmaya göre bellek maliyeti ihmal
 * edilebilir (1280² ARGB snapshot adım başına ~5 MB tutardı).
 */
class StickerEditorState(
    /** Dokunulmamış fotoğraf. Geri getirme fırçası bunun piksellerini geri koyar. */
    val original: Bitmap,
    /** Başlangıç maskesi: arka plan silindiyse onun alfası, silinmediyse tamamen opak. */
    baseMask: Bitmap,
) {

    enum class Tool { Erase, Restore, Pen, Text }

    /** Sadece [Tool.Erase] ve [Tool.Restore] için anlamlı. */
    enum class Mode { Brush, Lasso }

    private sealed interface Op {
        /** Maskeye uygulanan fırça ya da lasso. */
        class MaskStroke(
            val erase: Boolean,
            val lasso: Boolean,
            val radius: Float,
            val points: MutableList<Pair<Float, Float>> = mutableListOf(),
        ) : Op

        /** Overlay'e renkli çizgi. */
        class PenStroke(
            val color: Int,
            val radius: Float,
            val points: MutableList<Pair<Float, Float>> = mutableListOf(),
        ) : Op

        /** Overlay'e metin ya da emoji. Konum sürüklenerek değişebilir. */
        class Stamp(
            val text: String,
            val color: Int,
            var size: Float,
            var x: Float,
            var y: Float,
        ) : Op
    }

    private val base: Bitmap = baseMask.copy(Bitmap.Config.ARGB_8888, false)

    val mask: Bitmap = baseMask.copy(Bitmap.Config.ARGB_8888, true)

    /** Fotoğrafın üstüne eklenenler. Başlangıçta tamamen saydam. */
    val overlay: Bitmap =
        Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)

    private val maskCanvas = Canvas(mask)
    private val overlayCanvas = Canvas(overlay)
    private val done = ArrayDeque<Op>()
    private val undone = ArrayDeque<Op>()
    private var active: Op? = null
    private var selected: Op.Stamp? = null

    /** Compose'un yeniden çizmesi için sayaç: bitmap'ler yerinde değişiyor. */
    var revision by mutableIntStateOf(0)
        private set

    private var _tool by mutableStateOf(Tool.Erase)

    /** Metin aracindan cikildiginda secili metin birakilir ve kesinlesir. */
    var tool: Tool
        get() = _tool
        set(value) {
            if (value != Tool.Text) deselectStamp()
            _tool = value
        }

    var mode by mutableStateOf(Mode.Brush)
    var brushRadiusPx by mutableFloatStateOf(DEFAULT_BRUSH_RADIUS)
    var penColor by mutableIntStateOf(PALETTE.first())
    var textSizePx by mutableFloatStateOf(DEFAULT_TEXT_SIZE)

    /** Metin diyalogunda son yazilan icerik; tekrar acildiginda hazir gelsin diye. */
    var pendingText by mutableStateOf<String?>(null)

    var lassoPreview by mutableStateOf<List<Pair<Float, Float>>?>(null)
        private set

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    val isDirty: Boolean get() = done.isNotEmpty()

    /** Secili (tasinabilir/boyutlandirilabilir) bir metin var mi. */
    var hasSelectedStamp by mutableStateOf(false)
        private set

    // --- çizim ---

    fun startStroke(x: Float, y: Float, radiusInImagePixels: Float) {
        val op: Op? = when (tool) {
            Tool.Erase, Tool.Restore -> Op.MaskStroke(
                erase = tool == Tool.Erase,
                lasso = mode == Mode.Lasso,
                radius = radiusInImagePixels,
            ).apply { points += x to y }

            Tool.Pen -> Op.PenStroke(
                color = penColor,
                radius = radiusInImagePixels,
            ).apply { points += x to y }

            // Metin aracı tuvale çizmiyor: metin diyalogda onaylanınca
            // yerleşiyor, sonrasında sürüklenip boyutlandırılıyor.
            Tool.Text -> null
        }
        active = op ?: return

        when (op) {
            is Op.MaskStroke -> if (op.lasso) {
                lassoPreview = op.points.toList()
            } else {
                drawOp(op)
                bump()
            }

            is Op.PenStroke -> {
                drawOp(op)
                bump()
            }

            is Op.Stamp -> {
                drawOp(op)
                bump()
            }
        }
    }

    fun extendStroke(x: Float, y: Float) {
        when (val op = active) {
            null -> return

            is Op.MaskStroke -> {
                op.points += x to y
                if (op.lasso) {
                    // Lasso bırakılana kadar maskeye dokunmuyor.
                    lassoPreview = op.points.toList()
                } else {
                    drawSegment(
                        op.points,
                        brushPaint(op.radius, maskPaintColor(op.erase), srcMode = true),
                        maskCanvas,
                    )
                    bump()
                }
            }

            is Op.PenStroke -> {
                op.points += x to y
                drawSegment(op.points, brushPaint(op.radius, op.color, srcMode = false), overlayCanvas)
                bump()
            }

            is Op.Stamp -> {
                // Yerleştirdikten sonra parmağını kaldırmadan sürükleyerek konumlandır.
                op.x = x
                op.y = y
                rebuildOverlay()
            }
        }
    }

    fun endStroke() {
        val op = active ?: return
        active = null
        lassoPreview = null

        if (op is Op.MaskStroke && op.lasso) {
            if (op.points.size < MIN_LASSO_POINTS) return
            drawOp(op)
            bump()
        }

        done.addLast(op)
        undone.clear()
        updateHistoryFlags()
    }

    // --- metin ---

    /**
     * Metni verilen noktaya yerleştirir ve **seçili bırakır**.
     *
     * Seçili kaldığı sürece tek parmak taşır, iki parmak boyutlandırır. Eskiden
     * "yaz, sonra tuvale dokunarak yerleştir" akışı vardı; taşımak isteyen her
     * dokunuş metnin bir kopyasını daha ekliyordu.
     */
    fun placeText(text: String, x: Float, y: Float) {
        if (text.isEmpty()) return
        deselectStamp()
        val stamp = Op.Stamp(text = text, color = penColor, size = textSizePx, x = x, y = y)
        done.addLast(stamp)
        undone.clear()
        selected = stamp
        hasSelectedStamp = true
        updateHistoryFlags()
        rebuildOverlay()
    }

    fun dragSelectedStamp(dx: Float, dy: Float) {
        val stamp = selected ?: return
        stamp.x += dx
        stamp.y += dy
        rebuildOverlay()
    }

    /** İki parmak jesti metni büyütür/küçültür — tuvali yakınlaştırmaz. */
    fun scaleSelectedStamp(factor: Float) {
        val stamp = selected ?: return
        stamp.size = (stamp.size * factor).coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)
        textSizePx = stamp.size
        rebuildOverlay()
    }

    /** Slider da seçili metni doğrudan boyutlandırır. */
    fun setTextSize(size: Float) {
        textSizePx = size
        selected?.let {
            it.size = size
            rebuildOverlay()
        }
    }

    fun deselectStamp() {
        if (selected == null) return
        selected = null
        hasSelectedStamp = false
    }

    /** Seçili metnin görsel uzayındaki sınırları; seçim çerçevesi için. */
    fun selectedStampBounds(): FloatArray? = selected?.let { boundsOf(it) }

    /** Dokunulan nokta seçili metnin üstünde mi. */
    fun isPointOnSelectedStamp(x: Float, y: Float): Boolean {
        val stamp = selected ?: return false
        return contains(boundsOf(stamp), stamp.size, x, y)
    }

    /**
     * Dokunulan noktadaki metni seçer.
     *
     * Üst üste binen metinlerde en son eklenen (yani en üstte çizilen) kazanır.
     * Boşluğa dokunmak seçimi bırakır.
     *
     * @return bir metin seçildiyse true.
     */
    fun selectStampAt(x: Float, y: Float): Boolean {
        val hit = done.asReversed()
            .filterIsInstance<Op.Stamp>()
            .firstOrNull { contains(boundsOf(it), it.size, x, y) }

        if (hit == null) {
            deselectStamp()
            return false
        }
        selected = hit
        hasSelectedStamp = true
        textSizePx = hit.size
        return true
    }

    private fun boundsOf(stamp: Op.Stamp): FloatArray {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = stamp.size
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val halfWidth = paint.measureText(stamp.text) / 2f + stamp.size * 0.1f
        val metrics = paint.fontMetrics
        return floatArrayOf(
            stamp.x - halfWidth,
            stamp.y + metrics.ascent,
            stamp.x + halfWidth,
            stamp.y + metrics.descent,
        )
    }

    /**
     * Parmak ucu metnin tam sınırına isabet etmek zorunda değil; küçük metinleri
     * yakalayabilmek için boyutla orantılı bir pay bırakılıyor.
     */
    private fun contains(bounds: FloatArray, stampSize: Float, x: Float, y: Float): Boolean {
        val tolerance = stampSize * HIT_TOLERANCE_RATIO
        return x >= bounds[0] - tolerance && x <= bounds[2] + tolerance &&
            y >= bounds[1] - tolerance && y <= bounds[3] + tolerance
    }

    /**
     * İkinci parmak değince çağrılır: zoom yapmak isteyen kullanıcı istemeden
     * iz bırakmasın diye başlamış işlem geri sarılır.
     */
    fun cancelStroke() {
        if (active == null) return
        active = null
        lassoPreview = null
        rebuildAll()
    }

    // --- geçmiş ---

    fun undo() {
        if (done.isEmpty()) return
        deselectStamp()
        undone.addLast(done.removeLast())
        rebuildAll()
        updateHistoryFlags()
    }

    fun redo() {
        if (undone.isEmpty()) return
        deselectStamp()
        done.addLast(undone.removeLast())
        rebuildAll()
        updateHistoryFlags()
    }

    /** Maskeyi ve overlay'i orijinale uygulayıp kaydedilecek görseli üretir. */
    fun buildResult(): Bitmap {
        val output = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(original, 0f, 0f, null)
        canvas.drawBitmap(mask, 0f, 0f, Paint().apply {
            // DST_IN: hedefi (fotoğraf) kaynağın (maske) alfasıyla çarpar.
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        })
        canvas.drawBitmap(overlay, 0f, 0f, null)
        return output
    }

    // --- çizim iç kısmı ---

    private fun rebuildAll() {
        maskCanvas.drawBitmap(base, 0f, 0f, SRC_PAINT)
        overlay.eraseColor(Color.TRANSPARENT)
        done.forEach { drawOp(it) }
        bump()
    }

    /** Sadece overlay değiştiyse maskeyi baştan çizmeye gerek yok. */
    private fun rebuildOverlay() {
        overlay.eraseColor(Color.TRANSPARENT)
        done.forEach { if (it !is Op.MaskStroke) drawOp(it) }
        active?.let { if (it !is Op.MaskStroke) drawOp(it) }
        bump()
    }

    private fun drawOp(op: Op) {
        when (op) {
            is Op.MaskStroke -> {
                val color = maskPaintColor(op.erase)
                if (op.lasso) {
                    if (op.points.size < MIN_LASSO_POINTS) return
                    maskCanvas.drawPath(
                        op.points.toPath(close = true),
                        fillPaint(color, srcMode = true),
                    )
                } else {
                    drawFreehand(op.points, op.radius, color, maskCanvas, srcMode = true)
                }
            }

            is Op.PenStroke -> drawFreehand(op.points, op.radius, op.color, overlayCanvas, srcMode = false)

            is Op.Stamp -> drawStamp(op)
        }
    }

    private fun drawFreehand(
        points: List<Pair<Float, Float>>,
        radius: Float,
        color: Int,
        canvas: Canvas,
        srcMode: Boolean,
    ) {
        if (points.size == 1) {
            val (x, y) = points.first()
            canvas.drawCircle(x, y, radius, fillPaint(color, srcMode))
        } else {
            canvas.drawPath(points.toPath(close = false), brushPaint(radius, color, srcMode))
        }
    }

    private fun drawSegment(points: List<Pair<Float, Float>>, paint: Paint, canvas: Canvas) {
        val size = points.size
        if (size < 2) return
        val (x1, y1) = points[size - 2]
        val (x2, y2) = points[size - 1]
        canvas.drawLine(x1, y1, x2, y2, paint)
    }

    private fun drawStamp(stamp: Op.Stamp) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = stamp.size
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        // Sticker metni her zeminde okunmalı; harf içeren metne kontrast kontur
        // çiziliyor. Emoji'ye kontur çirkin durduğu için atlanıyor.
        if (stamp.text.any { it.isLetterOrDigit() }) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stamp.size * OUTLINE_RATIO
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = contrastColor(stamp.color)
            overlayCanvas.drawText(stamp.text, stamp.x, stamp.y, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = stamp.color
        overlayCanvas.drawText(stamp.text, stamp.x, stamp.y, paint)
    }

    private fun List<Pair<Float, Float>>.toPath(close: Boolean): Path = Path().also { path ->
        forEachIndexed { index, (x, y) ->
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (close) path.close()
    }

    private fun brushPaint(radius: Float, color: Int, srcMode: Boolean) =
        paintFor(color, srcMode).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = radius * 2f
        }

    private fun fillPaint(color: Int, srcMode: Boolean) =
        paintFor(color, srcMode).apply { style = Paint.Style.FILL }

    /**
     * @param srcMode maske boyaları hedefi **değiştirir** (SRC): silmek alfayı 0,
     *   geri getirmek 255 yapmalı. Renkli kalem ise normal harmanlanır, yoksa
     *   üst üste çizilen çizgiler birbirini keserdi.
     */
    private fun paintFor(color: Int, srcMode: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        if (srcMode) {
            // SRC + antialias: kenar pikselleri kapsama oranınca harmanlanır,
            // yani kenar sert değil yumuşak olur.
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
        }
    }

    private fun maskPaintColor(erase: Boolean) = if (erase) Color.TRANSPARENT else Color.WHITE

    private fun updateHistoryFlags() {
        canUndo = done.isNotEmpty()
        canRedo = undone.isNotEmpty()
    }

    private fun bump() {
        revision++
    }

    companion object {
        const val DEFAULT_BRUSH_RADIUS = 40f
        const val MIN_BRUSH_RADIUS = 8f
        const val MAX_BRUSH_RADIUS = 160f

        const val DEFAULT_TEXT_SIZE = 120f
        const val MIN_TEXT_SIZE = 32f
        const val MAX_TEXT_SIZE = 400f

        private const val OUTLINE_RATIO = 0.14f

        /** Metne dokunma payı, metin boyutuna oranla. */
        private const val HIT_TOLERANCE_RATIO = 0.25f
        private const val MIN_LASSO_POINTS = 3

        /** Sticker'da işe yarayan, birbirinden ayırt edilebilir renkler. */
        val PALETTE = listOf(
            0xFFFFFFFF.toInt(),
            0xFF000000.toInt(),
            0xFFE53935.toInt(),
            0xFFFB8C00.toInt(),
            0xFFFDD835.toInt(),
            0xFF43A047.toInt(),
            0xFF1E88E5.toInt(),
            0xFF8E24AA.toInt(),
            0xFFEC407A.toInt(),
        )

        private val SRC_PAINT = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
        }

        /** Açık renkte siyah, koyu renkte beyaz kontur. */
        fun contrastColor(color: Int): Int {
            val luminance = 0.299 * Color.red(color) +
                0.587 * Color.green(color) +
                0.114 * Color.blue(color)
            return if (luminance > 140) Color.BLACK else Color.WHITE
        }

        /** Arka plan silinmemişse: her piksel görünür. */
        fun opaqueMask(width: Int, height: Int): Bitmap =
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.WHITE)
            }

        /** Verilen görselin alfa kanalından maske üretir. */
        fun maskFromAlpha(source: Bitmap): Bitmap {
            val alpha = source.extractAlpha()
            val mask = Bitmap.createBitmap(
                source.width,
                source.height,
                Bitmap.Config.ARGB_8888,
            )
            // ALPHA_8 bitmap çizilirken paint rengi kullanılır, bitmap alfası kapsama olur.
            Canvas(mask).drawBitmap(alpha, 0f, 0f, Paint().apply { color = Color.WHITE })
            alpha.recycle()
            return mask
        }
    }
}
