package com.example.spinach

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot

// Everything the admin screens read and change.
// Being an admin means a document exists at admins/{userId}. Create it by hand in the Firebase console.
// The app only hides the admin screens from other people. Real protection needs Firestore security rules.
class AdminRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    fun isAdmin(userId: String, onDone: (Boolean) -> Unit) {
        db.collection("admins").document(userId).get()
            .addOnSuccessListener { onDone(it.exists()) }
            .addOnFailureListener { onDone(false) }
    }

    // US 03.04.01 and US 03.03.01 read the campsites, US 03.06.01 reads their images
    fun listenCampsites(
        onResult: (List<Campsite>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration =
        db.collection("campsites").addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            onResult(snapshot?.toObjects(Campsite::class.java).orEmpty())
        }

    // US 03.05.01
    fun listenProfiles(
        onResult: (List<ProfileSummary>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration =
        db.collection("users").addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            onResult(
                snapshot?.documents.orEmpty().map {
                    ProfileSummary(
                        id = it.id,
                        name = it.getString("name").orEmpty(),
                        email = it.getString("email").orEmpty(),
                        phone = it.getString("phoneNumber").orEmpty(),
                    )
                },
            )
        }

    // US 03.01.01
    fun deleteCampsite(campsiteId: String, onDone: (Result<Unit>) -> Unit) =
        finish(db.collection("campsites").document(campsiteId).delete(), onDone)

    // US 03.02.01: removes the profile document. The sign-in account itself stays.
    fun deleteProfile(userId: String, onDone: (Result<Unit>) -> Unit) =
        finish(db.collection("users").document(userId).delete(), onDone)

    // US 03.03.01: takes the image off the campsite
    fun removeImage(campsiteId: String, url: String, onDone: (Result<Unit>) -> Unit) =
        finish(
            db.collection("campsites").document(campsiteId)
                .update("images", FieldValue.arrayRemove(url)),
            onDone,
        )

    // US 03.07.01: deletes the owner's profile, campsites and lotteries
    fun removeOwner(ownerId: String, onDone: (Result<Unit>) -> Unit) {
        if (ownerId.isBlank()) {
            onDone(Result.failure(Exception("This campsite has no owner id")))
            return
        }
        Tasks.whenAllSuccess<QuerySnapshot>(
            db.collection("campsites").whereEqualTo("ownerId", ownerId).get(),
            db.collection("lotteries").whereEqualTo("ownerId", ownerId).get(),
        )
            .addOnSuccessListener { results ->
                val refs: List<DocumentReference> =
                    results.flatMap { snapshot -> snapshot.documents.map { it.reference } } +
                        db.collection("users").document(ownerId)
                val tasks = refs.chunked(400).map { chunk ->
                    val batch = db.batch()
                    chunk.forEach { batch.delete(it) }
                    batch.commit()
                }
                Tasks.whenAll(tasks)
                    .addOnSuccessListener { onDone(Result.success(Unit)) }
                    .addOnFailureListener { onDone(Result.failure(it)) }
            }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }

    private fun finish(task: com.google.android.gms.tasks.Task<Void>, onDone: (Result<Unit>) -> Unit) {
        task.addOnSuccessListener { onDone(Result.success(Unit)) }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }
}
