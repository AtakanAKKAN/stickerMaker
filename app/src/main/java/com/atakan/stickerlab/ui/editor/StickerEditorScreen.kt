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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.atakan.stickerlab.ui.theme.CheckerDarkCellA
import com.atakan.stickerlab.ui.theme.CheckerDarkCellB
import com.atakan.stickerlab.ui.theme.CheckerLightCellA
import com.atakan.stickerlab.ui.theme.CheckerLightCellB
import com.atakan.stickerlab.ui.theme.StickerIcons
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
    //
    // Zemin rengi burada açıkça veriliyor: tam ekran modal olduğu için altındaki
    // Surface'e güvenmemeli — tasarım turunda üst bar beyaz bir şerit olarak
    // çıkıyordu.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
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

/**
 * Üst bar yalnızca ikon: metin butonları ("Kapat / Geri al / İleri / Bitti")
 * yan yana dizilince satırın yarısını kaplıyor, oysa hepsi tanıdık ikonlar.
 * Tek istisna "Bitti" — ekranın birincil aksiyonu, dolu buton olarak duruyor.
 */
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BarIconButton(StickerIcons.Close, "Kapat", onClick = onClose)
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
            BarIconButton(StickerIcons.Undo, "Geri al", enabled = canUndo, onClick = onUndo)
            BarIconButton(StickerIcons.Redo, "İleri", enabled = canRedo, onClick = onRedo)
        }
        FilledIconButton(
            onClick = onApply,
            modifier = Modifier.size(BAR_BUTTON),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(StickerIcons.Check, contentDescription = "Bitti", modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun BarIconButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(BAR_BUTTON),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
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

    // isSystemInDarkTheme() değil: tasarım turu temayı elle zorluyor, damalı
    // zemin de gerçekten çizilen temayı izlemeli.
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

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
            drawCheckerboard(darkTheme)
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

/**
 * Alt araç çubuğu.
 *
 * Önceden üç satırdı (boyut + araç chip'leri + fırça/lasso chip'leri) ve ekranın
 * alt üçte birini yiyordu; asıl iş ise tuvalde. Şimdi **tek araç satırı** var,
 * seçili aracın seçenekleri onun üstünde bağlama göre tek satırda beliriyor.
 * Chip'ler ~32dp idi, yani önerilen 48dp dokunma hedefinin altında.
 */
@Composable
private fun ToolBar(state: StickerEditorState, onEditText: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Açık temada çubuk zemini tuvalle neredeyse aynı beyazlıkta;
            // çubuğun nerede başladığını bu ince çizgi söylüyor.
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                ToolOptions(state)
                ToolHint(state)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ToolButton(state, StickerEditorState.Tool.Erase, StickerIcons.Erase, "Sil")
                    ToolButton(
                        state,
                        StickerEditorState.Tool.Restore,
                        StickerIcons.Restore,
                        "Geri getir",
                    )
                    ToolButton(state, StickerEditorState.Tool.Pen, StickerIcons.Pen, "Kalem")
                    // Metin aracına dokunmak doğrudan yazma diyalogunu açıyor:
                    // önceki akışta önce araç seçilip sonra "Metin ekle"ye
                    // basılıyordu, yani iki dokunuş ve alt çubukta fazladan bir
                    // buton demekti. Seçili bir metin varken dokunuş sadece araca
                    // dönüyor, yazılan iptal olmasın diye.
                    ToolButton(
                        state,
                        StickerEditorState.Tool.Text,
                        StickerIcons.TextTool,
                        "Metin",
                    ) {
                        val wasText = state.tool == StickerEditorState.Tool.Text
                        state.tool = StickerEditorState.Tool.Text
                        if (!state.hasSelectedStamp || wasText) onEditText()
                    }
                }
            }
        }
    }
}

