package com.atakan.stickerlab.export

/**
 * WhatsApp'ın dosya formatı kuralları. İhlal edilirse pack sessizce reddedilir,
 * hata mesajı gelmez — bu yüzden limitler tek yerde sabit tutuluyor.
 */
object StickerFormat {

    /** Sticker tam olarak bu boyutta olmak zorunda; oran korunup ortalanıyor. */
    const val STICKER_SIZE = 512

    /** Statik sticker için üst sınır. Animasyonlu 500 KB ama v1'de yok. */
    const val STICKER_MAX_BYTES = 100 * 1024

    const val TRAY_SIZE = 96
    const val TRAY_MAX_BYTES = 50 * 1024

    /**
     * Kenarlarda bırakılan saydam pay. WhatsApp sticker'ı sohbet balonunda
     * kırpmıyor ama konturu tam kenara dayanan sticker'lar kesik görünüyor.
     */
    const val STICKER_PADDING = 8
    const val TRAY_PADDING = 2

    /**
     * Limitin hemen altını hedeflemek yerine küçük bir emniyet payı bırakılıyor:
     * bazı cihazlarda aynı quality değeri birkaç yüz bayt fark üretebiliyor.
     */
    const val SIZE_SAFETY_MARGIN = 2 * 1024

    /** Bu değerin altındaki alfa "boş piksel" sayılır (trim sırasında). */
    const val ALPHA_THRESHOLD = 8
}
