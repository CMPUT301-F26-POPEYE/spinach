
package com.example.spinach

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

                                transaction.set(document.reference, lottery)//for saving
                            }
                        }
                        null
                    }
                }
            }
    }
}