/** Seçili aracın seçenekleri; hepsi tek satır, yükseklik araç değişince oynamıyor. */
@Composable
private fun ToolOptions(state: StickerEditorState) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = OPTION_ROW),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state.tool) {
            StickerEditorState.Tool.Text -> {
                SizeSlider(
                    value = state.textSizePx,
                    // Seçili metin varsa slider onu doğrudan boyutlandırır.
                    onValueChange = state::setTextSize,
                    range = StickerEditorState.MIN_TEXT_SIZE..StickerEditorState.MAX_TEXT_SIZE,
                )
                Palette(state, modifier = Modifier.weight(1f))
                if (state.hasSelectedStamp) {
                    TextButton(onClick = state::deselectStamp) { Text("Bitir") }
                }
            }

            StickerEditorState.Tool.Pen -> {
                SizeSlider(
                    value = state.brushRadiusPx,
                    onValueChange = { state.brushRadiusPx = it },
                    range = StickerEditorState.MIN_BRUSH_RADIUS..StickerEditorState.MAX_BRUSH_RADIUS,
                )
                Palette(state, modifier = Modifier.weight(1f))
            }

            // Fırça/lasso ayrımı sadece maskeyi boyayan araçlar için anlamlı.
            else -> {
                ModeToggle(state)
                if (state.mode == StickerEditorState.Mode.Brush) {
                    Slider(
                        value = state.brushRadiusPx,
                        onValueChange = { state.brushRadiusPx = it },
                        valueRange = StickerEditorState.MIN_BRUSH_RADIUS..
                            StickerEditorState.MAX_BRUSH_RADIUS,
                        colors = editorSliderColors(),
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                } else {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Boyut slider'ı renk paletiyle aynı satırı paylaştığı için sabit genişlikte:
 * fırça boyutu hassas bir ayar değil, palet ise dokuz renkle daha çok yer
 * istiyor.
 */
@Composable
private fun SizeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        colors = editorSliderColors(),
        modifier = Modifier.size(width = 124.dp, height = OPTION_ROW),
    )
}

/**
 * Slider'ın boş kısmı için nötr ton.
 *
 * Material varsayılanı `secondaryContainer`; tema o yuvayı marka menekşesinin
 * koyu tonuna bağladığı için (bkz. `Theme.kt`) koyu temada boş kısım dolu kadar
 * güçlü görünüyor, çubuk "sonuna kadar açık" izlenimi veriyordu.
 */
@Composable
private fun editorSliderColors() = SliderDefaults.colors(
    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
)

@Composable
private fun Palette(state: StickerEditorState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StickerEditorState.PALETTE.forEach { color ->
            val selected = color == state.penColor
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape,
                    )
                    .clickable { state.penColor = color },
            )
        }
    }
}

@Composable
private fun ModeToggle(state: StickerEditorState) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        ModeButton(state, StickerEditorState.Mode.Brush, StickerIcons.Brush, "Fırça")
        ModeButton(state, StickerEditorState.Mode.Lasso, StickerIcons.Lasso, "Lasso")
    }
}

@Composable
private fun ModeButton(
    state: StickerEditorState,
    mode: StickerEditorState.Mode,
    icon: ImageVector,
    label: String,
) {
    val selected = state.mode == mode
    Box(
        modifier = Modifier
            .size(OPTION_ROW)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            )
            .clickable { state.mode = mode },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * Büyük, tek dokunuşta vurulan araç butonu: 56dp kutu, altında etiket.
 * Etiket kalıyor çünkü "Sil" ile "Geri getir" ikonları tek başına yeterince
 * ayırt edici değil.
 *
 * Seçili durumun dili [ModeButton] ile aynı: dolu `primary` zemin, `onPrimary`
 * ikon. Önce burada `primaryContainer` kullanılıyordu; cihazda iki sorun çıktı —
 * fırça/lasso düğmesinin hemen üstünde ikinci bir menekşe tonu oluyordu ve açık
 * temada araç çubuğunun zemininden ayırt edilemediği için seçili araç
 * kayboluyordu.
 */
@Composable
private fun ToolButton(
    state: StickerEditorState,
    tool: StickerEditorState.Tool,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit = { state.tool = tool },
) {
    val selected = state.tool == tool
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(TOOL_BUTTON)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(28.dp),
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** Yalnızca gerçekten gereken durumlarda tek satırlık açıklama. */
@Composable
private fun ToolHint(state: StickerEditorState) {
    val hint = when {
        state.tool == StickerEditorState.Tool.Text && state.hasSelectedStamp ->
            "Tek parmakla taşı, iki parmakla boyutlandır."

        state.tool == StickerEditorState.Tool.Text ->
            "Var olan bir metne dokunarak tekrar seçebilirsin."

        state.mode == StickerEditorState.Mode.Lasso ->
            "Bir alanın çevresini çiz, parmağını kaldırınca içi uygulanır."

        else -> return
    }
    Text(
        text = hint,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
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

/** Araç butonu: 56dp, önerilen 48dp dokunma hedefinin rahatça üstünde. */
private val TOOL_BUTTON = 56.dp

/** Üst bardaki ikon butonlar. */
private val BAR_BUTTON = 48.dp

/** Seçenek satırının yüksekliği; araç değişince çubuk zıplamasın diye sabit. */
private val OPTION_ROW = 48.dp

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

/**
 * Saydamlığı gösteren damalı zemin. Temaya göre koyu ya da açık tonlar; gerekçe
 * [CheckerLightCellA] tanımında.
 *
 * Hücre boyutu dp: ham piksel olduğunda yoğun ekranlarda desen görünmez
 * inceliğe düşüyordu.
 */
private fun DrawScope.drawCheckerboard(darkTheme: Boolean) {
    val cell = 8.dp.toPx()
    val cellA = if (darkTheme) CheckerDarkCellA else CheckerLightCellA
    val cellB = if (darkTheme) CheckerDarkCellB else CheckerLightCellB
    drawRect(color = cellA)
    var row = 0
    var y = 0f
    while (y < size.height) {
        var column = 0
        var x = 0f
        while (x < size.width) {
            if ((row + column) % 2 == 1) {
                drawRect(color = cellB, topLeft = Offset(x, y), size = Size(cell, cell))
            }
            x += cell
            column++
        }
        y += cell
        row++
    }
}
