package com.lekitech.lezgikeyboard.onboarding

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.lekitech.lezgikeyboard.stickers.StickerFiles
import com.lekitech.lezgikeyboard.stickers.StickerPack

/**
 * Exports the eagle sticker pack to messengers — the Android
 * counterpart of the iOS `StickerSharing` (D-033). WhatsApp adds the
 * pack through its third-party sticker contract (the enable intent +
 * `WhatsAppStickerProvider`); Telegram imports it through its official
 * sticker-import intent with the images served by the keyboard's
 * FileProvider. Both return false when the messenger is not installed.
 */
object StickerSharing {

    fun addToWhatsApp(activity: Activity): Boolean {
        val intent = Intent(WHATSAPP_ENABLE_ACTION).apply {
            putExtra("sticker_pack_id", StickerPack.IDENTIFIER)
            putExtra(
                "sticker_pack_authority",
                "${activity.packageName}.stickercontentprovider",
            )
            putExtra("sticker_pack_name", StickerPack.NAME)
        }
        return try {
            @Suppress("DEPRECATION")
            activity.startActivityForResult(intent, ADD_PACK_REQUEST)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    fun addToTelegram(activity: Activity): Boolean {
        val uris = ArrayList<Uri>()
        val emojis = ArrayList<String>()
        for ((name, emoji) in StickerPack.stickers) {
            val file = StickerFiles.sticker(activity, name) ?: return false
            uris.add(
                FileProvider.getUriForFile(
                    activity, "${activity.packageName}.stickers", file,
                ),
            )
            emojis.add(emoji)
        }
        val intent = Intent(TELEGRAM_CREATE_PACK_ACTION).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra("IMPORTER", activity.packageName)
            putStringArrayListExtra("STICKER_EMOJIS", emojis)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // The read grant covers every URI only when they all ride
            // in the clip data, not just the extras.
            clipData = ClipData.newRawUri("stickers", uris.first()).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
        }
        return try {
            activity.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private const val WHATSAPP_ENABLE_ACTION = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
    private const val TELEGRAM_CREATE_PACK_ACTION = "org.telegram.messenger.CREATE_STICKER_PACK"
    private const val ADD_PACK_REQUEST = 200
}
