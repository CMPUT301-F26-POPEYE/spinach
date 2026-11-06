package com.example.spinach

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// Plain functions, no Firebase or Android, so JUnit can test them.
// "today" is UTC midnight of the current calendar day, the same form as Reservation.dates.
object ReservationQueries {

    const val DAY_MILLIS = 86_400_000L

    fun todayUtcMidnight(): Long {
        val local = Calendar.getInstance()
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utc.clear()
        utc.set(
            local.get(Calendar.YEAR),
            local.get(Calendar.MONTH),
            local.get(Calendar.DAY_OF_MONTH),
        )
        return utc.timeInMillis
    }

    // A reservation with no dates is never past.
    fun isPast(reservation: Reservation, today: Long): Boolean {
        val last = reservation.dates.maxOrNull() ?: return false
        return last < today
    }

    // US 02.07.01: ACTIVE and not ended, soonest first.
    fun active(reservations: List<Reservation>, today: Long): List<Reservation> =
        reservations
            .filter { it.status == ReservationStatus.ACTIVE && !isPast(it, today) }
            .sortedBy { it.dates.minOrNull() ?: Long.MAX_VALUE }

    // US 02.07.02: ACTIVE and ended, most recent first.
    fun past(reservations: List<Reservation>, today: Long): List<Reservation> =
        reservations
            .filter { it.status == ReservationStatus.ACTIVE && isPast(it, today) }
            .sortedByDescending { it.dates.maxOrNull() ?: Long.MIN_VALUE }

    // US 02.06.01: dates today or later, booked for one campsite by an ACTIVE reservation.
    fun bookedDates(
        reservations: List<Reservation>,
        campsiteId: String,
        today: Long,
    ): List<Long> =
        active(reservations.filter { it.campsiteId == campsiteId }, today)
            .flatMap { it.dates }
            .filter { it >= today }
            .distinct()
            .sorted()

    // For example "Fri 6 Nov 2026". Read in UTC because dates are stored at UTC midnight.
    fun formatDay(day: Long, locale: Locale = Locale.getDefault()): String {
        val format = SimpleDateFormat("EEE d MMM yyyy", locale)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(day)
    }

    // Consecutive days become a range, anything else a comma list.
    fun describeDates(dates: List<Long>, locale: Locale = Locale.getDefault()): String {
        if (dates.isEmpty()) return "No dates"
        val sorted = dates.distinct().sorted()
        if (sorted.size == 1) return formatDay(sorted[0], locale)
        val consecutive = sorted.zipWithNext().all { (a, b) -> b - a == DAY_MILLIS }
        return if (consecutive) {
            "${formatDay(sorted.first(), locale)} to ${formatDay(sorted.last(), locale)}"
        } else {
            sorted.joinToString(", ") { formatDay(it, locale) }
        }
    }
}