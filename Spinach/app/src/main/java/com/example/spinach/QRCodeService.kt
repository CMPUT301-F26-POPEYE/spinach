package com.example.spinach

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter

//this code was partly produced by claude with the promt, "how would i go about adding these" nov 23 2026

/**
 * QRCodeService
 * Generates and reads the promotional QR code for a campsite.
 *
 * Every QR code holds the text `spinach://campsite/<campsiteId>`, where the ID is the
 * campsite's Firestore document ID. Firestore document IDs are unique within a collection,
 * so no two campsites share a QR code, and scanning one gives back the ID of the campsite
 * it was made for.
 *
 * The code is generated in [CampsiteRepository.registerCampsite] and stored in
 * [Campsite.qrCode].
 *
 * To cover:
 * - US 02.02.01 As a campsite owner, I want the system to generate a unique QR code that
 *   links to the campsite details.
 * - US 01.12.01 (scanning) uses [campsiteIdFrom] to turn a scanned code back into a campsite ID.
 */
object QRCodeService {
    private const val PREFIX = "spinach://campsite/"

    /**
     * Builds the text stored in [Campsite.qrCode] and drawn into the QR image.
     *
     * @param campsiteId The campsite's Firestore document ID.
     * @return The QR code text, `spinach://campsite/<campsiteId>`.
     * @throws IllegalArgumentException if [campsiteId] is blank.
     */
    fun payloadFor(campsiteId: String): String {
        require(campsiteId.isNotBlank()) { "campsite must have an ID" }
        return PREFIX + campsiteId
    }

    /**
     * Turns scanned QR code text back into a campsite ID.
     *
     * @param payload The text read from a QR code.
     * @return The campsite ID, or null if the text is not one of this app's campsite codes.
     */
    fun campsiteIdFrom(payload: String): String? =
        payload.trim()
            .takeIf { it.startsWith(PREFIX) }
            ?.removePrefix(PREFIX)
            ?.takeIf { it.isNotBlank() && '/' !in it }

    /**
     * Encodes text as a QR code grid. Uses only the ZXing library, not Android, so it can
     * be unit tested on the JVM.
     *
     * @param payload The text to encode, usually from [payloadFor].
     * @param size The width and height of the grid in pixels.
     * @return The QR code as a grid where `true` is a black square.
     */
    fun encode(payload: String, size: Int = 512): BitMatrix =
        QRCodeWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(EncodeHintType.MARGIN to 1),
        )

    /**
     * Converts a QR code grid from [encode] into an Android [Bitmap] that can be displayed.
     *
     * @param matrix The QR code grid.
     * @return A black and white bitmap of the QR code.
     */
    fun toBitmap(matrix: BitMatrix): Bitmap {
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height) { i ->
            if (matrix[i % width, i / width]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
