
package com.example.spinach

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class LotteryRepository {

    private val db = FirebaseFirestore.getInstance()
    fun create(lottery: Lottery, userId: String, onDone: (Result<String>) -> Unit) {// US 0.2.04.01, crating lottery for campsite

        //This if function was created with the assistance of Open AI, Nov 5, 2026 "Create lottery test file
        if (lottery.campsiteId.isBlank() || userId.isBlank()) {
            onDone(
                Result.failure(
                    IllegalArgumentException(
                        "You must be signed in and select a campsite before creating a lottery"
                    )
                )
            )
            return
        }

        val error = lottery.validationError()//checking for all lot details

        if (error != null) {
            onDone(Result.failure(Exception(error)))
            return
        }

        db.collection("campsites").document(lottery.campsiteId).get()//checking if user owns campsite
            .addOnSuccessListener { campsite ->

                val ownerId = campsite.getString("ownerId")

                if (ownerId != userId) {//only owner can create lottery
                    onDone(Result.failure(Exception("Only the campsite owner can create a lottery")))
                    return@addOnSuccessListener
                }

                lottery.ownerId = userId

                val lotteryRef = db.collection("lotteries").document()//save lot to firestore

                lotteryRef.set(lottery)//saving and retunring the id if works
                    .addOnSuccessListener {
                        onDone(Result.success(lotteryRef.id))
                    }
                    .addOnFailureListener { error ->
                        onDone(Result.failure(error))
                    }
            }
            .addOnFailureListener { error ->
                onDone(Result.failure(error))
            }
    }

    fun listenForCampsite(//getting lot for campsite, also updating when they change
        campsiteId: String,
        onResult: (List<Lottery>) -> Unit
    ): ListenerRegistration {

        return db.collection("lotteries")
            .whereEqualTo("campsiteId", campsiteId)
            .addSnapshotListener { snapshot, error ->

                if (error != null || snapshot == null) {//return empty list if cant load lot
                    onResult(emptyList())
                } else {
                    val lotteries = snapshot.toObjects(Lottery::class.java)
                    onResult(lotteries)
                }
            }
    }

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
