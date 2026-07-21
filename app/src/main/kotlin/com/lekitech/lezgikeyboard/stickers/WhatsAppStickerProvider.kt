package com.lekitech.lezgikeyboard.stickers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor

/**
 * WhatsApp third-party sticker pack provider (D-033) — implements the
 * official contract (github.com/WhatsApp/stickers): WhatsApp queries
 * the pack metadata and sticker list through this provider and pulls
 * the images as asset file descriptors after the user confirms the
 * add-pack intent from the app. Read access is limited to WhatsApp by
 * the `com.whatsapp.sticker.READ` permission in the manifest; the
 * images are the same cache copies the keyboard's Commit Content path
 * uses.
 */
class WhatsAppStickerProvider : ContentProvider() {

    private lateinit var matcher: UriMatcher

    override fun onCreate(): Boolean {
        val authority = authority()
        matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(authority, "metadata", CODE_METADATA_ALL)
            addURI(authority, "metadata/*", CODE_METADATA_ONE)
            addURI(authority, "stickers/*", CODE_STICKERS)
            addURI(authority, "stickers_asset/*/*", CODE_STICKER_ASSET)
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? = when (matcher.match(uri)) {
        CODE_METADATA_ALL -> packCursor()
        CODE_METADATA_ONE ->
            if (uri.lastPathSegment == StickerPack.IDENTIFIER) packCursor() else null
        CODE_STICKERS ->
            if (uri.lastPathSegment == StickerPack.IDENTIFIER) stickerCursor() else null
        else -> null
    }

    private fun packCursor(): Cursor {
        val cursor = MatrixCursor(
            arrayOf(
                "sticker_pack_identifier",
                "sticker_pack_name",
                "sticker_pack_publisher",
                "sticker_pack_icon",
                "android_play_store_link",
                "ios_app_download_link",
                "sticker_pack_publisher_email",
                "sticker_pack_publisher_website",
                "sticker_pack_privacy_policy_website",
                "sticker_pack_license_agreement_website",
                "image_data_version",
                "whatsapp_will_not_cache_stickers",
                "animated_sticker_pack",
            ),
        )
        cursor.addRow(
            arrayOf<Any>(
                StickerPack.IDENTIFIER,
                StickerPack.NAME,
                StickerPack.PUBLISHER,
                "tray.png",
                PLAY_STORE_LINK,
                "", "", "", "", "",
                IMAGE_DATA_VERSION,
                0,
                0,
            ),
        )
        return cursor
    }

    private fun stickerCursor(): Cursor {
        val cursor = MatrixCursor(
            arrayOf("sticker_file_name", "sticker_emoji", "sticker_accessibility_text"),
        )
        for ((name, emoji) in StickerPack.stickers) {
            cursor.addRow(arrayOf<Any>(StickerPack.fileName(name), emoji, ""))
        }
        return cursor
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        if (matcher.match(uri) != CODE_STICKER_ASSET) return null
        val segments = uri.pathSegments
        if (segments.size != 3 || segments[1] != StickerPack.IDENTIFIER) return null
        val fileName = segments[2]
        val context = context ?: return null
        val file = when {
            fileName == "tray.png" -> StickerFiles.tray(context)
            StickerPack.names.any { StickerPack.fileName(it) == fileName } ->
                StickerFiles.ensure(context, "stickers/$fileName", fileName)
            else -> null
        } ?: return null
        return AssetFileDescriptor(
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY),
            0,
            AssetFileDescriptor.UNKNOWN_LENGTH,
        )
    }

    override fun getType(uri: Uri): String? = when (matcher.match(uri)) {
        CODE_METADATA_ALL -> "vnd.android.cursor.dir/vnd.${authority()}.metadata"
        CODE_METADATA_ONE -> "vnd.android.cursor.item/vnd.${authority()}.metadata"
        CODE_STICKERS -> "vnd.android.cursor.dir/vnd.${authority()}.stickers"
        CODE_STICKER_ASSET ->
            if (uri.lastPathSegment == "tray.png") "image/png" else "image/webp"
        else -> null
    }

    private fun authority(): String = "${context?.packageName}.stickercontentprovider"

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException()

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?,
    ): Int = throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int =
        throw UnsupportedOperationException()

    private companion object {
        const val CODE_METADATA_ALL = 1
        const val CODE_METADATA_ONE = 2
        const val CODE_STICKERS = 3
        const val CODE_STICKER_ASSET = 4

        const val PLAY_STORE_LINK =
            "https://play.google.com/store/apps/details?id=com.lekitech.lezgikeyboard"

        /** Bump when the sticker images change — WhatsApp re-pulls. */
        const val IMAGE_DATA_VERSION = "1"
    }
}
