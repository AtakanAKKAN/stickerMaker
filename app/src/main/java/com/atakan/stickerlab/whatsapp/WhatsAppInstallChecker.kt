package com.atakan.stickerlab.whatsapp

import android.content.Context
import android.content.pm.PackageManager

/**
 * Manifest'teki <queries> bloğu olmadan bu kontrol API 30+ cihazlarda her zaman
 * "kurulu değil" döner.
 */
object WhatsAppInstallChecker {

    const val CONSUMER_PACKAGE = "com.whatsapp"
    const val SMB_PACKAGE = "com.whatsapp.w4b"

    data class Status(val consumerInstalled: Boolean, val smbInstalled: Boolean) {
        val anyInstalled: Boolean get() = consumerInstalled || smbInstalled
    }

    fun status(context: Context): Status = Status(
        consumerInstalled = isInstalled(context, CONSUMER_PACKAGE),
        smbInstalled = isInstalled(context, SMB_PACKAGE),
    )

    private fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
