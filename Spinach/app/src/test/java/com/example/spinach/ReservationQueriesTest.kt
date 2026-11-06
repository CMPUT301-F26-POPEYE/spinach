package com.example.spinach

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReservationQueriesTest {

    private val dayMillis = ReservationQueries.DAY_MILLIS

    private fun utcDay(year: Int, month: Int, day: Int): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.clear()
        calendar.set(year, month - 1, day)
        return calendar.timeInMillis
    }

    private val today = utcDay(2026, 11, 3)

    private fun reservation(
        vararg offsets: Int,
        campsiteId: String = "camp1",
        status: ReservationStatus = ReservationStatus.ACTIVE,
    ) = Reservation(
        campsiteId = campsiteId,
        status = status,
        dates = offsets.map { today + it * dayMillis }.toMutableList(),
    )

    @Test
    fun isPast_falseWhenLastDateIsToday() {
        assertFalse(ReservationQueries.isPast(reservation(-2, -1, 0), today))
    }

    @Test
    fun isPast_trueWhenLastDateWasYesterday() {
        assertTrue(ReservationQueries.isPast(reservation(-3, -2, -1), today))
    }

    @Test
    fun isPast_falseWhenNoDates() {
        assertFalse(ReservationQueries.isPast(reservation(), today))
    }

    @Test
    fun active_sortsSoonestFirst() {
        val later = reservation(10)
        val sooner = reservation(2, 3)
        assertEquals(listOf(sooner, later), ReservationQueries.active(listOf(later, sooner), today))
    }

    @Test
    fun active_excludesPastCancelledAndRejected() {
        val upcoming = reservation(5)
        val ended = reservation(-5)
        val cancelled = reservation(5, status = ReservationStatus.CANCELLED)
        val rejected = reservation(5, status = ReservationStatus.REJECTED)
        val result = ReservationQueries.active(listOf(upcoming, ended, cancelled, rejected), today)
        assertEquals(listOf(upcoming), result)
    }

    @Test
    fun past_sortsMostRecentFirst() {
        val older = reservation(-20, -19)
        val recent = reservation(-3, -2)
        assertEquals(listOf(recent, older), ReservationQueries.past(listOf(older, recent), today))
    }

    @Test
    fun past_excludesUpcomingAndCancelled() {
        val upcoming = reservation(5)
        val endedButCancelled = reservation(-5, status = ReservationStatus.CANCELLED)
        val ended = reservation(-5)
        val result = ReservationQueries.past(listOf(upcoming, endedButCancelled, ended), today)
        assertEquals(listOf(ended), result)
    }

    @Test
    fun bookedDates_onlyThisCampsiteActiveAndUpcoming() {
        val mine = reservation(2, 3, campsiteId = "camp1")
        val otherCampsite = reservation(4, campsiteId = "camp2")
        val cancelled = reservation(6, campsiteId = "camp1", status = ReservationStatus.CANCELLED)
        val ended = reservation(-4, campsiteId = "camp1")
        val result = ReservationQueries.bookedDates(
            listOf(mine, otherCampsite, cancelled, ended), "camp1", today,
        )
        assertEquals(listOf(today + 2 * dayMillis, today + 3 * dayMillis), result)
    }

    @Test
    fun bookedDates_dropsPastDatesOfARunningReservation() {
        val running = reservation(-1, 0, 1)
        val result = ReservationQueries.bookedDates(listOf(running), "camp1", today)
        assertEquals(listOf(today, today + dayMillis), result)
    }

    @Test
    fun bookedDates_removesDuplicatesAndSorts() {
        val first = reservation(5, 3)
        val second = reservation(3, 4)
        val result = ReservationQueries.bookedDates(listOf(first, second), "camp1", today)
        assertEquals(
            listOf(today + 3 * dayMillis, today + 4 * dayMillis, today + 5 * dayMillis),
            result,
        )
    }

    @Test
    fun formatDay_readsTheStoredDateInUtc() {
        assertEquals(
            "Fri 6 Nov 2026",
            ReservationQueries.formatDay(utcDay(2026, 11, 6), Locale.ENGLISH),
        )
    }

    @Test
    fun describeDates_consecutiveDaysBecomeARange() {
        val dates = listOf(utcDay(2026, 11, 8), utcDay(2026, 11, 6), utcDay(2026, 11, 7))
        assertEquals(
            "Fri 6 Nov 2026 to Sun 8 Nov 2026",
            ReservationQueries.describeDates(dates, Locale.ENGLISH),
        )
    }

    @Test
    fun describeDates_gapsBecomeAList() {
        val dates = listOf(utcDay(2026, 11, 6), utcDay(2026, 11, 9))
        assertEquals(
            "Fri 6 Nov 2026, Mon 9 Nov 2026",
            ReservationQueries.describeDates(dates, Locale.ENGLISH),
        )
    }

    @Test
    fun describeDates_empty() {
        assertEquals("No dates", ReservationQueries.describeDates(emptyList(), Locale.ENGLISH))
    }
}