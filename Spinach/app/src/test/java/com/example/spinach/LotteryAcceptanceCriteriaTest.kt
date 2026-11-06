
package com.example.spinach

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

//the test file was generated with the assistance of ChatGpt Open AI, in a chat called "Create lottery test file" on November 5, 2026
class LotteryAcceptanceCriteriaTest {

    private val dayMs = 86_400_000L

    private fun day(y: Int, m: Int, d: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(y, m - 1, d)
        }.timeInMillis

    private val start = day(2026, 12, 1)
    private val end = day(2026, 12, 10)
    private val bookingStart = day(2026, 12, 11)
    private val bookingEnd = day(2026, 12, 20)

    private fun lottery(
        regStart: Long = start,
        regEnd: Long = end,
        bookStart: Long = bookingStart,
        bookEnd: Long = bookingEnd,
        count: Int = 3,
        entrants: List<String> = emptyList()
    ) = Lottery(
        campsiteId = "camp-123",
        ownerId = "owner-123",
        registrationStart = regStart,
        registrationEnd = regEnd,
        bookingStart = bookStart,
        bookingEnd = bookEnd,
        winnerCount = count,
        waitlist = entrants.toMutableList()
    )

    private fun invalid(l: Lottery, message: String) {
        assertNotNull(message, l.validationError())
        assertTrue(
            "Expected a helpful error: $message",
            !l.validationError().isNullOrBlank()
        )
    }

    // US 02.04.01: Lottery configuration

    @Test
    fun validLotteryContainsCampsiteOwnerAndAllConfiguredFields() {
        val l = lottery()

        assertNull(l.validationError())
        assertEquals("camp-123", l.campsiteId)
        assertEquals("owner-123", l.ownerId)
        assertEquals(start, l.registrationStart)
        assertEquals(end, l.registrationEnd)
        assertEquals(bookingStart, l.bookingStart)
        assertEquals(bookingEnd, l.bookingEnd)
        assertEquals(3, l.winnerCount)
        assertFalse(l.drawn)
        assertTrue(l.isActive())
    }

    @Test
    fun drawnLotteryIsNoLongerActive() {
        val l = lottery(entrants = listOf("a"))

        l.draw()

        assertTrue(l.drawn)
        assertFalse(l.isActive())
    }

    // US 02.04.03: Registration period

    @Test
    fun missingRegistrationStartRejected() =
        invalid(
            lottery(regStart = 0L),
            "Missing registration start"
        )

    @Test
    fun missingRegistrationEndRejected() =
        invalid(
            lottery(regEnd = 0L),
            "Missing registration end"
        )

    @Test
    fun equalRegistrationDatesRejected() =
        invalid(
            lottery(regEnd = start),
            "Registration must have positive duration"
        )

    @Test
    fun reversedRegistrationDatesRejected() =
        invalid(
            lottery(regStart = end, regEnd = start),
            "Reversed registration"
        )

    @Test
    fun validRegistrationDatesAccepted() {
        assertNull(lottery().validationError())
    }

    @Test
    fun registrationDoesNotFinishBeforeItsEnd() {
        val l = lottery()

        assertFalse(l.registrationOver(start))
        assertFalse(l.registrationOver(end - 1))
        assertFalse(l.registrationOver(end))
    }

    @Test
    fun registrationFinishesAfterEndDate() {
        val l = lottery()

        assertTrue(l.registrationOver(end + dayMs))
        assertTrue(l.registrationOver(end + 2 * dayMs))
    }

    // US 02.04.04: Booking period

    @Test
    fun missingBookingStartRejected() =
        invalid(
            lottery(bookStart = 0L),
            "Missing booking start"
        )

    @Test
    fun missingBookingEndRejected() =
        invalid(
            lottery(bookEnd = 0L),
            "Missing booking end"
        )

    @Test
    fun bookingBeforeRegistrationEndRejected() =
        invalid(
            lottery(bookStart = end - dayMs),
            "Booking too early"
        )

    @Test
    fun bookingCanStartOnRegistrationEnd() {
        assertNull(
            lottery(bookStart = end).validationError()
        )
    }

    @Test
    fun bookingEndBeforeStartRejected() =
        invalid(
            lottery(bookEnd = bookingStart - dayMs),
            "Reversed booking dates"
        )

    @Test
    fun bookingSingleDayAllowedByCurrentSpecification() {
        assertNull(
            lottery(bookEnd = bookingStart).validationError()
        )
    }

