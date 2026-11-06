package com.example.spinach

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class NotificationRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    // US 02.10.01: one notification per waitlisted entrant, reports how many were written
    fun sendToWaitlists(
        lotteries: List<Lottery>,
        senderId: String,
        message: String,
        onDone: (Result<Int>) -> Unit,
    ) {
        val error = NotificationRules.messageError(message)
        if (error != null) {
            onDone(Result.failure(Exception(error)))
            return
        }

        val pairs = NotificationRules.waitlistedPairs(lotteries)
        if (pairs.isEmpty()) {
            onDone(Result.failure(Exception("Nobody is on the waitlist")))
            return
        }

        findOptedOut(pairs.map { it.second }.distinct()) { result ->
            result.fold(
                onSuccess = { optedOut ->
                    val chosen = NotificationRules.recipients(pairs, optedOut)
                    if (chosen.isEmpty()) {
                        onDone(Result.failure(Exception("Everyone on the waitlist has turned notifications off")))
                    } else {
                        write(chosen, senderId, message.trim(), onDone)
                    }
                },
                onFailure = { onDone(Result.failure(it)) },
            )
        }
    }

    // US 02.10.02: everything this owner has sent. Call remove() when the screen goes away.
    fun listenForSender(
        senderId: String,
        onResult: (List<Notification>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration =
        listen(db.collection("notifications").whereEqualTo("senderId", senderId), onResult, onError)

    // US 03.08.01: the admin log of every notification
    fun listenAll(
        onResult: (List<Notification>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration = listen(db.collection("notifications"), onResult, onError)

    private fun listen(
        query: Query,
        onResult: (List<Notification>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration =
        query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            onResult(snapshot?.toObjects(Notification::class.java).orEmpty())
        }

    // US 01.10.01: a user document with notificationsEnabled == false means opted out.
    // No user document means we send.
    private fun findOptedOut(entrantIds: List<String>, onDone: (Result<Set<String>>) -> Unit) {
        val optedOut = mutableSetOf<String>()
        var remaining = entrantIds.size
        var failed = false
        for (id in entrantIds) {
            db.collection("users").document(id).get()
                .addOnSuccessListener { user ->
                    if (failed) return@addOnSuccessListener
                    if (user.exists() && user.getBoolean("notificationsEnabled") == false) {
                        optedOut.add(id)
                    }
                    remaining -= 1
                    if (remaining == 0) onDone(Result.success(optedOut))
                }
                .addOnFailureListener { error ->
                    if (failed) return@addOnFailureListener
                    failed = true
                    onDone(Result.failure(error))
                }
        }
    }

    private fun write(
        recipients: List<Pair<String, String>>,
        senderId: String,
        message: String,
        onDone: (Result<Int>) -> Unit,
    ) {
        val now = System.currentTimeMillis()
        val tasks = recipients.chunked(400).map { chunk ->
            val batch = db.batch()
            for ((lotteryId, entrantId) in chunk) {
                batch.set(
                    db.collection("notifications").document(),
                    Notification(
                        recipientId = entrantId,
                        senderId = senderId,
                        lotteryId = lotteryId,
                        message = message,
                        createdAt = now,
                    ),
                )
            }
            batch.commit()
        }
        Tasks.whenAll(tasks)
            .addOnSuccessListener { onDone(Result.success(recipients.size)) }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }
}