package com.atakan.stickerlab.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Sticker editörü. Tam ekran.
 *
 * Jest kuralı: **tek parmak çizer, iki parmak zoom/pan yapar.** Araç değiştirme
 * düğmesi yok — rakiplerin çoğunda "el aracına geç, sonra fırçaya dön" döngüsü
 * en çok zaman kaybettiren şey.
 */
@Composable
fun StickerEditorScreen(
    state: StickerEditorState,
    onApply: () -> Unit,
    onCancel: () -> Unit,
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    var textDialog by remember { mutableStateOf(false) }
    val transform = remember(state) { CanvasTransform() }

    // enableEdgeToEdge açık: bu ekran Scaffold içinde olmadığı için üst ve alt
    // sistem çubuklarının altında kalan butonlar tıklanamıyordu.
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        TopBar(
            canUndo = state.canUndo,
            canRedo = state.canRedo,
            onUndo = state::undo,
            onRedo = state::redo,
            onClose = { if (state.isDirty) confirmDiscard = true else onCancel() },
            onApply = onApply,
        )

        EditorCanvas(
            state = state,
            transform = transform,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        ToolBar(state = state, onEditText = { textDialog = true })
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Değişiklikler silinsin mi?") },
            text = { Text("Bu ekranda yaptıkların kaydedilmeyecek.") },
            confirmButton = { TextButton(onClick = onCancel) { Text("Sil") } },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Vazgeç") }
            },
        )
    }

    if (textDialog) {
        TextDialog(
            initial = state.pendingText.orEmpty(),
            onConfirm = { text ->
                state.pendingText = text
                state.tool = StickerEditorState.Tool.Text
                val center = transform.viewportCenter(
                    Offset(state.original.width / 2f, state.original.height / 2f),
                )
                state.placeText(text, center.x, center.y)
                textDialog = false
            },
            onDismiss = { textDialog = false },
        )
    }
}

@Composable
private fun TopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClose) { Text("Kapat") }
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = onUndo, enabled = canUndo) { Text("Geri al") }
            TextButton(onClick = onRedo, enabled = canRedo) { Text("İleri") }
        }
        Button(onClick = onApply) { Text("Bitti") }
    }
}

/**
 * Tuvalin zoom/pan durumu. Ekran seviyesinde tutuluyor çünkü metin
 * yerleştirilirken "ekranda görünen alanın ortası" bilgisine ihtiyaç var.
 */
class CanvasTransform {
    var scale by mutableFloatStateOf(0f)
    var offset by mutableStateOf(Offset.Zero)
    var canvasSize by mutableStateOf(Size.Zero)

    fun toImageSpace(point: Offset): Offset =
        Offset((point.x - offset.x) / scale, (point.y - offset.y) / scale)

    /** Görünen alanın ortası, görsel uzayında. */
    fun viewportCenter(fallback: Offset): Offset {
        if (scale <= 0f || canvasSize == Size.Zero) return fallback
        return toImageSpace(Offset(canvasSize.width / 2f, canvasSize.height / 2f))
    }
}

