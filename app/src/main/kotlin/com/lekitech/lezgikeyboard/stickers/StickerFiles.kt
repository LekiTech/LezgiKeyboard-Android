package com.lekitech.lezgikeyboard.stickers

import android.content.Context
import java.io.File

/**
 * Cache copies of the bundled sticker assets, shared by every consumer
 * that needs a real file: the keyboard's Commit Content path (D-031),
 * the WhatsApp pack provider, and the Telegram import intent (D-033).
 * Copied on demand, never expired — the assets are immutable per
 * build, and the cache directory is the system's to reclaim.
 */
object StickerFiles {

    fun directory(context: Context): File =
        File(context.cacheDir, "stickers").apply { mkdirs() }

    /** The cache copy of one sticker (or the tray icon), creating it on demand. */
    fun ensure(context: Context, assetPath: String, fileName: String): File? = try {
        val file = File(directory(context), fileName)
        if (!file.exists()) {
            context.assets.open(assetPath).use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
        }
        file
    } catch (_: Exception) {
        null
    }

    fun sticker(context: Context, name: String): File? =
        ensure(context, StickerPack.assetPath(name), StickerPack.fileName(name))

    fun tray(context: Context): File? =
        ensure(context, StickerPack.TRAY_ASSET, "tray.png")
}
