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
        return null
    }

    @Exclude// US 02.04.01, lot stays active until draw
    fun isActive(): Boolean = !drawn

    fun allowsDates(dates: List<Long>): Boolean =//US 02.04.04, reservations dates should be in booking period
        dates.all { it in bookingStart..bookingEnd }

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