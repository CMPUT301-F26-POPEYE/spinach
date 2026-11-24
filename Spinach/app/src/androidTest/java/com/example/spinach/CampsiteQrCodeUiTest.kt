package com.example.spinach

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.spinach.ui.OwnerApp
import com.google.firebase.firestore.GeoPoint
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

//this code was partly produced by claude with the promt, "how would i go about adding these" nov 23 2026

// US 02.02.01: the owner sees their campsite's QR code
@RunWith(AndroidJUnit4::class)
class CampsiteQrCodeUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun campsite(id: String, qrCode: String) = Campsite(
        id = id,
        name = "Lakeside",
        location = "Jasper",
        description = "Fire pit",
        maxOccupancy = 4,
        mapCoordinates = GeoPoint(52.0, -118.0),
        tags = mutableListOf("lake"),
        qrCode = qrCode,
        ownerId = "owner1",
    )

    @Test
    fun qrCodeShowsOnCampsitePage() {
        val site = campsite("site1", QRCodeService.payloadFor("site1"))
        compose.setContent { OwnerApp(ownerId = "owner1", campsites = listOf(site)) }

        compose.onNodeWithText("Lakeside").performClick()   // open the campsite from the owner home

        compose.onNodeWithContentDescription("QR code for Lakeside")
            .performScrollTo()
            .assertIsDisplayed()
    }

    // Campsites saved before the QR change have qrCode = "" and should still get one
    @Test
    fun oldCampsiteWithoutQrStillShowsOne() {
        val site = campsite("old1", qrCode = "")
        compose.setContent { OwnerApp(ownerId = "owner1", campsites = listOf(site)) }

        compose.onNodeWithText("Lakeside").performClick()

        compose.onNodeWithContentDescription("QR code for Lakeside")
            .performScrollTo()
            .assertIsDisplayed()
    }
}