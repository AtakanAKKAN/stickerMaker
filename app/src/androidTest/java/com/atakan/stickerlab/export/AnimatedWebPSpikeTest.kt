package com.atakan.stickerlab.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.drawable.AnimatedImageDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.penfeizhou.animation.awebpencoder.WebPEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * **Fizibilite denemesi — ürün kodu değil.**
 *
 * Soru: hareketli sticker için gereken animasyonlu WebP'yi cihazda üretebiliyor
 * muyuz, ve çıkan dosya WhatsApp'ın kurallarına uyuyor mu?
 *
 * STICKERLAB.md animasyonlu sticker'ı "libwebp NDK derlemesi veya ffmpeg bundle
 * = +25MB APK" gerekçesiyle v2'ye ertelemişti. O varsayım yanlış çıktı:
 * [WebPEncoder] saf Java, her kareyi Android'in kendi `Bitmap.compress(WEBP)`
 * çağrısıyla sıkıştırıp RIFF kaplarını (VP8X/ANIM/ANMF) kendisi örüyor. NDK yok,
 * `.so` yok, projenin ASCII olmayan yolu sorun olmuyor.
 *
 * Kütüphane bilerek **yalnızca `androidTestImplementation`**: appcompat ve iki
 * decoder modülü çekiyor, Compose-only uygulamaya girmesin. Format kanıtlanırsa
 * ~250 satırlık muxer'ı kendimiz yazıp bağımlılığı hiç almayabiliriz.
 *
 * Doğrulanan kurallar (github.com/WhatsApp/stickers):
 * 512×512, ≤ 500 KB, kare süresi ≥ 8 ms, toplam ≤ 10 sn.
 *
 * Çıktı cihazda `files/design/animated-spike.webp`; WhatsApp'ın gerçekten kabul
 * ettiğini ancak telefonda, kurulu WhatsApp ile sınayabiliriz — emülatörde
 * WhatsApp yok.
 */
@RunWith(AndroidJUnit4::class)
class AnimatedWebPSpikeTest {

    @Test
    fun animasyonlu_webp_whatsapp_kurallarina_uyuyor() {
        val frames = (0 until FRAME_COUNT).map { bouncingBall(it) }

        val encoder = WebPEncoder().loopCount(0).quality(QUALITY)
        frames.forEach { encoder.addFrame(it, 0, 0, FRAME_DURATION_MS) }
        val bytes = encoder.build()
        frames.forEach { it.recycle() }

        // 1. Boyut limiti
        assertTrue(
            "Animasyonlu sticker ${bytes.size} bayt, limit $MAX_BYTES",
            bytes.size <= MAX_BYTES,
        )

        // 2. Süre kuralları
        assertTrue("Kare süresi 8 ms'in altında", FRAME_DURATION_MS >= 8)
        assertTrue(
            "Toplam süre 10 sn'yi aşıyor",
            FRAME_COUNT * FRAME_DURATION_MS <= 10_000,
        )

        // 3. Gerçekten animasyonlu mu ve 512×512 mi — dosyayı geri okuyarak.
        // Encoder'ın "animasyonlu dosya ürettim" iddiasına güvenmiyoruz;
        // bozuk bir RIFF kabı da bayt üretir, WhatsApp ise sessizce reddeder.
        val file = outputFile()
        file.writeBytes(bytes)

        val source = ImageDecoder.createSource(file)
        val drawable = ImageDecoder.decodeDrawable(source)
        assertTrue(
            "Çıktı animasyonlu değil: ${drawable.javaClass.simpleName}",
            drawable is AnimatedImageDrawable,
        )
        assertEquals(512, drawable.intrinsicWidth)
        assertEquals(512, drawable.intrinsicHeight)

        android.util.Log.i(
            "AnimatedSpike",
            "OK: ${bytes.size} bayt, $FRAME_COUNT kare, " +
                "${FRAME_COUNT * FRAME_DURATION_MS} ms -> ${file.absolutePath}",
        )
    }

    /** Saydam zeminde hareket eden bir top — sticker formuna yakın bir test deseni. */
    private fun bouncingBall(index: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val progress = index.toFloat() / (FRAME_COUNT - 1)
        val centerY = 140f + 232f * kotlin.math.abs(kotlin.math.sin(progress * Math.PI)).toFloat()

        paint.color = Color.rgb(0x5B, 0x45, 0xE0)
        canvas.drawCircle(256f, centerY, 110f, paint)
        paint.color = Color.rgb(0xFF, 0xC8, 0x3D)
        canvas.drawCircle(256f, centerY, 70f, paint)
        return bitmap
    }

    private fun outputFile(): File {
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "design",
        ).apply { mkdirs() }
        return File(dir, "animated-spike.webp")
    }

    private companion object {
        const val FRAME_COUNT = 12
        const val FRAME_DURATION_MS = 80
        const val QUALITY = 80

        /** WhatsApp animasyonlu sticker limiti; statikte 100 KB. */
        const val MAX_BYTES = 500 * 1024
    }
}
