package com.example.spinach

import com.google.firebase.firestore.DocumentId

// US 02.12.01: a message from a campsite owner to an entrant who reserved the campsite.
// Stored in the "messages" collection. The admin log (US 03.12.01) reads the same collection.
class Message(
    @DocumentId var id: String = "",
    var senderId: String = "",
    var recipientId: String = "",
    var reservationId: String = "",
    var campsiteId: String = "",
    var campsiteName: String = "",
    var text: String = "",
    var createdAt: Long = 0L,
)
