package com.atakan.stickerlab.segmentation

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit Subject Segmentation sarmalayıcısı — arka planı silip özneyi döndürür.
 *
 * Model uygulamayla gelmiyor, Play Services üzerinden indiriliyor. İlk kullanımda
 * indirme birkaç saniye sürebilir ve internet yoksa hiç gelmez; bu yüzden
 * indirme ayrı bir adım olarak dışarı veriliyor ve UI durumu gösterebiliyor.
 *
 * Kesim kalitesi Google'ın modelinin kalitesidir — marketteki uygulamalar da
 * aynı modeli kullanıyor. Fark manuel rötuşta (M4) yaratılacak.
 */
@Singleton
class SubjectCutout @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /** Modelin cihazda hazır olup olmadığı. Ağ gerektirmez. */
    suspend fun isModelReady(): Boolean = try {
        newSegmenter().use { segmenter ->
            ModuleInstall.getClient(context)
                .areModulesAvailable(segmenter)
                .await()
                .areModulesAvailable()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Modül durumu okunamadı, indirme gerekiyor varsayılıyor", e)
        false
    }

    /** Modeli indirir. Zaten varsa hemen döner. */
    suspend fun downloadModel() {
        newSegmenter().use { segmenter ->
            val request = ModuleInstallRequest.newBuilder().addApi(segmenter).build()
            ModuleInstall.getClient(context).installModules(request).await()
        }
    }

    /**
     * @return arka planı silinmiş görsel, ya da özne bulunamadıysa null.
     * @throws Exception model indirilemediyse veya işleme başarısızsa.
     */
    suspend fun cutout(source: Bitmap): Bitmap? = newSegmenter().use { segmenter ->
        val result = segmenter.process(InputImage.fromBitmap(source, 0)).await()
        result.foregroundBitmap
    }

    private fun newSegmenter() = SubjectSegmentation.getClient(
        SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()
    )

    private companion object {
        const val TAG = "SubjectCutout"
    }
}

/**
 * Play Services Task -> coroutine köprüsü.
 *
 * kotlinx-coroutines-play-services bağımlılığı eklemek yerine bu kadarı yeterli;
 * tek kullanan yer burası.
 */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
