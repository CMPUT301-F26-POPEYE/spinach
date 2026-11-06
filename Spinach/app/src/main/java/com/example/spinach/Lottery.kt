package com.example.spinach

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude

// Dates are epoch millis at UTC midnight
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

    @Exclude// US 02.04.01, lot stays active until draw
    fun isActive(): Boolean = !drawn

    @Exclude
    fun isRegistrationOpen(now: Long): Boolean {
        return now >= registrationStart &&
                now < registrationEnd + 86_400_000L &&
                !drawn
    }

    @Exclude
    fun allowsDates(dates: List<Long>): Boolean =//selects atleast 1 date, within period, no date rej
        dates.isNotEmpty() &&
                dates.all { it in bookingStart..bookingEnd }

    @Exclude
    fun canReserve(userId: String, now: Long): Boolean {//checking conditions before entrant reserves
        return drawn &&//finished selecting
                winners.contains(userId) &&//wom
                now >= bookingStart &&
                now < bookingEnd + 86_400_000L//date checks
    }


    // US 02.04.02
    @Exclude
    fun campsites(): List<String> = if (campsiteIds.isEmpty()) listOf(campsiteId) else campsiteIds

    // US 02.04.07
    @Exclude
    fun hasWaitlistRoom(): Boolean {
        val capacity = waitlistCapacity ?: return true
        return waitlist.size < capacity
    }


    fun registrationOver(now: Long): Boolean = now >= registrationEnd + 86_400_000L//checks if registration is over
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