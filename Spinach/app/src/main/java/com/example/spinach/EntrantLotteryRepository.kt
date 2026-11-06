package com.example.spinach

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query


/**
 * EntrantLotteryRepository
 * Entrant side of lotteries in Firestore. The owner side is LotteryRepository.
 * Listeners keep calling onResult when data changes. Call remove() on the returned
 * registration when the screen goes away.
 *
 * To cover:
 * - US 01.02.02 As an entrant, I want to leave the waiting list for a campsite lottery.
 * - US 01.03.01 As an entrant, I want to receive a notification when I win the campsite lottery.
 * - US 01.03.02 As an entrant, I want to receive a notification when I lose the campsite lottery.
 *
 * @param db The Firestore database. Can be replaced in tests.
 */
class EntrantLotteryRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private val lotteries = db.collection("lotteries")

    /**
     * Listens for the lotteries the entrant is still waiting in (US 01.02.02).
     * @param entrantId The id of the entrant.
     * @param onResult Called with the lotteries now and every time they change.
     * @param onError Called if Firestore could not load the lotteries.
     * @return The registration to remove() when the screen goes away.
     */
    fun listenWaitingFor(
        entrantId: String,
        onResult: (List<Lottery>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration =
        listen(lotteries.whereArrayContains("waitlist", entrantId), onResult, onError)

    /**
     * Listens for the lotteries the entrant won (US 01.03.01).
     * Use EntrantLotteryRules.resultTitle and resultMessage for the text to show.
     */
    fun listenWonBy(
        entrantId: String,
        onResult: (List<Lottery>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration =
        listen(lotteries.whereArrayContains("winners", entrantId), onResult, onError)

    /**
     * Listens for the lotteries the entrant lost (US 01.03.02).
     * Use EntrantLotteryRules.resultTitle and resultMessage for the text to show.
     */
    fun listenLostBy(
        entrantId: String,
        onResult: (List<Lottery>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration =
        listen(lotteries.whereArrayContains("losers", entrantId), onResult, onError)

    /**
     * Removes the entrant from the waiting list of a lottery (US 01.02.02).
     * This runs in a transaction, like the draw in LotteryRepository, so the entrant cannot be
     * removed while the draw is happening. It fails if EntrantLotteryRules says they cannot leave.
     *
     * @param lottery The lottery to leave.
     * @param entrantId The id of the entrant who is leaving.
     * @param onDone Called with success, or with the reason it failed.
     */
    fun leaveWaitlist(
        lottery: Lottery,
        entrantId: String,
        onDone: (Result<Unit>) -> Unit,
    ) {
        val reference = lotteries.document(lottery.id)
        db.runTransaction { transaction ->
            // read the newest version of the lottery, it may have been drawn since the screen loaded
            // This code was produced partly by Claude Sonnet 5.5, 6/11/2026 over the prompt
            // "Explain and help me with the argument handling of this part here".
            val current = transaction.get(reference).toObject(Lottery::class.java)
                ?: throw IllegalArgumentException("This lottery no longer exists")

            val error = EntrantLotteryRules.leaveError(current, entrantId)
            if (error != null) throw IllegalArgumentException(error)

            transaction.update(reference, "waitlist", FieldValue.arrayRemove(entrantId))
            null
        }
            .addOnSuccessListener { onDone(Result.success(Unit)) }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }

    private fun listen(
        query: Query,
        onResult: (List<Lottery>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration =
        query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            onResult(snapshot?.toObjects(Lottery::class.java).orEmpty())
        }
}