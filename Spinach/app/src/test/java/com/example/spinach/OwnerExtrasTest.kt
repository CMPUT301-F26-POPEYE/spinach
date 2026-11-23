package com.example.spinach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerExtrasTest {

    private val day = ReservationQueries.DAY_MILLIS
    private val today = 100 * day

    private fun reservation(
        id: String = "r",
        status: ReservationStatus = ReservationStatus.ACTIVE,
        dates: List<Long> = listOf(today + day),
        createdAt: Long = 0L,
    ) = Reservation(
        id = id,
        ownerId = "owner",
        entrantId = "entrant",
        dates = dates.toMutableList(),
        status = status,
        createdAt = createdAt,
    )

    // US 02.07.03
    @Test
    fun cancelledKeepsOnlyCancelledNewestFirst() {
        val list = listOf(
            reservation("a", ReservationStatus.CANCELLED, createdAt = 1),
            reservation("b", ReservationStatus.ACTIVE),
            reservation("c", ReservationStatus.CANCELLED, createdAt = 5),
            reservation("d", ReservationStatus.REJECTED),
        )
        assertEquals(listOf("c", "a"), ReservationQueries.cancelled(list).map { it.id })
    }

    @Test
    fun rejectedKeepsOnlyRejected() {
        val list = listOf(
            reservation("a", ReservationStatus.REJECTED),
            reservation("b", ReservationStatus.CANCELLED),
        )
        assertEquals(listOf("a"), ReservationQueries.rejected(list).map { it.id })
    }

    // US 02.08.01
    @Test
    fun upcomingActiveCanBeRejected() {
        assertTrue(ReservationQueries.canReject(reservation(), today))
    }

    @Test
    fun pastReservationCannotBeRejected() {
        assertFalse(ReservationQueries.canReject(reservation(dates = listOf(today - 3 * day)), today))
    }

    @Test
    fun cancelledReservationCannotBeRejected() {
        assertFalse(ReservationQueries.canReject(reservation(status = ReservationStatus.CANCELLED), today))
    }

    @Test
    fun rejectedDatesAreFreedForBookedDates() {
        val list = listOf(reservation(status = ReservationStatus.REJECTED))
        assertTrue(ReservationQueries.bookedDates(list, "", today).isEmpty())
    }

    // US 02.12.01
    @Test
    fun blankMessageIsRejected() {
        assertNotNull(MessageRules.error("   "))
        assertNull(MessageRules.error("See you Friday"))
    }

    @Test
    fun ownerCanOnlyMessageOwnReservations() {
        assertTrue(MessageRules.canMessage(reservation(), "owner"))
        assertFalse(MessageRules.canMessage(reservation(), "someone-else"))
    }

    @Test
    fun cannotMessageAReservationWithoutAnEntrantOrId() {
        assertFalse(MessageRules.canMessage(Reservation(id = "r", ownerId = "owner"), "owner"))
        assertFalse(MessageRules.canMessage(Reservation(ownerId = "owner", entrantId = "e"), "owner"))
    }

    @Test
    fun messagesAreNewestFirst() {
        val list = listOf(Message(id = "old", createdAt = 1), Message(id = "new", createdAt = 9))
        assertEquals(listOf("new", "old"), MessageRules.newestFirst(list).map { it.id })
    }

    // US 02.10.02 and US 03.08.01
    @Test
    fun notificationsAreNewestFirst() {
        val list = listOf(Notification(id = "old", createdAt = 1), Notification(id = "new", createdAt = 9))
        assertEquals(listOf("new", "old"), NotificationRules.newestFirst(list).map { it.id })
    }

    // US 02.11.01
    @Test
    fun noRulesNeedsNoConfirmation() {
        assertTrue(RulesPolicy.canConfirm("", false))
        assertTrue(RulesPolicy.canConfirm("   ", false))
    }

    @Test
    fun rulesNeedTheBoxTicked() {
        assertFalse(RulesPolicy.canConfirm("No fires", false))
        assertTrue(RulesPolicy.canConfirm("No fires", true))
    }

    @Test
    fun rulesHaveALengthLimit() {
        assertNull(RulesPolicy.error("a".repeat(RulesPolicy.MAX_LENGTH)))
        assertNotNull(RulesPolicy.error("a".repeat(RulesPolicy.MAX_LENGTH + 1)))
    }

    // US 03.03.01 and US 03.06.01
    @Test
    fun imagesAreListedWithTheirCampsite() {
        val a = Campsite(id = "c1", name = "Lakeside", images = mutableListOf("u1", "", "u2"))
        val b = Campsite(id = "c2", name = "Pine Ridge")
        val images = AdminRules.images(listOf(a, b))
        assertEquals(listOf("u1", "u2"), images.map { it.url })
        assertTrue(images.all { it.campsiteId == "c1" && it.campsiteName == "Lakeside" })
    }

    @Test
    fun searchMatchesAnyFieldIgnoringCase() {
        assertTrue(AdminRules.matches("", "anything"))
        assertTrue(AdminRules.matches("lake", "Lakeside Site 4", "Banff"))
        assertTrue(AdminRules.matches(" banff ", "Lakeside", "Banff"))
        assertFalse(AdminRules.matches("zzz", "Lakeside", "Banff"))
    }
}