    @Test
    fun bookingDateBoundariesAreInclusive() {
        val l = lottery()

        assertTrue(l.allowsDates(listOf(bookingStart)))
        assertTrue(l.allowsDates(listOf(bookingEnd)))

        assertTrue(
            l.allowsDates(
                listOf(
                    bookingStart,
                    bookingStart + dayMs,
                    bookingEnd
                )
            )
        )
    }

    @Test
    fun datesOutsideBookingPeriodRejected() {
        val l = lottery()

        assertFalse(
            l.allowsDates(listOf(bookingStart - dayMs))
        )

        assertFalse(
            l.allowsDates(listOf(bookingEnd + dayMs))
        )

        assertFalse(
            l.allowsDates(
                listOf(bookingStart, bookingEnd + dayMs)
            )
        )
    }

    // US 02.04.05: Winner count

    @Test
    fun zeroWinnersRejected() =
        invalid(lottery(count = 0), "Zero winners")

    @Test
    fun negativeWinnersRejected() =
        invalid(lottery(count = -1), "Negative winners")

    @Test
    fun oneWinnerAllowed() {
        assertNull(lottery(count = 1).validationError())
    }

    @Test
    fun largePositiveWinnerCountAllowed() {
        assertNull(lottery(count = 100).validationError())
    }

    // US 02.05.01: Winner selection

    @Test
    fun waitlistLargerThanWinnerCount() {
        val entrants = (1..10).map { "user-$it" }
        val l = lottery(count = 3, entrants = entrants)

        l.draw()

        assertEquals(3, l.winners.size)
        assertEquals(7, l.losers.size)

        assertEquals(
            entrants.toSet(),
            (l.winners + l.losers).toSet()
        )

        assertEquals(
            10,
            (l.winners + l.losers).size
        )

        assertTrue(
            l.winners.intersect(l.losers.toSet()).isEmpty()
        )

        assertTrue(l.waitlist.isEmpty())
        assertTrue(l.drawn)
    }

    @Test
    fun waitlistSmallerThanWinnerCount() {
        val l = lottery(
            count = 5,
            entrants = listOf("a", "b")
        )

        l.draw()

        assertEquals(
            setOf("a", "b"),
            l.winners.toSet()
        )

        assertTrue(l.losers.isEmpty())
        assertTrue(l.waitlist.isEmpty())
    }

    @Test
    fun waitlistEqualsWinnerCount() {
        val l = lottery(
            count = 3,
            entrants = listOf("a", "b", "c")
        )

        l.draw()

        assertEquals(
            setOf("a", "b", "c"),
            l.winners.toSet()
        )

        assertTrue(l.losers.isEmpty())
    }

    @Test
    fun emptyWaitlistStillCompletesDraw() {
        val l = lottery()

        l.draw()

        assertTrue(l.drawn)
        assertTrue(l.winners.isEmpty())
        assertTrue(l.losers.isEmpty())
        assertTrue(l.waitlist.isEmpty())
    }

    @Test
    fun drawRunsOnlyOnceAndKeepsOriginalResults() {
        val l = lottery(
            count = 2,
            entrants = listOf("a", "b", "c", "d")
        )

        l.draw()

        val winners = l.winners.toList()
        val losers = l.losers.toList()

        l.waitlist.add("late-entrant")
        l.draw()

        assertEquals(winners, l.winners)
        assertEquals(losers, l.losers)

        assertFalse(
            l.winners.contains("late-entrant")
        )

        assertFalse(
            l.losers.contains("late-entrant")
        )
    }

    @Test
    fun selectionVariesAcrossIndependentDraws() {
        val observed = mutableSetOf<String>()

        repeat(100) {
            val l = lottery(
                count = 1,
                entrants = listOf("a", "b", "c", "d", "e")
            )

            l.draw()
            observed.addAll(l.winners)
        }

        assertTrue(
            "Selection appears deterministic",
            observed.size > 1
        )
    }

    @Test
    fun onlyWinnersCanReserveDuringBookingPeriod() {
        val l = lottery(entrants = listOf("user-1", "user-2"))

        // Before the draw, nobody can reserve
        assertFalse(l.canReserve("user-1", bookingStart))

        // Draw the winners
        l.draw()

        // A winner can reserve during the booking period
        val winner = l.winners.first()
        assertTrue(l.canReserve(winner, bookingStart))
        assertTrue(l.canReserve(winner, bookingEnd))

        // A user who did not win cannot reserve
        assertFalse(l.canReserve("not-a-winner", bookingStart))

        // Even winners cannot reserve outside the booking period
        assertFalse(l.canReserve(winner, bookingStart - dayMs))
        assertFalse(l.canReserve(winner, bookingEnd + dayMs))
    }
}

