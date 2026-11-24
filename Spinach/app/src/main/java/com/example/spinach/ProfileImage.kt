package com.example.spinach

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

//this code was partly produced by claude with the promt, "how would i go about adding these" nov 24 2026

/**
 * ProfileImage
 * Converts a photo the user picked into the small Base64 JPEG text stored in
 * [User.profileImage], and converts that text back into an image for display.
 *
 * Images are stored in the user's Firestore document instead of Firebase Storage, so the
 * app needs no extra service or image library. Shrinking to [MAX_SIDE] pixels keeps each
 * image around 15-30 KB, well under Firestore's 1 MB document limit.
 *
 * To cover:
 * - As an entrant, I want to add, update, or remove my profile image.
 */
object ProfileImage {
    /** The longest side of a stored image, in pixels. */
    private const val MAX_SIDE = 256

    /**
     * Reads the photo at [uri], shrinks it, and returns it as Base64 JPEG text.
     *
     * The photo's size is read first so a large photo is loaded at a reduced size,
     * which avoids running out of memory.
     *
     * @param context Used to open the photo.
     * @param uri The photo the user picked, from the Photo Picker.
     * @return The image as Base64 text, ready to store in [User.profileImage].
     * @throws IllegalArgumentException if the photo can't be read.
     */
    fun encode(context: Context, uri: Uri): String {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2

        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalArgumentException("Could not read that image")

        val scale = MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height)
        val small = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            decoded
        }

        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Turns stored Base64 text back into an image that Compose can display.
     *
     * @param encoded Text from [User.profileImage].
     * @return The image, or null if the text is not a valid image.
     */
    fun decode(encoded: String): ImageBitmap? = try {
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (e: IllegalArgumentException) {
        null
    }
}
