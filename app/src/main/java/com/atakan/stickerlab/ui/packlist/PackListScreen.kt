package com.atakan.stickerlab.ui.packlist

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.atakan.stickerlab.ui.common.StickerThumbnail
import com.atakan.stickerlab.ui.packdetail.NameDialog
import com.atakan.stickerlab.whatsapp.AddPackToWhatsApp
import com.atakan.stickerlab.whatsapp.WhatsAppInstallChecker
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackListScreen(
    onOpenPack: (Long) -> Unit,
    viewModel: PackListViewModel = hiltViewModel(),
) {
    val packs by viewModel.packs.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val whatsAppStatus = viewModel.whatsAppStatus
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showCreate by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = AddPackToWhatsApp.describeResult(result.resultCode, result.data)
        scope.launch { snackbarHostState.showSnackbar(text) }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("StickerLab") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (viewModel.canCreatePack) {
                        showCreate = true
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "WhatsApp uygulama başına en fazla ${PackRules.MAX_PACKS} pack kabul ediyor."
                            )
                        }
                    }
                },
                text = { Text("Yeni pack") },
                icon = {},
            )
        },
    ) { innerPadding ->
        if (packs.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Henüz pack yok.", textAlign = TextAlign.Center)
                Button(
                    onClick = { showCreate = true },
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text("Pack oluştur") }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 88.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(packs, key = { it.pack.identifier }) { packWithStickers ->
                PackRow(
                    packWithStickers = packWithStickers,
                    trayFile = viewModel.trayFile(packWithStickers),
                    whatsAppInstalled = whatsAppStatus.anyInstalled,
                    onOpen = { onOpenPack(packWithStickers.pack.id) },
                    onAddToWhatsApp = {
                        launcher.launch(
                            AddPackToWhatsApp.intent(
                                identifier = packWithStickers.pack.identifier,
                                name = packWithStickers.pack.name,
                                targetPackage = if (whatsAppStatus.consumerInstalled) {
                                    WhatsAppInstallChecker.CONSUMER_PACKAGE
                                } else {
                                    WhatsAppInstallChecker.SMB_PACKAGE
                                },
                            )
                        )
                    },
                )
            }
        }
    }

    if (showCreate) {
        NameDialog(
            title = "Yeni pack",
            initial = "",
            onConfirm = {
                viewModel.createPack(it)
                showCreate = false
            },
            onDismiss = { showCreate = false },
        )
    }
}

@Composable
private fun PackRow(
    packWithStickers: PackWithStickers,
    trayFile: File,
    whatsAppInstalled: Boolean,
    onOpen: () -> Unit,
    onAddToWhatsApp: () -> Unit,
) {
    val pack = packWithStickers.pack
    val count = packWithStickers.stickers.size
    val publishable = PackRules.isPublishable(count)

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StickerThumbnail(
                    file = trayFile,
                    contentDescription = null,
                    // Tray dosyası hep aynı adı taşıyor ama içeriği değişebiliyor;
                    // sürüm anahtarı olmadan eski ikon ekranda kalırdı.
                    version = pack.imageDataVersion,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(pack.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "$count sticker · v${pack.imageDataVersion}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (!publishable) {
                Text(
                    text = if (count < PackRules.MIN_STICKERS) {
                        "${PackRules.MIN_STICKERS - count} sticker daha gerekli"
                    } else {
                        "Pack dolu"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (!whatsAppInstalled) {
                Text("WhatsApp kurulu değil.", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpen) { Text("Aç") }
                Button(
                    onClick = onAddToWhatsApp,
                    enabled = whatsAppInstalled && publishable,
                ) { Text("WhatsApp'a Ekle") }
            }
        }
    }
}
