package com.atakan.stickerlab.data

/**
 * WhatsApp'ın pack kuralları.
 *
 * Bunlar ihlal edilince WhatsApp hata vermiyor — pack'i sessizce reddediyor ya da
 * eklenmiş pack bozuluyor. Bu yüzden hem UI (butonları kapatmak için) hem
 * repository (son savunma) aynı fonksiyonları kullanıyor.
 */
object PackRules {

    const val MIN_STICKERS = 3
    const val MAX_STICKERS = 30
    const val MAX_PACKS = 10

    fun canCreatePack(packCount: Int): Boolean = packCount < MAX_PACKS

    fun canAddSticker(packCount: Int): Boolean = packCount < MAX_STICKERS

    /** Pack WhatsApp'a eklenebilir mi. */
    fun isPublishable(stickerCount: Int): Boolean = stickerCount in MIN_STICKERS..MAX_STICKERS

    /** Hedef pack bu kadar sticker'ı alabilir mi. */
    fun canAccept(targetCount: Int, incoming: Int): Boolean =
        targetCount + incoming <= MAX_STICKERS

    /**
     * Kaynak pack bu kadar sticker'ı verebilir mi.
     *
     * Kritik kural: 4 sticker'lı bir pack'ten 2 tanesi taşınırsa kaynakta 2 kalır
     * ve pack WhatsApp'ta **sessizce bozulur**. Geriye 1 veya 2 sticker bırakan
     * taşıma engelleniyor. Hepsini taşımak (0 kalması) serbest — boş pack zaten
     * normal bir durum, yeni oluşturulan pack de boş.
     */
    fun canMoveOut(sourceCount: Int, moving: Int): Boolean {
        val remaining = sourceCount - moving
        return remaining == 0 || remaining >= MIN_STICKERS
    }

    /** Taşımanın neden engellendiğini kullanıcıya söylemek için. */
    fun moveBlockedReason(sourceCount: Int, moving: Int): String? {
        if (canMoveOut(sourceCount, moving)) return null
        val remaining = sourceCount - moving
        return "Kaynak pakette $remaining sticker kalır. WhatsApp en az " +
            "$MIN_STICKERS istiyor; ya hepsini taşı ya da kopyala."
    }
}
