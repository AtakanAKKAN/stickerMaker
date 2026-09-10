package com.atakan.stickerlab.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.atakan.stickerlab.data.StickerFiles
import com.atakan.stickerlab.data.db.StickerPackDao
import com.atakan.stickerlab.data.entity.StickerPackEntity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.FileNotFoundException

/**
 * WhatsApp'ın pack'leri okuduğu provider.
 *
 * Kolon isimleri ve URI şeması [StickerContract]'ta sabit; buradaki tek iş
 * Room'daki veriyi o şemaya birebir çevirmek. Doğrulama hatalarında WhatsApp
 * sessizce vazgeçer, bu yüzden her sapma burada loglanır.
 */
class StickerContentProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ProviderEntryPoint {
        fun stickerPackDao(): StickerPackDao
    }

    private lateinit var matcher: UriMatcher

    // ContentProvider.onCreate, Hilt'in application component'i hazır olmadan da
    // çalışabilir; DAO ilk sorguda çözülür.
    private val dao: StickerPackDao by lazy {
        EntryPointAccessors
            .fromApplication(ctx().applicationContext, ProviderEntryPoint::class.java)
            .stickerPackDao()
    }

    override fun onCreate(): Boolean {
        matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(StickerContract.AUTHORITY, StickerContract.METADATA, CODE_METADATA_ALL)
            addURI(StickerContract.AUTHORITY, "${StickerContract.METADATA}/*", CODE_METADATA_ONE)
            addURI(StickerContract.AUTHORITY, "${StickerContract.STICKERS}/*", CODE_STICKERS)
            addURI(
                StickerContract.AUTHORITY,
                "${StickerContract.STICKERS_ASSET}/*/*",
                CODE_STICKERS_ASSET,
            )
        }
        return true
    }

    // WhatsApp doğrulama hatalarını açıklamıyor; hangi URI'ye kadar geldiğini
    // görmek tek teşhis yolu:  adb logcat -s StickerProvider
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        Log.d(TAG, "query <- " + uri + " (caller=" + callingPackage + ")")
        return queryInternal(uri)
    }

    private fun queryInternal(uri: Uri): Cursor? = when (matcher.match(uri)) {
        CODE_METADATA_ALL -> metadataCursor(uri, dao.getAllPacksSync())

        CODE_METADATA_ONE -> {
            val identifier = uri.lastPathSegment.orEmpty()
            metadataCursor(uri, listOfNotNull(dao.getPackSync(identifier)))
        }

        CODE_STICKERS -> {
            val identifier = uri.lastPathSegment.orEmpty()
            MatrixCursor(StickerContract.STICKER_COLUMNS).apply {
                dao.getStickersSync(identifier).forEach { sticker ->
                    addRow(arrayOf(sticker.fileName, sticker.emojis, sticker.accessibilityText))
                }
                setNotificationUri(resolver(), uri)
            }
        }

        else -> {
            Log.w(TAG, "Bilinmeyen query URI: $uri")
            null
        }
    }

    private fun metadataCursor(uri: Uri, packs: List<StickerPackEntity>): Cursor =
        MatrixCursor(StickerContract.METADATA_COLUMNS).apply {
            packs.forEach { pack ->
                addRow(
                    arrayOf<Any?>(
                        pack.identifier,
                        pack.name,
                        pack.publisher,
                        pack.trayFileName,
                        "", // android_play_store_link — internal testing, boş kalıyor
                        "", // ios_app_download_link
                        "", // sticker_pack_publisher_email
                        "", // sticker_pack_publisher_website
                        "", // sticker_pack_privacy_policy_website
                        "", // sticker_pack_license_agreement_website
                        pack.imageDataVersion.toString(),
                        // whitelisted her zaman 0: 1 verilirse WhatsApp pack'i
                        // yayınlanmış sanıp cache'ler, değişiklikler görünmez.
                        0,
                        if (pack.animated) 1 else 0,
                    )
                )
            }
            setNotificationUri(resolver(), uri)
        }

    /**
     * WhatsApp dosyaları openAssetFile üzerinden okur; adb'nin "content read"
     * komutu ve bazı okuyucular openFile'a düşer. İkisi de aynı dosyayı
     * çözmeli, yoksa eksik olan taraf ancak WhatsApp'ta fark edilir.
     */
    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
        val file = resolveStickerFile(uri)
        Log.d(TAG, "openAssetFile <- " + uri + " (" + file.length() + " bytes, caller=" + callingPackage + ")")
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, file.length())
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        Log.d(TAG, "openFile <- " + uri).let { ParcelFileDescriptor.open(resolveStickerFile(uri), ParcelFileDescriptor.MODE_READ_ONLY) }

    private fun resolveStickerFile(uri: Uri): File {
        if (matcher.match(uri) != CODE_STICKERS_ASSET) {
            throw FileNotFoundException("Asset URI değil: $uri")
        }
        val segments = uri.pathSegments
        val identifier = segments[segments.size - 2]
        val fileName = segments.last()

        // Path traversal koruması: dosya gerçekten pack klasörünün altında mı.
        val packDir = StickerFiles.packDir(ctx(), identifier).canonicalFile
        val file = File(packDir, fileName).canonicalFile
        if (!file.path.startsWith(packDir.path + File.separator) || !file.isFile) {
            Log.w(TAG, "Dosya bulunamadı ya da klasör dışında: $uri")
            throw FileNotFoundException("Sticker yok: $uri")
        }
        return file
    }

    override fun getType(uri: Uri): String? = when (matcher.match(uri)) {
        CODE_METADATA_ALL -> "vnd.android.cursor.dir/vnd.${StickerContract.AUTHORITY}.metadata"
        CODE_METADATA_ONE -> "vnd.android.cursor.item/vnd.${StickerContract.AUTHORITY}.metadata"
        CODE_STICKERS -> "vnd.android.cursor.dir/vnd.${StickerContract.AUTHORITY}.stickers"
        CODE_STICKERS_ASSET -> if (uri.lastPathSegment?.endsWith(".png") == true) {
            "image/png"
        } else {
            "image/webp"
        }
        else -> null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Salt okunur provider")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Salt okunur provider")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Salt okunur provider")

    private fun ctx(): Context = requireNotNull(context) { "Provider context yok" }

    private fun resolver() = ctx().contentResolver

    private companion object {
        const val TAG = "StickerProvider"
        const val CODE_METADATA_ALL = 1
        const val CODE_METADATA_ONE = 2
        const val CODE_STICKERS = 3
        const val CODE_STICKERS_ASSET = 4
    }
}
