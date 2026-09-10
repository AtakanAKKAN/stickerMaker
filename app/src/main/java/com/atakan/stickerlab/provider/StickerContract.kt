package com.atakan.stickerlab.provider

import android.net.Uri
import com.atakan.stickerlab.BuildConfig

/**
 * WhatsApp third-party sticker pack sözleşmesi.
 *
 * Bu dosyadaki string'lerin hiçbiri serbest seçim değil: WhatsApp cursor kolonlarını
 * isimle okur ve eşleşmezse pack'i hata vermeden reddeder. Değiştirmeyin.
 */
object StickerContract {

    const val AUTHORITY: String = BuildConfig.STICKER_AUTHORITY

    const val METADATA: String = "metadata"
    const val STICKERS: String = "stickers"
    const val STICKERS_ASSET: String = "stickers_asset"

    val AUTHORITY_URI: Uri = Uri.parse("content://$AUTHORITY")

    // --- /metadata kolonları ---
    const val STICKER_PACK_IDENTIFIER = "sticker_pack_identifier"
    const val STICKER_PACK_NAME = "sticker_pack_name"
    const val STICKER_PACK_PUBLISHER = "sticker_pack_publisher"
    const val STICKER_PACK_ICON = "sticker_pack_icon"
    const val ANDROID_PLAY_STORE_LINK = "android_play_store_link"
    const val IOS_APP_DOWNLOAD_LINK = "ios_app_download_link"
    const val PUBLISHER_EMAIL = "sticker_pack_publisher_email"
    const val PUBLISHER_WEBSITE = "sticker_pack_publisher_website"
    const val PRIVACY_POLICY_WEBSITE = "sticker_pack_privacy_policy_website"
    const val LICENSE_AGREEMENT_WEBSITE = "sticker_pack_license_agreement_website"
    const val IMAGE_DATA_VERSION = "image_data_version"
    const val WHITELISTED = "whitelisted"
    const val ANIMATED_STICKER_PACK = "animated_sticker_pack"

    val METADATA_COLUMNS = arrayOf(
        STICKER_PACK_IDENTIFIER,
        STICKER_PACK_NAME,
        STICKER_PACK_PUBLISHER,
        STICKER_PACK_ICON,
        ANDROID_PLAY_STORE_LINK,
        IOS_APP_DOWNLOAD_LINK,
        PUBLISHER_EMAIL,
        PUBLISHER_WEBSITE,
        PRIVACY_POLICY_WEBSITE,
        LICENSE_AGREEMENT_WEBSITE,
        IMAGE_DATA_VERSION,
        WHITELISTED,
        ANIMATED_STICKER_PACK,
    )

    // --- /stickers kolonları ---
    const val STICKER_FILE_NAME = "sticker_file_name"
    const val STICKER_EMOJI = "sticker_emoji"
    const val STICKER_ACCESSIBILITY_TEXT = "sticker_accessibility_text"

    val STICKER_COLUMNS = arrayOf(
        STICKER_FILE_NAME,
        STICKER_EMOJI,
        STICKER_ACCESSIBILITY_TEXT,
    )

    /** Emoji listesi cursor'da noktalı virgülle ayrılır: "😂;🔥" */
    const val EMOJI_SEPARATOR = ";"

    fun stickersAssetUri(identifier: String, fileName: String): Uri =
        AUTHORITY_URI.buildUpon()
            .appendPath(STICKERS_ASSET)
            .appendPath(identifier)
            .appendPath(fileName)
            .build()
}
