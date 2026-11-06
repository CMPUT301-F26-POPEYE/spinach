

package com.example.spinach

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude

// Dates are epoch millis at UTC midnight

/**
 * defines lott model used to manage campsite lotteries, stores registration,booking periods, campsiteID...
 * provides validition and checks, random winner selction
 * Outstanding issue: A server-side scheduled trigger is not implemented, but It is not required
 * Draws currently run when the application resumes after registration closes.
 */


/**
 * Represents a campsite lottery and its registration and booking periods.
 */

class Lottery(
    @DocumentId var id: String = "",
    var campsiteId: String = "",
    var ownerId: String = "",
    var registrationStart: Long = 0L,
    var registrationEnd: Long = 0L,
    var bookingStart: Long = 0L,
    var bookingEnd: Long = 0L,
    var winnerCount: Int = 0,
    var waitlist: MutableList<String> = mutableListOf(),//ppl waiting for draw
    var winners: MutableList<String> = mutableListOf(),//ppl selected
    var losers: MutableList<String> = mutableListOf(),//not selected
    var drawn: Boolean = false,
    var campsiteIds: MutableList<String> = mutableListOf(),// US 02.04.02, every campsite in this lottery
    var waitlistCapacity: Int? = null,// US 02.04.07, null means no limit
) {

    /**
     * Validates registration dates, booking dates, winner count,  waitlist capacity.
     */
    // US 02.04.03, 02.04.04, 02.04.05
    @Exclude
    fun validationError(): String? {//formatting v
        if (registrationStart == 0L || registrationEnd == 0L)
            return "Registration start and end dates are required"
        if (registrationEnd <= registrationStart)
            return "Registration must end after it starts"
        if (bookingStart == 0L || bookingEnd == 0L)
            return "Booking start and end dates are required"
        if (bookingStart < registrationEnd)
            return "Booking can't start before registration ends"
        if (bookingEnd < bookingStart)
            return "Booking end can't be before booking start"
        if (winnerCount <= 0)
            return "Number of winners must be a whole number above 0"
        val capacity = waitlistCapacity
        if (capacity != null && capacity <= 0)
            return "Waitlist capacity must be a whole number above 0"
        return null
    }

    /**
     * Checks if the lottery has not been draw
     */
    @Exclude// US 02.04.01, lot stays active until draw
    fun isActive(): Boolean = !drawn

    /**
     * Checks if registration is open.
     */
    @Exclude
    fun isRegistrationOpen(now: Long): Boolean {
        return now >= registrationStart &&
                now < registrationEnd + 86_400_000L &&
                !drawn
    }

    /**
     * Checks if slected reservation dates are in the lottery booking period.
     *.
     */
    @Exclude
    fun allowsDates(dates: List<Long>): Boolean =//selects atleast 1 date, within period, no date rej
        dates.isNotEmpty() &&
                dates.all { it in bookingStart..bookingEnd }


    /**
     * Checks whether an entrant can reserve during the booking period.
     */
    @Exclude
    fun canReserve(userId: String, now: Long): Boolean {//checking conditions before entrant reserves
        return drawn &&//finished selecting
                winners.contains(userId) &&//wom
                now >= bookingStart &&
                now < bookingEnd + 86_400_000L//date checks
    }


    /**
     * Returns all campsite IDs included in the lottery.
     * Uses the primary campsite ID if the campsite list is empty.
     */
    // US 02.04.02
    @Exclude
    fun campsites(): List<String> = if (campsiteIds.isEmpty()) listOf(campsiteId) else campsiteIds


    /**
     * Checks whether another entrant can join the waitlist.
     * Returns true if the capacity is unlimited or space remains.
     */
    // US 02.04.07
    @Exclude
    fun hasWaitlistRoom(): Boolean {
        val capacity = waitlistCapacity ?: return true
        return waitlist.size < capacity
    }


    /**
     * Checks whether the lottery registration period has ended.
     * Registration remains open through the final registration day.
     */
    fun registrationOver(now: Long): Boolean = now >= registrationEnd + 86_400_000L//checks if registration is over

    /**
     * Randomly selects winners from the waitlist.
     * Selects up to the specified winner count, marks the remaining
     * entrants as losers, clears the waitlist, and prevents redraws.
     */
    // US 02.05.01
    fun draw() {

        if (drawn) return// dont draw again if winners have ben chosen

        val shuffled = waitlist.shuffled()
        val count = minOf(winnerCount, shuffled.size)//picks smaller number avaulable spots
        winners = shuffled.take(count).toMutableList()
        losers = shuffled.drop(count).toMutableList()
        waitlist = mutableListOf()
        drawn = true
    }
}