package com.atakan.stickerlab.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Bu testler bir regresyondan doğdu: `inJustDecodeBounds = true` ile
 * `BitmapFactory.decodeStream` her zaman null döner, ve null kontrolü yanlış
 * yere konduğu için her fotoğraf "açılamadı" hatası veriyordu.
 */
@RunWith(AndroidJUnit4::class)
class BitmapLoaderTest {

    private lateinit var context: Context
    private lateinit var dir: File
    private lateinit var loader: BitmapLoader

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        loader = BitmapLoader(context)
        dir = File(context.cacheDir, "loader-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @Test
    fun normal_boyutlu_gorsel_yukleniyor() {
        val uri = writeJpeg(800, 600)

        val loaded = loader.load(uri)

        assertEquals(800, loaded.width)
        assertEquals(600, loaded.height)
    }

    @Test
    fun buyuk_gorsel_kucultulerek_yukleniyor() {
        val uri = writeJpeg(5000, 4000)

        val loaded = loader.load(uri)

        assertTrue(
            "kaynak 5000px, yüklenen ${loaded.width}px — küçültülmeliydi",
            loaded.width < 5000,
        )
        // inSampleSize ikinin katlarıyla çalışıyor; sonuç 2048'in altına düşmemeli
        // ki 512'ye ölçeklerken detay kaybı olmasın.
        assertTrue("çok küçülmüş: ${loaded.width}px", loaded.width >= 1024)
    }

    @Test
    fun bozuk_dosya_anlamli_hata_veriyor() {
        val broken = File(dir, "broken.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }

        try {
            loader.load(Uri.fromFile(broken))
            fail("LoadException bekleniyordu")
        } catch (e: LoadException) {
            assertTrue(
                "hata mesajı çözümlemeye işaret etmeli: ${e.message}",
                e.message.orEmpty().contains("çözülemedi"),
            )
        }
    }

    @Test
    fun olmayan_dosya_acilamadi_hatasi_veriyor() {
        try {
            loader.load(Uri.fromFile(File(dir, "yok.jpg")))
            fail("LoadException bekleniyordu")
        } catch (e: LoadException) {
            assertTrue(e.message.orEmpty().contains("açılamadı"))
        }
    }

    private fun writeJpeg(width: Int, height: Int): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawCircle(
            width / 2f, height / 2f, minOf(width, height) / 3f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLUE },
        )
        val file = File(dir, "src-${width}x$height.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        return Uri.fromFile(file)
    }
}
