package com.example.spinach


/**
 * EntrantReservationRules
 * Plain functions (no Firebase, no Android) that decide what an entrant is allowed to do
 * with a reservation, so JUnit can test them (see EntrantReservationRulesTest).
 *
 * To cover:
 * - US 01.07.01 As an entrant, I want to cancel my campsite reservation.
 * - US 01.06.01 As an entrant, I want to view a list of my active reservations.
 * - US 01.05.01 As an entrant, I want to receive confirmation of my campsite reservation.
 */
object EntrantReservationRules {

    /** Title of the confirmation shown to the entrant (US 01.05.01). */
    const val CONFIRMATION_TITLE = "Reservation confirmed"

    /**
     * Checks if an entrant can cancel a reservation (US 01.07.01).
     * A reservation can only be cancelled when it is the entrant's own, it is still ACTIVE
     * and it has not ended yet.
     *
     * @param reservation The reservation the entrant wants to cancel.
     * @param entrantId The id of the entrant who is trying to cancel.
     * @param today Today as UTC midnight in millis (see ReservationQueries.todayUtcMidnight).
     * @return `null` if the reservation can be cancelled, otherwise the reason it cannot.
     */
    fun cancelError(reservation: Reservation, entrantId: String, today: Long): String? {
        // someone else's reservation
        if (reservation.entrantId != entrantId) return "This is not your reservation"
        // already cancelled or rejected by the owner
        if (reservation.status != ReservationStatus.ACTIVE) return "Only active reservations can be cancelled"
        // the last reserved date is before today
        if (ReservationQueries.isPast(reservation, today)) return "Past reservations cannot be cancelled"
        return null
    }

    /**
     * Checks if an entrant can confirm a reservation (US 01.05.01).
     * They must have picked dates, accepted the campsite rules (US 02.11.01), won the lottery
     * for this campsite, be inside the booking period, and the dates must be inside the
     * booking period and still free.
     *
     * @param reservation The reservation the entrant wants to confirm.
     * @param lottery The lottery the entrant won.
     * @param entrantId The id of the entrant who is confirming.
     * @param bookedDates Dates of this campsite that other active reservations already took
     * (see ReservationQueries.bookedDates).
     * @param now The current time in millis.
     * @return `null` if the reservation can be confirmed, otherwise the reason it cannot.
     */
    fun confirmError(
        reservation: Reservation,
        lottery: Lottery,
        entrantId: String,
        bookedDates: List<Long>,
        now: Long,
    ): String? {
        if (reservation.dates.isEmpty()) return "Pick at least one date"
        if (!reservation.rulesAcknowledged) return "You must confirm you read the campsite rules"
        // with several campsites in one lottery the entrant picks one of them
        if (!lottery.campsites().contains(reservation.campsiteId)) return "This campsite is not part of the lottery"
        // Lottery.canReserve checks the draw happened, the entrant won and the booking period is open
        if (!lottery.canReserve(entrantId, now)) return "You can only reserve if you won the lottery and the booking period is open"
        if (!lottery.allowsDates(reservation.dates)) return "Pick dates inside the booking period"
        if (reservation.dates.any { it in bookedDates }) return "Some of those dates are already booked"
        return null
    }

    /**
     * Text of the confirmation shown to the entrant (US 01.05.01).
     * @param reservation The reservation that was confirmed.
     */
    fun confirmationMessage(reservation: Reservation): String =
        "Your reservation at ${reservation.campsiteName} is confirmed for " +
                ReservationQueries.describeDates(reservation.dates) + "."
}