@Composable
private fun EditorCanvas(
    state: StickerEditorState,
    transform: CanvasTransform,
    modifier: Modifier = Modifier,
) {
    val originalImage = remember(state) { state.original.asImageBitmap() }
    val maskImage = remember(state) { state.mask.asImageBitmap() }
    val overlayImage = remember(state) { state.overlay.asImageBitmap() }

    val imageWidth = state.original.width
    val imageHeight = state.original.height

    var cursor by remember(state) { mutableStateOf<Offset?>(null) }

    fun fitToCanvas(size: Size) {
        val fit = minOf(size.width / imageWidth, size.height / imageHeight)
        transform.scale = fit
        transform.offset = Offset(
            (size.width - imageWidth * fit) / 2f,
            (size.height - imageHeight * fit) / 2f,
        )
    }

    fun toImageSpace(point: Offset) = transform.toImageSpace(point)

    Box(
        modifier = modifier.testTag(EDITOR_CANVAS_TAG).pointerInput(state) {
            awaitEachGesture {
                val first = awaitFirstDown(requireUnconsumed = false)
                if (transform.scale <= 0f) return@awaitEachGesture

                // Metin aracında tuval çizim yüzeyi değil, metinlerin yüzeyi:
                // metnin üstüne dokunmak onu seçer, boşluğa dokunmak seçimi bırakır.
                // Seçili metin varken jestler tuvale değil metne uygulanır:
                // tek parmak taşır, iki parmak boyutlandırır. Seçim bırakılınca
                // (araç değişince ya da "Bitir") iki parmak yine tuvali yakınlaştırır.
                if (state.tool == StickerEditorState.Tool.Text) {
                    val point = toImageSpace(first.position)
                    val onStamp = state.isPointOnSelectedStamp(point.x, point.y) ||
                        state.selectStampAt(point.x, point.y)
                    if (!onStamp) return@awaitEachGesture
                }

                if (state.hasSelectedStamp) {
                    var previous = first.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        if (pressed.size >= 2) {
                            val zoom = event.calculateZoom()
                            if (zoom != 0f) state.scaleSelectedStamp(zoom)
                            val pan = event.calculatePan()
                            state.dragSelectedStamp(pan.x / transform.scale, pan.y / transform.scale)
                        } else {
                            val position = pressed.first().position
                            val delta = position - previous
                            previous = position
                            state.dragSelectedStamp(delta.x / transform.scale, delta.y / transform.scale)
                        }
                        event.changes.forEach { it.consume() }
                    }
                    return@awaitEachGesture
                }

                var drawing = true
                var transforming = false

                cursor = first.position
                val start = toImageSpace(first.position)
                state.startStroke(start.x, start.y, state.brushRadiusPx / transform.scale)

                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break

                    if (pressed.size >= 2) {
                        // İkinci parmak indi: bu bir zoom jesti, çizim değil.
                        // Başlamış işlemi geri sararak istenmeyen izi siliyoruz.
                        if (drawing) {
                            state.cancelStroke()
                            drawing = false
                            cursor = null
                        }
                        transforming = true

                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        val centroid = event.calculateCentroid()
                        if (zoom != 0f && centroid != Offset.Unspecified) {
                            val newScale = (transform.scale * zoom).coerceIn(
                                minScale(transform.canvasSize, imageWidth, imageHeight),
                                maxScale(transform.canvasSize, imageWidth, imageHeight),
                            )
                            val applied = newScale / transform.scale
                            transform.offset = centroid - (centroid - transform.offset) * applied + pan
                            transform.scale = newScale
                        }
                    } else if (drawing && !transforming) {
                        val position = pressed.first().position
                        cursor = position
                        val point = toImageSpace(position)
                        state.extendStroke(point.x, point.y)
                    }

                    event.changes.forEach { it.consume() }
                }

                if (drawing) state.endStroke()
                cursor = null
            }
        },
    ) {
        // Damalı zemin ayrı katmanda: DstIn uygulanan katmanın içinde olsaydı
        // o da kesilirdi.
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCheckerboard()
            if (transform.canvasSize != size) {
                transform.canvasSize = size
                if (transform.scale <= 0f) fitToCanvas(size)
            }
        }

        // Fotoğraf + maske. Offscreen katman olmadan DstIn altındaki her şeyi keserdi.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            @Suppress("UNUSED_EXPRESSION")
            state.revision
            if (transform.scale <= 0f) return@Canvas
            val destination = destination(transform.offset)
            val destinationSize = destinationSize(imageWidth, imageHeight, transform.scale)
            drawImage(originalImage, dstOffset = destination, dstSize = destinationSize,
                filterQuality = FilterQuality.Low)
            drawImage(maskImage, dstOffset = destination, dstSize = destinationSize,
                blendMode = BlendMode.DstIn, filterQuality = FilterQuality.Low)
        }

        // Overlay maskeden bağımsız: silgi fotoğrafı siliyor, kalem/metin üstte kalıyor.
        Canvas(modifier = Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION")
            state.revision
            if (transform.scale <= 0f) return@Canvas
            drawImage(
                overlayImage,
                dstOffset = destination(transform.offset),
                dstSize = destinationSize(imageWidth, imageHeight, transform.scale),
                filterQuality = FilterQuality.Low,
            )
        }

        // Üst katman: lasso hattı ya da fırça imleci.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val lasso = state.lassoPreview
            if (lasso != null && transform.scale > 0f) {
                // Lasso maskeye ancak parmak kalkınca uygulanıyor; o ana kadar
                // kullanıcının tek geri bildirimi bu hat.
                val path = Path()
                lasso.forEachIndexed { index, (x, y) ->
                    val screenX = x * transform.scale + transform.offset.x
                    val screenY = y * transform.scale + transform.offset.y
                    if (index == 0) path.moveTo(screenX, screenY) else path.lineTo(screenX, screenY)
                }
                drawPath(path, color = Color.White, style = Stroke(width = 4f))
                drawPath(
                    path = path,
                    color = Color.Black,
                    style = Stroke(
                        width = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                    ),
                )
                return@Canvas
            }

            // Seçili metnin çerçevesi: hangi nesnenin taşınacağı belli olmalı.
            state.selectedStampBounds()?.let { bounds ->
                if (transform.scale <= 0f) return@Canvas
                val left = bounds[0] * transform.scale + transform.offset.x
                val top = bounds[1] * transform.scale + transform.offset.y
                val right = bounds[2] * transform.scale + transform.offset.x
                val bottom = bounds[3] * transform.scale + transform.offset.y
                drawRect(
                    color = Color.White,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = Stroke(width = 4f),
                )
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = Stroke(
                        width = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                    ),
                )
                return@Canvas
            }

            // Fırça imleci: parmağın altında kalan alanı göremediğin için
            // en azından fırçanın sınırı görünür olmalı.
            if (!state.tool.usesBrush() || state.mode != StickerEditorState.Mode.Brush) {
                return@Canvas
            }
            val position = cursor ?: return@Canvas
            drawCircle(Color.White, state.brushRadiusPx, position, style = Stroke(width = 3f))
            drawCircle(Color.Black, state.brushRadiusPx, position, style = Stroke(width = 1f))
        }
    }
}

