package com.example.spinach

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

// US 02.11.01: the rules and policies an owner writes for one campsite.
// Stored in the "campsiteRules" collection, one document per campsite, with the campsite id as the document id.
class CampsiteRules(
    @DocumentId var id: String = "",
    var ownerId: String = "",
    var text: String = "",
    var updatedAt: Long = 0L,
)

object RulesPolicy {

    const val MAX_LENGTH = 2000

    fun error(text: String): String? =
        if (text.length > MAX_LENGTH) "Rules must be $MAX_LENGTH characters or fewer" else null

    // A campsite with no rules needs no confirmation. Otherwise the entrant must tick the box.
    // The reservation flow (US 01.04.x) should only save a reservation when this is true.
    fun canConfirm(rulesText: String, acknowledged: Boolean): Boolean =
        rulesText.isBlank() || acknowledged
}

class CampsiteRulesRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    // Also used by the entrant side to read the rules before reserving. null means no rules yet.
    fun listen(campsiteId: String, onResult: (CampsiteRules?) -> Unit): ListenerRegistration =
        db.collection("campsiteRules").document(campsiteId)
            .addSnapshotListener { snapshot, _ ->
                onResult(snapshot?.toObject(CampsiteRules::class.java))
            }

    fun save(campsiteId: String, ownerId: String, text: String, onDone: (Result<Unit>) -> Unit) {
        val error = RulesPolicy.error(text)
        if (error != null) {
            onDone(Result.failure(Exception(error)))
            return
        }
        db.collection("campsites").document(campsiteId).get()
            .addOnSuccessListener { campsite ->
                if (campsite.getString("ownerId") != ownerId) {
                    onDone(Result.failure(Exception("Only the campsite owner can set the rules")))
                    return@addOnSuccessListener
                }
                db.collection("campsiteRules").document(campsiteId)
                    .set(CampsiteRules(ownerId = ownerId, text = text.trim(), updatedAt = System.currentTimeMillis()))
                    .addOnSuccessListener { onDone(Result.success(Unit)) }
                    .addOnFailureListener { onDone(Result.failure(it)) }
            }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }
}
