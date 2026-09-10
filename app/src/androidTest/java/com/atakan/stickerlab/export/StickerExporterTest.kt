package com.atakan.stickerlab.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

/**
 * Cihazda çalışır. Bitmap.compress'in davranışı (özellikle WebP'nin ürettiği
 * dosya boyutu) Android sürümüne ve cihaza göre değişiyor; bu yüzden bu
 * katmanın doğrulaması host tarafında anlamlı olmuyor.
 *
 *   ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class StickerExporterTest {

    private lateinit var dir: File
    private val exporter = StickerExporter()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        dir = File(context.cacheDir, "export-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @Test
    fun sticker_512x512_ve_limit_altinda() {
        val result = exporter.exportSticker(circleBitmap(1024), File(dir, "a.webp"))

        val decoded = decode(result.file)
        assertEquals(512, decoded.width)
        assertEquals(512, decoded.height)
        assertTrue("alfa kanalı korunmalı", decoded.hasAlpha())
        assertTrue(
            "boyut ${result.byteCount} bayt, limit ${StickerFormat.STICKER_MAX_BYTES}",
            result.byteCount <= StickerFormat.STICKER_MAX_BYTES,
        )
    }

    /**
     * Asıl sınav: rastgele gürültü sıkıştırmaya en dirençli girdi. Sabit quality
     * kullanılsaydı burada limit aşılırdı.
     */
    @Test
    fun gurultulu_gorsel_binary_search_ile_sigiyor() {
        val result = exporter.exportSticker(noiseBitmap(1024), File(dir, "noise.webp"))

        assertTrue(
            "boyut ${result.byteCount} bayt, limit ${StickerFormat.STICKER_MAX_BYTES}",
            result.byteCount <= StickerFormat.STICKER_MAX_BYTES,
        )
        assertTrue(
            "gürültülü görselde quality düşürülmeliydi, q=${result.quality}",
            (result.quality ?: 100) < 100,
        )
        assertEquals(512, decode(result.file).width)
    }

    @Test
    fun kucuk_kaynak_512ye_buyutuluyor() {
        val result = exporter.exportSticker(circleBitmap(64), File(dir, "small.webp"))

        val decoded = decode(result.file)
        assertEquals(512, decoded.width)
        assertEquals(512, decoded.height)
    }

    /**
     * ML Kit çıktısı genelde büyük bir tuvalin köşesinde küçük bir nesne oluyor.
     * Kırpma çalışmazsa sticker sohbette minicik görünür.
     */
    @Test
    fun saydam_kenarlar_kirpilip_ortalaniyor() {
        val source = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        Canvas(source).drawRect(
            50f, 50f, 150f, 150f,
            Paint().apply { color = Color.RED },
        )

        val result = exporter.exportSticker(source, File(dir, "corner.webp"))
        val decoded = decode(result.file)

        // Kırpma sonrası kare içerik tuvali doldurmalı: merkez opak, köşeler saydam.
        assertTrue("merkez opak olmalı", Color.alpha(decoded.getPixel(256, 256)) > 200)
        assertTrue("sol üst köşe saydam olmalı", Color.alpha(decoded.getPixel(2, 2)) < 40)
        assertTrue(
            "içerik tuvale yayılmalı",
            Color.alpha(decoded.getPixel(256, StickerFormat.STICKER_PADDING + 4)) > 200,
        )
    }

    @Test
    fun tray_96x96_png_ve_limit_altinda() {
        val result = exporter.exportTray(circleBitmap(1024), File(dir, "tray.png"))

        val decoded = decode(result.file)
        assertEquals(96, decoded.width)
        assertEquals(96, decoded.height)
        assertTrue(
            "boyut ${result.byteCount} bayt, limit ${StickerFormat.TRAY_MAX_BYTES}",
            result.byteCount <= StickerFormat.TRAY_MAX_BYTES,
        )
        assertTrue("PNG imzası", result.file.readBytes().take(4) == PNG_SIGNATURE)
    }

    @Test
    fun tamamen_saydam_gorsel_cokmeden_isleniyor() {
        val empty = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)

        val result = exporter.exportSticker(empty, File(dir, "empty.webp"))

        assertEquals(512, decode(result.file).width)
    }

    @Test
    fun ayni_dosyaya_tekrar_yazmak_uzerine_yaziyor() {
        val target = File(dir, "same.webp")
        exporter.exportSticker(circleBitmap(512), target)
        val second = exporter.exportSticker(noiseBitmap(512), target)

        assertEquals(second.byteCount.toLong(), target.length())
        assertTrue("geçici dosya kalmamalı", dir.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    // --- yardımcılar ---

    private fun decode(file: File): Bitmap =
        BitmapFactory.decodeFile(file.absolutePath)
            ?: throw AssertionError("Dosya çözülemedi: $file")

    private fun circleBitmap(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawCircle(
            size / 2f, size / 2f, size / 3f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(233, 79, 55) },
        )
        return bitmap
    }

    private fun noiseBitmap(size: Int): Bitmap {
        val random = Random(42)
        val pixels = IntArray(size * size) {
            Color.argb(255, random.nextInt(256), random.nextInt(256), random.nextInt(256))
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    private companion object {
        val PNG_SIGNATURE = listOf<Byte>(0x89.toByte(), 0x50, 0x4E, 0x47)
    }
}
