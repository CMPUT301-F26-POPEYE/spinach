
package com.example.spinach

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Manages campsite lotteries stored in Firebase Firestore.
 * Uses the Repository pattern to separate database operations
 * from the Lottery model and user interface.
 *
 * Outstanding issue: Lottery draws currently depend on the
 * application running rather than a scheduled server trigger.
 */
class LotteryRepository {

    private val db = FirebaseFirestore.getInstance()

    /**
     * Creates a lottery after validating its information and
     * verifying ownership of all selected campsites.
     * Saves the lottery to Firestore and reports success or failure.
     */
    //This function was created by the help of Claude, Anthropic "Implementing Lottery Creation". November 4, 2026
    fun create(lottery: Lottery, userId: String, onDone: (Result<String>) -> Unit) {// US 02.04.01 and 02.04.02
        val campsiteIds = lottery.campsites().filter { it.isNotBlank() }.distinct()
        if (campsiteIds.isEmpty() || userId.isBlank()) {
            onDone(
                Result.failure(
                    IllegalArgumentException(
                        "You must be signed in and select a campsite before creating a lottery"
                    )
                )
            )
            return
        }

        val error = lottery.validationError()
        if (error != null) {
            onDone(Result.failure(Exception(error)))
            return
        }

        checkOwnsAll(campsiteIds, userId) { problem ->
            if (problem != null) {
                onDone(Result.failure(problem))
                return@checkOwnsAll
            }

            lottery.ownerId = userId
            lottery.campsiteId = campsiteIds.first()
            lottery.campsiteIds = campsiteIds.toMutableList()

            val lotteryRef = db.collection("lotteries").document()
            lotteryRef.set(lottery)
                .addOnSuccessListener { onDone(Result.success(lotteryRef.id)) }
                .addOnFailureListener { onDone(Result.failure(it)) }
        }
    }

    // only the owner of every campsite can put them in a lottery
    private fun checkOwnsAll(campsiteIds: List<String>, userId: String, onDone: (Exception?) -> Unit) {
        var remaining = campsiteIds.size
        var failed = false
        for (id in campsiteIds) {
            db.collection("campsites").document(id).get()
                .addOnSuccessListener { campsite ->
                    if (failed) return@addOnSuccessListener
                    if (campsite.getString("ownerId") != userId) {
                        failed = true
                        onDone(Exception("Only the campsite owner can create a lottery"))
                        return@addOnSuccessListener
                    }
                    remaining -= 1
                    if (remaining == 0) onDone(null)
                }
                .addOnFailureListener { error ->
                    if (failed) return@addOnFailureListener
                    failed = true
                    onDone(error)
                }
        }
    }

    /**
     * Listens for lotteries associated with a campsite.
     * Updates the lottery list whenever Firestore data changes.
     */
    fun listenForCampsite(//getting lot for campsite, also updating when they change
        campsiteId: String,
        onResult: (List<Lottery>) -> Unit
    ): ListenerRegistration {

        return db.collection("lotteries")
                .whereArrayContains("campsiteIds", campsiteId)
                .addSnapshotListener { snapshot, error ->

                if (error != null || snapshot == null) {//return empty list if cant load lot
                    onResult(emptyList())
                } else {
                    val lotteries = snapshot.toObjects(Lottery::class.java)
                    onResult(lotteries)
                }
            }
    }

    /**
     * Listens for lotteries that other owners may add campsites to: shared and not yet drawn.
     * Updates the list whenever Firestore data changes.
     *
     * @param onResult Called with the current shared lotteries, or an empty list if they can't be loaded.
     * @return The listener registration; call remove() on it when the screen closes.
     */
    // Shared lottery story
    fun listenForSharedLotteries(onResult: (List<Lottery>) -> Unit): ListenerRegistration =
        db.collection("lotteries")
            .whereEqualTo("openToOtherOwners", true)
            .whereEqualTo("drawn", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    onResult(emptyList())
                } else {
                    onResult(snapshot.toObjects(Lottery::class.java))
                }
            }

    /**
     * Adds one of an owner's campsites to a shared lottery.
     *
     * Runs in a transaction, so the lottery can't be drawn while the campsite is being added.
     * The checks are in [Lottery.addCampsiteError], and the Firestore rules enforce the same.
     *
     * @param lotteryId The lottery to add the campsite to.
     * @param campsiteId The campsite to add. It must belong to [userId].
     * @param userId The signed-in owner.
     * @param onDone Called with success, or with the reason the campsite couldn't be added.
     */
    // Shared lottery story
    fun addCampsite(lotteryId: String, campsiteId: String, userId: String, onDone: (Result<Unit>) -> Unit) {
        val lotteryRef = db.collection("lotteries").document(lotteryId)
        val campsiteRef = db.collection("campsites").document(campsiteId)
        db.runTransaction { transaction ->
            val lottery = transaction.get(lotteryRef).toObject(Lottery::class.java)
                ?: throw IllegalArgumentException("Lottery not found")
            val campsiteOwner = transaction.get(campsiteRef).getString("ownerId")
            lottery.addCampsiteError(userId, campsiteId, campsiteOwner)?.let { throw IllegalArgumentException(it) }
            transaction.update(
                lotteryRef,
                mapOf(
                    "campsiteIds" to FieldValue.arrayUnion(campsiteId),
                    "ownerIds" to FieldValue.arrayUnion(userId),
                ),
            )
            null
        }
            .addOnSuccessListener { onDone(Result.success(Unit)) }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }

    /**
     * Finds lotteries whose registration periods have ended.
     * Randomly selects winners, records losers, and saves the
     * results using Firestore transactions to prevent repeated draws.
     */
    fun drawDueLotteries() { // US 0.2.05.01, drawing winners

        val currentTime = System.currentTimeMillis()

        db.collection("lotteries")//only undrrwn
            .whereEqualTo("drawn", false)
            .get()
            .addOnSuccessListener { snapshot ->

                for (document in snapshot.documents) {
                    db.runTransaction { transaction ->// transaction so no draws overwrite
                        val lottery = transaction.get(document.reference)
                            .toObject(Lottery::class.java)

                        if (lottery != null) {
                            if (!lottery.drawn && lottery.registrationOver(currentTime)) {

                                lottery.draw()//this will chose the winners and losers

                                // update only the draw fields, so fields added later (like ownerIds)
                                // that older lotteries don't have aren't written and the rules allow it
                                transaction.update(
                                    document.reference,
                                    mapOf(
                                        "waitlist" to lottery.waitlist,
                                        "winners" to lottery.winners,
                                        "losers" to lottery.losers,
                                        "drawn" to lottery.drawn,
                                    ),
                                )
                            }
                        }
                        null
                    }
                }
            }
    }
}
