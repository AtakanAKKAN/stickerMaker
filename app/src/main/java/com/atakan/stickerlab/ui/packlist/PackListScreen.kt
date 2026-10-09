package com.atakan.stickerlab.ui.packlist

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atakan.stickerlab.R
import com.atakan.stickerlab.data.PackRules
import com.atakan.stickerlab.data.db.PackWithStickers
import com.atakan.stickerlab.ui.common.StickerThumbnail
import com.atakan.stickerlab.ui.packdetail.NameDialog
import com.atakan.stickerlab.ui.theme.BrandViolet
import com.atakan.stickerlab.ui.theme.StickerIcons
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
        topBar = {
            TopAppBar(
                title = { Text("StickerLab") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
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
                // Eskiden boş bir lambda vardı: ikonsuz bir "extended FAB",
                // yani sadece köşede duran bir buton.
                icon = { Icon(StickerIcons.Add, contentDescription = null) },
            )
        },
    ) { innerPadding ->
        if (packs.isEmpty()) {
            EmptyPacks(
                onCreate = { showCreate = true },
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
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

/**
 * Pack kartı.
 *
 * Önce satırda hem "Aç" butonu hem de tıklanabilir bir kart vardı: aynı yere
 * çıkan iki yol, yanında da "WhatsApp'a Ekle" ile sıkışık bir satır. Artık
 * karta dokunmak pack'i açıyor (chevron bunu söylüyor) ve görünen tek buton
 * ekranın asıl tekrar eden işi: **WhatsApp'a Ekle**.
 *
 * Overflow menüsü bilerek yok. Plan "tek birincil aksiyon + overflow" diyordu
 * ama içine koyacak bir şey çıkmadı: yeniden adlandırma ve silme pack detayında
 * yaşıyor, buraya da konsaydı tam da kaldırdığımız "iki ayrı yol" sorunu geri
 * gelirdi.
 */
@Composable
internal fun PackRow(
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
                // Tray ikonu WhatsApp'ta pack'i temsil eden görsel; listede de
                // pack'in yüzü o. 56dp'de kartın içinde kayboluyordu.
                StickerThumbnail(
                    file = trayFile,
                    contentDescription = null,
                    // Tray dosyası hep aynı adı taşıyor ama içeriği değişebiliyor;
                    // sürüm anahtarı olmadan eski ikon ekranda kalırdı.
                    version = pack.imageDataVersion,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(16.dp),
                        ),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(pack.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "$count sticker",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = StickerIcons.Chevron,
                    contentDescription = "Pack'i aç",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }

            StatusNote(count = count, whatsAppInstalled = whatsAppInstalled)

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onAddToWhatsApp,
                enabled = whatsAppInstalled && publishable,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(StickerIcons.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("WhatsApp'a Ekle")
            }
        }
    }
}

/**
 * Pack'i WhatsApp'a eklemeyi engelleyen durum, varsa tek satır.
 *
 * Düz küçük yazıydı ve butonun neden kapalı olduğunu söylemesi gerekirken
 * kartın içinde kayboluyordu; artık kendi zemini olan bir şerit.
 */
@Composable
private fun StatusNote(count: Int, whatsAppInstalled: Boolean) {
    val note = when {
        !whatsAppInstalled -> "WhatsApp kurulu değil."
        count < PackRules.MIN_STICKERS ->
            "${PackRules.MIN_STICKERS - count} sticker daha gerekli."

        count >= PackRules.MAX_STICKERS -> "Pack dolu (${PackRules.MAX_STICKERS} sticker)."
        else -> return
    }
    Text(
        text = note,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Boş durum: düz yazı yerine uygulamanın kendi marka yüzü. */
@Composable
internal fun EmptyPacks(onCreate: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(132.dp).clip(CircleShape).background(BrandViolet),
            contentAlignment = Alignment.Center,
        ) {
            // Launcher ikonunun ön planı: sarı sticker yüzü. Ayrı bir illüstrasyon
            // çizmek yerine uygulamanın kendi markasını kullanıyoruz.
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(132.dp),
            )
        }
        Text(
            text = "Henüz pack yok",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = "Bir pack oluştur, içine fotoğraflarından sticker ekle, " +
                "sonra WhatsApp'a gönder.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(onClick = onCreate, modifier = Modifier.padding(top = 24.dp)) {
            Icon(StickerIcons.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Pack oluştur")
        }
    }
}
