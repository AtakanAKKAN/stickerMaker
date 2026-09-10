package com.atakan.stickerlab.ui.create

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atakan.stickerlab.ui.editor.StickerEditorScreen

/**
 * Yeni sticker ekranı. Açılır açılmaz sistem Photo Picker'ını başlatır —
 * "+ Sticker" ile bu ekran arasında ayrıca bir dokunuş olmasın diye.
 *
 * Fotoğrafa hiçbir işlem otomatik uygulanmıyor: seçilen görsel olduğu gibi
 * gelir, arka plan silme ve düzenleme kullanıcının seçimiyle çalışır.
 * Photo Picker izin istemiyor (READ_MEDIA_IMAGES gerekmez).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateStickerScreen(
    onDone: () -> Unit,
    viewModel: CreateStickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) onDone() else viewModel.onPhotoPicked(uri)
    }

    LaunchedEffect(Unit) {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    LaunchedEffect(state) {
        if (state is CreateStickerViewModel.State.Saved) onDone()
    }

    val notice = (state as? CreateStickerViewModel.State.Editing)?.notice
    LaunchedEffect(notice) {
        if (notice != null) {
            snackbarHostState.showSnackbar(notice)
            viewModel.dismissNotice()
        }
    }

    val maskEditor = (state as? CreateStickerViewModel.State.Editing)?.maskEditor
    if (maskEditor != null) {
        StickerEditorScreen(
            state = maskEditor,
            onApply = viewModel::applyMaskEditor,
            onCancel = viewModel::cancelMaskEditor,
        )
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Yeni sticker") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when (val current = state) {
                is CreateStickerViewModel.State.Picking -> Unit

                is CreateStickerViewModel.State.Loading -> Status("Fotoğraf yükleniyor…")

                is CreateStickerViewModel.State.Saving -> Status("Kaydediliyor…")

                is CreateStickerViewModel.State.Editing -> Editor(
                    state = current,
                    onRemoveBackground = viewModel::removeBackground,
                    onReset = viewModel::resetToOriginal,
                    onRetouch = viewModel::openMaskEditor,
                    onSave = viewModel::save,
                    onCancel = onDone,
                )

                is CreateStickerViewModel.State.Saved -> Unit

                is CreateStickerViewModel.State.Failed -> Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(current.message, textAlign = TextAlign.Center)
                    OutlinedButton(
                        onClick = onDone,
                        modifier = Modifier.padding(top = 16.dp),
                    ) { Text("Geri dön") }
                }
            }
        }
    }
}

@Composable
private fun Status(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp),
    ) {
        CircularProgressIndicator()
        Text(
            text = message,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun Editor(
    state: CreateStickerViewModel.State.Editing,
    onRemoveBackground: () -> Unit,
    onReset: () -> Unit,
    onRetouch: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val idle = state.busy == null

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(PREVIEW_BACKGROUND),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = state.current.asImageBitmap(),
                contentDescription = "Sticker önizlemesi",
                modifier = Modifier.fillMaxSize().padding(8.dp),
            )

            if (state.busy != null) {
                Box(
                    modifier = Modifier.fillMaxSize().background(BUSY_SCRIM),
                    contentAlignment = Alignment.Center,
                ) {
                    Status(
                        when (state.busy) {
                            CreateStickerViewModel.Busy.DownloadingModel ->
                                "Arka plan silme modeli indiriliyor…\nBu sadece ilk seferde oluyor."

                            CreateStickerViewModel.Busy.RemovingBackground ->
                                "Arka plan siliniyor…"
                        }
                    )
                }
            }
        }

        // İşlemler. Hiçbiri otomatik çalışmaz; kullanıcı seçer.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            // Elle rötuş yapıldıktan sonra gizleniyor: bu işlem orijinale
            // uygulandığı için rötuşu sessizce silerdi.
            if (!state.backgroundRemoved && !state.manuallyEdited) {
                FilledTonalButton(onClick = onRemoveBackground, enabled = idle) {
                    Text("Arka planı sil")
                }
            }
            OutlinedButton(onClick = onRetouch, enabled = idle) { Text("Düzenle") }
            if (state.modified) {
                OutlinedButton(onClick = onReset, enabled = idle) { Text("Orijinale dön") }
            }
        }

        Text(
            text = "Hiçbir işlem seçmeden kaydedersen fotoğraf olduğu gibi " +
                "sticker formuna çevrilir.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            TextButton(onClick = onCancel) { Text("Vazgeç") }
            Button(onClick = onSave, enabled = idle) { Text("Kaydet") }
        }
    }
}

private val PREVIEW_BACKGROUND = Color(0xFFE8E8E8)
private val BUSY_SCRIM = Color(0xCCFFFFFF)