private fun StickerEditorState.Tool.usesBrush(): Boolean =
    this == StickerEditorState.Tool.Erase ||
        this == StickerEditorState.Tool.Restore ||
        this == StickerEditorState.Tool.Pen

@Composable
private fun ToolBar(state: StickerEditorState, onEditText: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {

        when {
            state.tool == StickerEditorState.Tool.Text -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Boyut", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = state.textSizePx,
                        // Seçili metin varsa slider onu doğrudan boyutlandırır.
                        onValueChange = { state.setTextSize(it) },
                        valueRange = StickerEditorState.MIN_TEXT_SIZE..StickerEditorState.MAX_TEXT_SIZE,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onEditText) { Text("Metin ekle") }
                    if (state.hasSelectedStamp) {
                        TextButton(onClick = state::deselectStamp) { Text("Bitir") }
                    }
                }
                Text(
                    text = if (state.hasSelectedStamp) {
                        "Tek parmakla taşı, iki parmakla boyutlandır. " +
                            "Bitir'e basınca iki parmak yine resmi yakınlaştırır."
                    } else {
                        "\"Metin ekle\" ile yaz. Var olan bir metne dokunarak " +
                            "tekrar seçebilirsin."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            state.mode == StickerEditorState.Mode.Lasso -> Text(
                text = "Bir alanın çevresini çiz, parmağını kaldırınca içi uygulanır.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )

            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Boyut", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = state.brushRadiusPx,
                    onValueChange = { state.brushRadiusPx = it },
                    valueRange = StickerEditorState.MIN_BRUSH_RADIUS..StickerEditorState.MAX_BRUSH_RADIUS,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
            }
        }

        // Renk sadece ekleyen araçları ilgilendiriyor; silgi ve geri getirmede yer kaplamaz.
        if (state.tool == StickerEditorState.Tool.Pen || state.tool == StickerEditorState.Tool.Text) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StickerEditorState.PALETTE.forEach { color ->
                    val selected = color == state.penColor
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color(0xFF9E9E9E)
                                },
                                shape = CircleShape,
                            )
                            .clickable { state.penColor = color },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToolChip(state, StickerEditorState.Tool.Erase, "Sil")
            ToolChip(state, StickerEditorState.Tool.Restore, "Geri getir")
            ToolChip(state, StickerEditorState.Tool.Pen, "Kalem")
            ToolChip(state, StickerEditorState.Tool.Text, "Metin")
        }

        // Fırça/lasso ayrımı sadece maskeyi boyayan araçlar için anlamlı.
        if (state.tool == StickerEditorState.Tool.Erase ||
            state.tool == StickerEditorState.Tool.Restore
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.mode == StickerEditorState.Mode.Brush,
                    onClick = { state.mode = StickerEditorState.Mode.Brush },
                    label = { Text("Fırça") },
                )
                FilterChip(
                    selected = state.mode == StickerEditorState.Mode.Lasso,
                    onClick = { state.mode = StickerEditorState.Mode.Lasso },
                    label = { Text("Lasso") },
                )
            }
        }
    }
}

