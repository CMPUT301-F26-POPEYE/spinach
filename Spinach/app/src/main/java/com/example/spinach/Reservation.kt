package com.example.spinach

import com.google.firebase.firestore.DocumentId

// Past is not stored. An ACTIVE reservation is past once its last date is before today.
enum class ReservationStatus { ACTIVE, CANCELLED, REJECTED }

// dates: epoch millis at UTC midnight of each reserved day.
// campsiteName and ownerId are copied onto the reservation so an owner's list needs one query.
class Reservation(
    @DocumentId var id: String = "",
    var campsiteId: String = "",
    var campsiteName: String = "",
    var lotteryId: String = "",
    var entrantId: String = "",
    var ownerId: String = "",
    var dates: MutableList<Long> = mutableListOf(),
    var status: ReservationStatus = ReservationStatus.ACTIVE,
    var rulesAcknowledged: Boolean = false,
    var createdAt: Long = 0L,
)