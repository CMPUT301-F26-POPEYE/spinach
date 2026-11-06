package com.example.spinach

// Plain functions, no Firebase, so JUnit can test them.
object MessageRules {

    fun error(text: String): String? =
        if (text.isBlank()) "Write a message first" else null

    // An owner can only message the entrant on one of their own reservations.
    fun canMessage(reservation: Reservation, ownerId: String): Boolean =
        reservation.id.isNotEmpty() &&
            reservation.ownerId == ownerId &&
            reservation.entrantId.isNotBlank()

    fun newestFirst(messages: List<Message>): List<Message> =
        messages.sortedByDescending { it.createdAt }
}
