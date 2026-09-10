package com.atakan.stickerlab.whatsapp

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.atakan.stickerlab.provider.StickerContract

/**
 * WhatsApp'a pack ekleme intent'i.
 *
 * Sonuç sessizce başarısız olabilir; [describeResult] dönen resultCode ve
 * "validation_error" extra'sını okunur hale getirir.
 */
object AddPackToWhatsApp {

    const val ACTION = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
    const val EXTRA_VALIDATION_ERROR = "validation_error"
    private const val TAG = "AddPackToWhatsApp"

    fun intent(
        identifier: String,
        name: String,
        targetPackage: String = WhatsAppInstallChecker.CONSUMER_PACKAGE,
    ): Intent = Intent(ACTION).apply {
        putExtra("sticker_pack_id", identifier)
        putExtra("sticker_pack_authority", StickerContract.AUTHORITY)
        putExtra("sticker_pack_name", name)
        setPackage(targetPackage)
    }

    fun describeResult(resultCode: Int, data: Intent?): String {
        val validationError = data?.getStringExtra(EXTRA_VALIDATION_ERROR)
        val message = when {
            validationError != null -> "Doğrulama hatası: $validationError"
            resultCode == Activity.RESULT_OK -> "Pack WhatsApp'a eklendi"
            resultCode == Activity.RESULT_CANCELED -> "İptal edildi (resultCode=0)"
            else -> "Bilinmeyen sonuç: resultCode=$resultCode"
        }
        Log.i(TAG, "resultCode=$resultCode, validation_error=$validationError")
        return message
    }
}
