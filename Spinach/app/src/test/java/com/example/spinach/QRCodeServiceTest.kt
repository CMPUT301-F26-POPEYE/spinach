package com.example.spinach

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

//this code was partly produced by claude with the promt, "how would i go about adding these" nov 23 2026

// US 02.02.01 acceptance criteria
class QRCodeServiceTest {

    // Criterion 1: the QR code encodes the campsite's identifier
    @Test
    fun payloadContainsCampsiteId() {
        assertEquals("spinach://campsite/abc123", QRCodeService.payloadFor("abc123"))
    }

    // Criterion 2: no two campsites share a QR code
    @Test
    fun differentCampsitesGetDifferentCodes() {
        assertNotEquals(QRCodeService.payloadFor("abc"), QRCodeService.payloadFor("abd"))
    }

    // Criterion 3: scanning the code resolves to the same campsite
    @Test
    fun scanningGivesBackTheSameCampsite() {
        val matrix = QRCodeService.encode(QRCodeService.payloadFor("abc123"))
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h) { i -> if (matrix[i % w, i / w]) 0xFF000000.toInt() else -1 }
        val scanned = QRCodeReader()
            .decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(w, h, pixels))))
            .text
        assertEquals("abc123", QRCodeService.campsiteIdFrom(scanned))
    }

    @Test
    fun codesFromOtherAppsAreRejected() {
        assertNull(QRCodeService.campsiteIdFrom("https://example.com"))
        assertNull(QRCodeService.campsiteIdFrom("spinach://campsite/"))
    }
}