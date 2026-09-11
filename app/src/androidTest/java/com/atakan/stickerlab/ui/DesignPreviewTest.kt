package com.atakan.stickerlab.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.atakan.stickerlab.ui.theme.StickerIcons
import com.atakan.stickerlab.ui.theme.StickerLabTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Tasarım gözden geçirme düzeneği.
 *
 * Test değil, **ekran görüntüsü üreteci**: bileşenleri gerçek cihazda çizip PNG
 * olarak kaydediyor. Uygulamayı kurup ekranlarda gezinmeden tasarım turu
 * yapmayı sağlıyor — özellikle elle çizilen vektör ikonların gerçekten doğru
 * göründüğünü görmek için, çünkü bozuk bir yol derlemede hata vermiyor.
 *
 * Çıktılar: /sdcard/Android/data/com.atakan.stickerlab/files/design/
 */
@RunWith(AndroidJUnit4::class)
class DesignPreviewTest {

    @get:Rule
    val rule = createComposeRule()

    private val icons: List<Pair<String, ImageVector>> = listOf(
        "Back" to StickerIcons.Back,
        "Close" to StickerIcons.Close,
        "Check" to StickerIcons.Check,
        "More" to StickerIcons.More,
        "Add" to StickerIcons.Add,
        "Undo" to StickerIcons.Undo,
        "Redo" to StickerIcons.Redo,
        "Erase" to StickerIcons.Erase,
        "Restore" to StickerIcons.Restore,
        "Brush" to StickerIcons.Brush,
        "Lasso" to StickerIcons.Lasso,
        "Pen" to StickerIcons.Pen,
        "TextTool" to StickerIcons.TextTool,
        "Move" to StickerIcons.Move,
        "Copy" to StickerIcons.Copy,
        "Delete" to StickerIcons.Delete,
        "Rename" to StickerIcons.Rename,
        "RemoveBg" to StickerIcons.RemoveBackground,
        "Share" to StickerIcons.Share,
        "Photo" to StickerIcons.Photo,
    )

    @Test
    fun ikon_seti_koyu() = capture("icons-dark", darkTheme = true) { IconGallery() }

    @Test
    fun ikon_seti_acik() = capture("icons-light", darkTheme = false) { IconGallery() }

    @Composable
    private fun IconGallery() {
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(icons) { (name, vector) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = vector,
                        contentDescription = name,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }

    private fun capture(name: String, darkTheme: Boolean, content: @Composable () -> Unit) {
        rule.setContent {
            StickerLabTheme(darkTheme = darkTheme) {
                Column(modifier = Modifier.testTag(CAPTURE_TAG)) { content() }
            }
        }
        rule.waitForIdle()

        val bitmap = rule.onNodeWithTag(CAPTURE_TAG).captureToImage().asAndroidBitmap()
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "design",
        ).apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private companion object {
        const val CAPTURE_TAG = "designCapture"
    }
}
