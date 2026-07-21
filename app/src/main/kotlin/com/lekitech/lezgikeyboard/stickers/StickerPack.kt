package com.lekitech.lezgikeyboard.stickers

/**
 * The bundled eagle sticker pack — the exact 512×512 WebP images the
 * iOS app exports to WhatsApp/Telegram, in the same canonical order
 * (`scripts/gen_messenger_stickers.py` in the iOS repository), so both
 * platforms ship one identical pack. On Android the keyboard inserts
 * them directly into the conversation through the Commit Content API —
 * a richer path than iOS keyboards have (D-031); the sticker section
 * appears on the emoji page whenever the focused field accepts images.
 * The app additionally exports the pack to WhatsApp and Telegram
 * (D-033), reusing the same emoji tags as the iOS share flow.
 */
object StickerPack {

    /** WhatsApp pack metadata (matches the iOS pack). */
    const val IDENTIFIER = "lezgi_eagle"
    const val NAME = "Lezgi Stickers"
    const val PUBLISHER = "LekiTech"
    const val TRAY_ASSET = "stickers/tray.png"

    /**
     * Asset file stems under `assets/stickers/` with their messenger
     * emoji tags, canonical pack order (kept in sync with
     * `gen_messenger_stickers.py` and `StickerSharing.swift`).
     */
    val stickers: List<Pair<String, String>> = listOf(
        "salam" to "👋",
        "thanks" to "🙏",
        "sweetheart" to "🥰",
        "great" to "👍",
        "yes" to "✅",
        "no" to "❌",
        "howareyou" to "🤗",
        "loveyou" to "❤️",
        "sorry" to "🥺",
        "bravo" to "👏",
        "congrats" to "🎉",
        "welcome" to "🤝",
        "comehere" to "🏃",
        "morning" to "☀️",
        "angry" to "😠",
        "goodnight" to "😴",
        "prayer" to "🤲",
        "lezginka" to "🕺",
        "khinkal" to "🥟",
        "lezgiflag" to "🦅",
    )

    val names: List<String> = stickers.map { it.first }

    fun assetPath(name: String): String = "stickers/$name.webp"

    fun fileName(name: String): String = "$name.webp"
}
