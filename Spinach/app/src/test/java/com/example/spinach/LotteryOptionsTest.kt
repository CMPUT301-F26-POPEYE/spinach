package com.example.spinach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LotteryOptionsTest {

    private val day = 86_400_000L

    private fun valid() = Lottery(
        campsiteId = "c1",
        registrationStart = 10 * day,
        registrationEnd = 12 * day,
        bookingStart = 13 * day,
        bookingEnd = 15 * day,
        winnerCount = 2,
    )

    @Test
    fun noCapacityMeansNoLimit() {
        val l = valid()
        l.waitlist = MutableList(500) { "u$it" }
        assertTrue(l.hasWaitlistRoom())
    }

    @Test
    fun roomWhileBelowCapacity() {
        val l = valid()
        l.waitlistCapacity = 3
        l.waitlist = mutableListOf("a", "b")
        assertTrue(l.hasWaitlistRoom())
    }

    @Test
    fun noRoomAtCapacity() {
        val l = valid()
        l.waitlistCapacity = 3
        l.waitlist = mutableListOf("a", "b", "c")
        assertFalse(l.hasWaitlistRoom())
    }

    @Test
    fun noCapacityIsValid() {
        assertNull(valid().validationError())
    }

    @Test
    fun positiveCapacityIsValid() {
        val l = valid()
        l.waitlistCapacity = 5
        assertNull(l.validationError())
    }

    @Test
    fun zeroCapacityIsRejected() {
        val l = valid()
        l.waitlistCapacity = 0
        assertEquals("Waitlist capacity must be a whole number above 0", l.validationError())
    }

    @Test
    fun negativeCapacityIsRejected() {
        val l = valid()
        l.waitlistCapacity = -1
        assertEquals("Waitlist capacity must be a whole number above 0", l.validationError())
    }

    @Test
    fun campsitesFallsBackToTheSingleCampsiteId() {
        assertEquals(listOf("c1"), valid().campsites())
    }

    @Test
    fun campsitesUsesTheListWhenSet() {
        val l = valid()
        l.campsiteIds = mutableListOf("c1", "c2")
        assertEquals(listOf("c1", "c2"), l.campsites())
    }
}