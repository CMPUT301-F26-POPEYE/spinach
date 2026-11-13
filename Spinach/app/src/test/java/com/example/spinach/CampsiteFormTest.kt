package com.example.spinach

import com.example.spinach.ui.campsiteFormError
import com.example.spinach.ui.copyOf
import com.example.spinach.ui.parseTags
import com.google.firebase.firestore.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * CampsiteFormTest
 * Unit tests for the campsite registration / edit form helpers in CampsiteFormScreen.kt.
 *
 * To cover:
 * - US 02.01.02 A missing required field, or a maximum occupancy that is not a positive
 *   whole number, blocks saving with a message.
 * - US 02.01.03 Editing works on a copy of the campsite.
 */
class CampsiteFormTest {

    private fun error(
        name: String = "Lakeside",
        location: String = "Jasper, AB",
        description: String = "Fire pit, water",
        occupancy: String = "6",
        latitude: String = "52.87",
        longitude: String = "-118.08",
        tags: String = "lake, hiking",
    ) = campsiteFormError(name, location, description, occupancy, latitude, longitude, tags)

    @Test
    fun completeFormHasNoError() {
        assertNull(error())
    }

    @Test
    fun missingRequiredFieldsAreRejected() {
        assertEquals("Name is required", error(name = " "))
        assertEquals("Location is required", error(location = ""))
        assertEquals("Description is required", error(description = ""))
        assertEquals("Maximum occupancy is required", error(occupancy = ""))
        assertEquals("Map location is required", error(latitude = ""))
        assertEquals("Map location is required", error(latitude = "0", longitude = "0"))
        assertEquals("Add at least one tag", error(tags = " , "))
    }

    @Test
    fun occupancyMustBePositiveWholeNumber() {
        val msg = "Maximum occupancy must be a positive whole number"
        assertEquals(msg, error(occupancy = "0"))
        assertEquals(msg, error(occupancy = "-3"))
        assertEquals(msg, error(occupancy = "2.5"))
        assertEquals(msg, error(occupancy = "abc"))
    }

    @Test
    fun coordinatesMustBeInRange() {
        assertEquals("Latitude must be a number between -90 and 90", error(latitude = "91"))
        assertEquals("Longitude must be a number between -180 and 180", error(longitude = "x"))
    }

    @Test
    fun tagsAreTrimmedAndBlanksDropped() {
        assertEquals(listOf("lake", "hiking", "dogs"), parseTags(" lake,hiking ,, dogs "))
    }

    @Test
    fun copyIsIndependentOfOriginal() {
        val original = Campsite(id = "c1", name = "A", tags = mutableListOf("lake"), ownerId = "o1",
            mapCoordinates = GeoPoint(1.0, 2.0))
        val copy = copyOf(original)
        copy.updateDetails(name = "B", tags = listOf("forest"))
        assertEquals("A", original.name)
        assertEquals(listOf("lake"), original.tags)
        assertEquals("c1", copy.id)
        assertEquals("o1", copy.ownerId)
        assertNotSame(original.tags, copy.tags)
    }
}