@Composable
private fun ToolChip(state: StickerEditorState, tool: StickerEditorState.Tool, label: String) {
    FilterChip(
        selected = state.tool == tool,
        onClick = { state.tool = tool },
        label = { Text(label) },
    )
}

/**
 * Metin ve emoji aynı özellik: kullanıcı klavyesinden emoji de yazabiliyor.
 * Sık kullanılanlar için kısayol satırı var.
 */
@Composable
private fun TextDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Metin veya emoji") },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Yazı") },
                    singleLine = true,
                )
                Text(
                    text = "Klavyeden emoji de yazabilirsin.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    QUICK_EMOJI.forEach { emoji ->
                        TextButton(onClick = { value += emoji }) { Text(emoji) }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) {
                Text("Tamam")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

/** Jest testleri tuvali bu etiketle buluyor. */
const val EDITOR_CANVAS_TAG = "editorCanvas"

private val QUICK_EMOJI = listOf("😀", "😂", "😍", "🔥", "👍", "🎉", "❤️", "😎", "🤔", "💀")

private fun destination(offset: Offset) =
    IntOffset(offset.x.roundToInt(), offset.y.roundToInt())

private fun destinationSize(width: Int, height: Int, scale: Float) =
    IntSize((width * scale).roundToInt(), (height * scale).roundToInt())

private fun minScale(canvas: Size, imageWidth: Int, imageHeight: Int): Float {
    if (canvas == Size.Zero) return 0.05f
    return minOf(canvas.width / imageWidth, canvas.height / imageHeight) * 0.5f
}

private fun maxScale(canvas: Size, imageWidth: Int, imageHeight: Int): Float {
    if (canvas == Size.Zero) return 20f
    return minOf(canvas.width / imageWidth, canvas.height / imageHeight) * 16f
}

private fun DrawScope.drawCheckerboard() {
    val cell = 24f
    drawRect(color = Color(0xFFFFFFFF))
    var row = 0
    var y = 0f
    while (y < size.height) {
        var column = 0
        var x = 0f
        while (x < size.width) {
            if ((row + column) % 2 == 1) {
                drawRect(
                    color = Color(0xFFE0E0E0),
                    topLeft = Offset(x, y),
                    size = Size(cell, cell),
                )
            }
            x += cell
            column++
        }
        y += cell
        row++
    }
}
