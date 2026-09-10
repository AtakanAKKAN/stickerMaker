package com.atakan.stickerlab.ui.packdetail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atakan.stickerlab.data.PackRules
import com.atakan.stickerlab.data.db.PackWithStickers
import com.atakan.stickerlab.data.entity.StickerEntity
import com.atakan.stickerlab.ui.common.StickerThumbnail

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PackDetailScreen(
    onBack: () -> Unit,
    onAddSticker: (Long) -> Unit,
    viewModel: PackDetailViewModel = hiltViewModel(),
) {
    val pack by viewModel.pack.collectAsStateWithLifecycle()
    val otherPacks by viewModel.otherPacks.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showRename by remember { mutableStateOf(false) }
    var showDeletePack by remember { mutableStateOf(false) }
    var transfer by remember { mutableStateOf<TransferKind?>(null) }
    var editing by remember { mutableStateOf<StickerEntity?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val current = pack
    Scaffold(
        topBar = {
            if (selection.isEmpty()) {
                DetailTopBar(
                    title = current?.pack?.name ?: "",
                    onBack = onBack,
                    onRename = { showRename = true },
                    onDelete = { showDeletePack = true },
                )
            } else {
                SelectionTopBar(
                    count = selection.size,
                    onClear = viewModel::clearSelection,
                )
            }
        },
        bottomBar = {
            if (selection.isNotEmpty()) {
                SelectionActions(
                    onMove = { transfer = TransferKind.Move },
                    onCopy = { transfer = TransferKind.Copy },
                    onDelete = viewModel::deleteSelected,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (current == null) return@Scaffold

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val count = current.stickers.size
            if (!PackRules.isPublishable(count)) {
                Text(
                    text = if (count < PackRules.MIN_STICKERS) {
                        "WhatsApp'a eklemek için ${PackRules.MIN_STICKERS - count} sticker daha gerekli."
                    } else {
                        "Pack dolu (${PackRules.MAX_STICKERS} sticker)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(current.stickers, key = { it.id }) { sticker ->
                    val selected = sticker.id in selection
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (selected) {
                                    Modifier.border(
                                        width = 3.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .combinedClickable(
                                onClick = {
                                    // Seçim modundayken dokunmak seçimi değiştirir,
                                    // normalde sticker detayını açar.
                                    if (selection.isNotEmpty()) {
                                        viewModel.toggleSelection(sticker.id)
                                    } else {
                                        editing = sticker
                                    }
                                },
                                onLongClick = { viewModel.toggleSelection(sticker.id) },
                            ),
                    ) {
                        StickerThumbnail(
                            file = viewModel.stickerFile(sticker.fileName),
                            contentDescription = sticker.accessibilityText,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                if (PackRules.canAddSticker(current.stickers.size)) {
                    item {
                        OutlinedButton(
                            onClick = { onAddSticker(current.pack.id) },
                            modifier = Modifier.aspectRatio(1f),
                        ) { Text("+ Sticker") }
                    }
                }
            }
        }
    }

    if (showRename && current != null) {
        NameDialog(
            title = "Pack adını değiştir",
            initial = current.pack.name,
            onConfirm = {
                viewModel.rename(it)
                showRename = false
            },
            onDismiss = { showRename = false },
        )
    }

    if (showDeletePack) {
        AlertDialog(
            onDismissRequest = { showDeletePack = false },
            title = { Text("Pack silinsin mi?") },
            text = { Text("Paketteki tüm sticker'lar ve dosyaları silinecek.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeletePack = false
                    viewModel.deletePack(onBack)
                }) { Text("Sil") }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePack = false }) { Text("Vazgeç") }
            },
        )
    }

    transfer?.let { kind ->
        TargetPackDialog(
            kind = kind,
            targets = otherPacks,
            enabled = { target ->
                if (kind == TransferKind.Move) viewModel.canMoveTo(target)
                else viewModel.canCopyTo(target)
            },
            reason = { target ->
                if (kind == TransferKind.Move) viewModel.moveBlockedReason(target) else null
            },
            onPick = { target ->
                if (kind == TransferKind.Move) viewModel.move(target) else viewModel.copy(target)
                transfer = null
            },
            onDismiss = { transfer = null },
        )
    }

    editing?.let { sticker ->
        StickerDialog(
            sticker = sticker,
            onSave = { emojis, text ->
                viewModel.updateEmojis(sticker.id, emojis, text)
                editing = null
            },
            onSetTray = {
                viewModel.setTray(sticker.id)
                editing = null
            },
            onDelete = {
                viewModel.toggleSelection(sticker.id)
                viewModel.deleteSelected()
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

enum class TransferKind { Move, Copy }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { TextButton(onClick = onBack) { Text("Geri") } },
        actions = {
            TextButton(onClick = { menuOpen = true }) { Text("Daha fazla") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Yeniden adlandır") },
                    onClick = { menuOpen = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text("Pack'i sil") },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(count: Int, onClear: () -> Unit) {
    TopAppBar(
        title = { Text("$count seçili") },
        navigationIcon = { TextButton(onClick = onClear) { Text("Vazgeç") } },
    )
}

@Composable
private fun SelectionActions(
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        // Scaffold alt çubuğun kendi insetini uygulamıyor; bu olmadan butonlar
        // telefonun gezinme çubuğuyla çakışıyor.
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        OutlinedButton(onClick = onMove) { Text("Taşı") }
        OutlinedButton(onClick = onCopy) { Text("Kopyala") }
        OutlinedButton(onClick = onDelete) { Text("Sil") }
    }
}

@Composable
private fun TargetPackDialog(
    kind: TransferKind,
    targets: List<PackWithStickers>,
    enabled: (PackWithStickers) -> Boolean,
    reason: (PackWithStickers) -> String?,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (kind == TransferKind.Move) "Nereye taşınsın?" else "Nereye kopyalansın?") },
        text = {
            if (targets.isEmpty()) {
                Text("Başka pack yok. Önce yeni bir pack oluştur.")
            } else {
                Column {
                    targets.forEach { target ->
                        val allowed = enabled(target)
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                TextButton(
                                    onClick = { onPick(target.pack.id) },
                                    enabled = allowed,
                                ) {
                                    Text("${target.pack.name} · ${target.stickers.size} sticker")
                                }
                                // Kapalı bir seçeneğin sebebini göstermek şart:
                                // kural WhatsApp'tan geliyor, kullanıcı tahmin edemez.
                                if (!allowed) {
                                    reason(target)?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
}

@Composable
private fun StickerDialog(
    sticker: StickerEntity,
    onSave: (String, String) -> Unit,
    onSetTray: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var emojis by remember(sticker.id) { mutableStateOf(sticker.emojis) }
    var text by remember(sticker.id) { mutableStateOf(sticker.accessibilityText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sticker") },
        text = {
            Column {
                OutlinedTextField(
                    value = emojis,
                    onValueChange = { emojis = it },
                    label = { Text("Emoji (noktalı virgülle ayır)") },
                    singleLine = true,
                )
                Text(
                    text = "WhatsApp en az 1, en fazla 3 emoji kabul ediyor.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Açıklama") },
                    singleLine = true,
                )
                Row(modifier = Modifier.padding(top = 12.dp)) {
                    TextButton(onClick = onSetTray) { Text("Tray ikonu yap") }
                    TextButton(onClick = onDelete) { Text("Sil") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(emojis, text) }) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
fun NameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Pack adı") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) {
                Text("Tamam")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

