
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


    /**
     * Updates an existing lottery after checking ownership and dates.
     * A lottery cannot be changed once winners have been drawn.
     *
     * Created with the help of ChatGPT, OpenAI,
     * "CMPUT 301 Update Lottery Listing", November 23, 2026.
     */
    fun update(
        lottery: Lottery,
        userId: String,
        onDone: (Result<Unit>) -> Unit
    ) {
        // Check that a lottery and owner were provided
        if (lottery.id.isBlank() || userId.isBlank()) {
            onDone(Result.failure(Exception("Lottery or owner is missing")))
            return
        }

        // Get the selected campsites without duplicates
        val campsiteIds = lottery.campsites()
            .filter { it.isNotBlank() }
            .distinct()

        if (campsiteIds.isEmpty()) {
            onDone(Result.failure(Exception("Select at least one campsite")))
            return
        }

        // Use the same validation rules as lottery creation
        lottery.validationError()?.let { error ->
            onDone(Result.failure(Exception(error)))
            return
        }

        val lotteryRef = db.collection("lotteries").document(lottery.id)

        // A transaction prevents saving changes after another user runs the draw
        db.runTransaction { transaction ->
            val existing = transaction.get(lotteryRef)
                .toObject(Lottery::class.java)
                ?: throw Exception("Lottery not found")

            if (existing.ownerId != userId) {
                throw Exception("You can only edit your own lotteries")
            }

            if (existing.drawn) {
                throw Exception("This lottery has already been drawn")
            }

            // Make sure the owner owns every selected campsite
            for (id in campsiteIds) {
                val campsiteRef = db.collection("campsites").document(id)
                val campsite = transaction.get(campsiteRef)

                if (campsite.getString("ownerId") != userId) {
                    throw Exception("You can only select campsites you own")
                }
            }

            val capacity = lottery.waitlistCapacity

            // Don't lower capacity below the current number of entrants
            if (capacity != null && capacity < existing.waitlist.size) {
                throw Exception("Capacity is below the current waitlist size")
            }

            // Update editable fields only; keep the current entrants and results
            transaction.update(
                lotteryRef,
                mapOf(
                    "campsiteId" to campsiteIds.first(),
                    "campsiteIds" to campsiteIds,
                    "registrationStart" to lottery.registrationStart,
                    "registrationEnd" to lottery.registrationEnd,
                    "bookingStart" to lottery.bookingStart,
                    "bookingEnd" to lottery.bookingEnd,
                    "winnerCount" to lottery.winnerCount,
                    "waitlistCapacity" to capacity
                )
            )

            Unit
        }.addOnSuccessListener {
            onDone(Result.success(Unit))
        }.addOnFailureListener { error ->
            onDone(Result.failure(error))
        }
    }


    /**
     * Loads the lotteries created by a specific owner.
     * The list updates whenever something changes in Firestore.
     *
     * Created with the help of ChatGPT, OpenAI,
     * "CMPUT 301 Update Lottery Listing", November 24, 2026.
     */
    fun listenForOwner(
        ownerId: String,
        onResult: (List<Lottery>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {

        // Only show lotteries belonging to the signed-in owner
        return db.collection("lotteries")
            .whereEqualTo("ownerId", ownerId)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    onError(error)
                } else {
                    onResult(
                        snapshot?.toObjects(Lottery::class.java) ?: emptyList()
                    )
                }
            }
    }


}
