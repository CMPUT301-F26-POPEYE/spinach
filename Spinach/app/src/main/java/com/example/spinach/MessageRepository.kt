package com.example.spinach

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class MessageRepository(
    db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private val messages = db.collection("messages")

    // US 02.12.01
    fun send(
        reservation: Reservation,
        ownerId: String,
        text: String,
        onDone: (Result<String>) -> Unit,
    ) {
        val error = MessageRules.error(text)
        if (error != null) {
            onDone(Result.failure(Exception(error)))
            return
        }
        if (!MessageRules.canMessage(reservation, ownerId)) {
            onDone(Result.failure(Exception("You can only message entrants on your own reservations")))
            return
        }
        val document = messages.document()
        document.set(
            Message(
                senderId = ownerId,
                recipientId = reservation.entrantId,
                reservationId = reservation.id,
                campsiteId = reservation.campsiteId,
                campsiteName = reservation.campsiteName,
                text = text.trim(),
                createdAt = System.currentTimeMillis(),
            ),
        )
            .addOnSuccessListener { onDone(Result.success(document.id)) }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }

    // US 03.12.01: the admin log of every message. Call remove() when the screen goes away.
    fun listenAll(
        onResult: (List<Message>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration =
        messages.addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            onResult(snapshot?.toObjects(Message::class.java).orEmpty())
        }
}
