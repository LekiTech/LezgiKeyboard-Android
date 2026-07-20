package com.lekitech.lezgikeyboard.stickers

/**
 * The bundled eagle sticker pack — the exact 512×512 WebP images the
 * iOS app exports to WhatsApp/Telegram, in the same canonical order
 * (`scripts/gen_messenger_stickers.py` in the iOS repository), so both
 * platforms ship one identical pack. On Android the keyboard inserts
 * them directly into the conversation through the Commit Content API —
 * a richer path than iOS keyboards have (D-031); the sticker section
 * appears on the emoji page whenever the focused field accepts images.
 */
object StickerPack {

    /** Asset file stems under `assets/stickers/`, canonical pack order. */
    val names: List<String> = listOf(
        "salam", "thanks", "sweetheart", "great", "yes", "no",
        "howareyou", "loveyou", "sorry", "bravo", "congrats", "welcome",
        "comehere", "morning", "angry", "goodnight", "prayer",
        "lezginka", "khinkal", "lezgiflag",
    )

    fun assetPath(name: String): String = "stickers/$name.webp"
}
