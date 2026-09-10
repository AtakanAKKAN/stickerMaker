package com.atakan.stickerlab.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.atakan.stickerlab.data.db.AppDatabase
import com.atakan.stickerlab.data.db.StickerPackDao
import com.atakan.stickerlab.export.StickerExporter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Taşıma ve kopyalama kuralları.
 *
 * Bu kurallar ihlal edilince WhatsApp hata vermiyor, pack sessizce bozuluyor —
 * yani bir hatayı ancak telefonda sticker sekmesine bakarak fark ederiz.
 * Bu yüzden kuralların testi elle denemeye bırakılmıyor.
 */
@RunWith(AndroidJUnit4::class)
class StickerRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var dao: StickerPackDao
    private lateinit var repository: StickerRepository

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.stickerPackDao()
        repository = StickerRepository(context, dao, StickerExporter())
    }

    @After
    fun tearDown() = runBlocking {
        // Sadece bu testin oluşturduğu klasörler siliniyor; filesDir gerçek
        // uygulamanınki, komple temizlemek kullanıcı verisini silerdi.
        dao.getAllPacksSync().forEach {
            StickerFiles.packDir(context, it.identifier).deleteRecursively()
        }
        database.close()
    }

    // --- taşıma kuralları ---

    @Test
    fun kaynakta_iki_sticker_birakan_tasima_engelleniyor() = runBlocking {
        val source = packWith(4)
        val target = packWith(0)

        val moving = stickerIds(source).take(2)
        try {
            repository.moveStickers(moving, target)
            fail("Kaynakta 2 sticker bırakan taşıma engellenmeliydi")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "hata sebebi açıklanmalı: ${e.message}",
                e.message.orEmpty().contains("en az"),
            )
        }

        assertEquals("kaynak dokunulmamalı", 4, dao.stickerCount(source))
        assertEquals("hedefe bir şey gitmemeli", 0, dao.stickerCount(target))
    }

    @Test
    fun kaynakta_yeterli_sticker_kalan_tasima_calisiyor() = runBlocking {
        val source = packWith(4)
        val target = packWith(0)

        repository.moveStickers(stickerIds(source).take(1), target)

        assertEquals(3, dao.stickerCount(source))
        assertEquals(1, dao.stickerCount(target))
    }

    /** Hepsini taşımak serbest: boş pack normal bir durum, yeni pack de boş. */
    @Test
    fun tum_stickerlari_tasimak_serbest() = runBlocking {
        val source = packWith(3)
        val target = packWith(0)

        repository.moveStickers(stickerIds(source), target)

        assertEquals(0, dao.stickerCount(source))
        assertEquals(3, dao.stickerCount(target))
    }

    @Test
    fun tasima_her_iki_packin_surumunu_artiriyor() = runBlocking {
        val source = packWith(4)
        val target = packWith(3)
        val sourceVersionBefore = dao.getPackById(source)!!.imageDataVersion
        val targetVersionBefore = dao.getPackById(target)!!.imageDataVersion

        repository.moveStickers(stickerIds(source).take(1), target)

        assertTrue(
            "kaynak sürümü artmalı",
            dao.getPackById(source)!!.imageDataVersion > sourceVersionBefore,
        )
        assertTrue(
            "hedef sürümü artmalı",
            dao.getPackById(target)!!.imageDataVersion > targetVersionBefore,
        )
    }

    @Test
    fun tasima_dosyayi_hedef_klasore_tasiyor() = runBlocking {
        // 4 sticker: 1 tanesi taşınınca kaynakta 3 kalır ve kural devreye girmez.
        val source = packWith(4)
        val target = packWith(0)
        val sticker = dao.getStickersByIds(stickerIds(source)).first()
        val sourceIdentifier = dao.getPackById(source)!!.identifier
        val oldFile = StickerFiles.stickerFile(context, sourceIdentifier, sticker.fileName)
        assertTrue("hazırlık: dosya olmalı", oldFile.isFile)

        repository.moveStickers(listOf(sticker.id), target)

        val moved = dao.getStickerById(sticker.id)!!
        val targetIdentifier = dao.getPackById(target)!!.identifier
        assertEquals("sticker hedef pack'e geçmeli", target, moved.packId)
        assertFalse("eski dosya kalmamalı", oldFile.isFile)
        assertTrue(
            "yeni dosya hedef klasörde olmalı",
            StickerFiles.stickerFile(context, targetIdentifier, moved.fileName).isFile,
        )
    }

    @Test
    fun dolu_hedefe_tasima_engelleniyor() = runBlocking {
        val source = packWith(3)
        val target = packWith(PackRules.MAX_STICKERS)

        try {
            repository.moveStickers(stickerIds(source), target)
            fail("Dolu hedefe taşıma engellenmeliydi")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("dolu"))
        }
        assertEquals(3, dao.stickerCount(source))
    }

    // --- kopyalama ---

    @Test
    fun kopyalama_kaynagi_bozmuyor_ve_ayri_dosya_uretiyor() = runBlocking {
        val source = packWith(3)
        val target = packWith(0)
        val sticker = dao.getStickersByIds(stickerIds(source)).first()
        val sourceIdentifier = dao.getPackById(source)!!.identifier

        repository.copyStickers(listOf(sticker.id), target)

        assertEquals("kaynak korunmalı", 3, dao.stickerCount(source))
        assertEquals(1, dao.stickerCount(target))
        assertTrue(
            "kaynak dosyası yerinde kalmalı",
            StickerFiles.stickerFile(context, sourceIdentifier, sticker.fileName).isFile,
        )

        val targetIdentifier = dao.getPackById(target)!!.identifier
        val copy = dao.getStickersForPack(target).first()
        assertNotEquals("iki pack aynı dosyayı paylaşmamalı", sticker.fileName, copy.fileName)
        assertTrue(
            StickerFiles.stickerFile(context, targetIdentifier, copy.fileName).isFile,
        )
    }

    /** Taşıma engellenen durumda kopyalama serbest olmalı — kullanıcının çıkış yolu. */
    @Test
    fun tasimanin_engellendigi_durumda_kopyalama_calisiyor() = runBlocking {
        val source = packWith(4)
        val target = packWith(0)

        repository.copyStickers(stickerIds(source).take(2), target)

        assertEquals(4, dao.stickerCount(source))
        assertEquals(2, dao.stickerCount(target))
    }

    // --- pack yaşam döngüsü ---

    @Test
    fun pack_limiti_uygulaniyor() = runBlocking {
        repeat(PackRules.MAX_PACKS) { repository.createPack("Pack $it") }

        try {
            repository.createPack("Fazladan")
            fail("${PackRules.MAX_PACKS} pack sonrası yenisi engellenmeliydi")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("En fazla"))
        }
        assertEquals(PackRules.MAX_PACKS, dao.packCount())
    }

    @Test
    fun yeniden_adlandirma_identifieri_degistirmiyor() = runBlocking {
        val packId = repository.createPack("Eski isim")
        val identifierBefore = dao.getPackById(packId)!!.identifier

        repository.renamePack(packId, "Yeni isim")

        val pack = dao.getPackById(packId)!!
        assertEquals("Yeni isim", pack.name)
        assertEquals(
            "identifier değişirse WhatsApp'taki pack sıfırlanır",
            identifierBefore,
            pack.identifier,
        )
    }

    @Test
    fun pack_silinince_dosyalari_da_gidiyor() = runBlocking {
        val packId = packWith(3)
        val identifier = dao.getPackById(packId)!!.identifier
        val dir = StickerFiles.packDir(context, identifier)
        assertTrue("hazırlık: klasör olmalı", dir.isDirectory)

        repository.deletePack(packId)

        assertEquals(0, dao.stickerCount(packId))
        assertFalse("klasör silinmeli", dir.exists())
    }

    @Test
    fun sticker_silinince_dosyasi_da_gidiyor() = runBlocking {
        val packId = packWith(3)
        val sticker = dao.getStickersByIds(stickerIds(packId)).first()
        val identifier = dao.getPackById(packId)!!.identifier
        val file = StickerFiles.stickerFile(context, identifier, sticker.fileName)

        repository.deleteStickers(listOf(sticker.id))

        assertEquals(2, dao.stickerCount(packId))
        assertFalse(file.isFile)
    }

    @Test
    fun sticker_dolu_pack_yeni_sticker_kabul_etmiyor() = runBlocking {
        val packId = packWith(PackRules.MAX_STICKERS)

        try {
            repository.addSticker(packId, squareBitmap())
            fail("Dolu pack yeni sticker almamalıydı")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("dolu"))
        }
        assertEquals(PackRules.MAX_STICKERS, dao.stickerCount(packId))
    }

    @Test
    fun bos_emoji_varsayilana_dusuyor() = runBlocking {
        val packId = packWith(3)
        val sticker = dao.getStickersByIds(stickerIds(packId)).first()

        repository.updateStickerMeta(sticker.id, emojis = "", accessibilityText = "test")

        val updated = dao.getStickerById(sticker.id)!!
        assertTrue("WhatsApp en az bir emoji istiyor", updated.emojis.isNotBlank())
    }

    // --- yardımcılar ---

    private suspend fun packWith(stickerCount: Int): Long {
        val packId = repository.createPack("Pack $stickerCount-${System.nanoTime()}")
        repeat(stickerCount) { repository.addSticker(packId, squareBitmap()) }
        return packId
    }

    private suspend fun stickerIds(packId: Long): List<Long> =
        dao.getStickersForPack(packId).map { it.id }

    private fun squareBitmap(): Bitmap =
        Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
}
