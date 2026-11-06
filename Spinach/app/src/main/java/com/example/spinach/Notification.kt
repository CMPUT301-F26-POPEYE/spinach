package com.example.spinach

import com.google.firebase.firestore.DocumentId

// A message from an owner to one entrant. Stored in the "notifications" collection.
class Notification(
    @DocumentId var id: String = "",
    var recipientId: String = "",
    var senderId: String = "",
    var lotteryId: String = "",
    var message: String = "",
    var createdAt: Long = 0L,
    var read: Boolean = false,
)