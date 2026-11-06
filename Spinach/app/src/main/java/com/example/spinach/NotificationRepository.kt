package com.example.spinach

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore

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