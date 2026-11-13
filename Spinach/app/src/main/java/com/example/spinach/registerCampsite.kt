package com.example.spinach
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await


//this code was partly produced by claude with the promt, "How should i go about solving these issues" nov 5 2026
class CampsiteRepository{

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Validates a campsite, stamps the current user as its owner, and saves it
     * to Firestore as a new document.
     *
     * On success, the campsite's [Campsite.id] is updated to the generated
     * document ID.
     *
     * @param campsite the campsite to register.
     * @return the generated Firestore document ID.
     * @throws IllegalArgumentException if validation fails, or if no user is signed in.
     */
    suspend fun registerCampsite(campsite: Campsite): String {
        campsite.validationError()?.let{throw IllegalArgumentException(it)}
        val uid = auth.currentUser?.uid ?: throw IllegalArgumentException("Not signed in")
        campsite.ownerId = uid

        val docRef = db.collection("campsites").add(campsite).await()
        campsite.id = docRef.id
        return docRef.id

        /**
         * Listens for changes to the current user's campsites and reports them live.
         *
         * Runs a Firestore query for campsites whose ownerId matches the signed-in
         * user and attaches a snapshot listener, so [onChange] is called again
         * whenever the data changes. Does nothing if no user is signed in.
         *
         * @param onChange callback invoked with the current list of the user's
         *   campsites each time the data updates.
         */
    }
    fun ownerCampsites(onChange: (List<Campsite>) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("campsites")
            .whereEqualTo("ownerId", uid)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.toObjects(Campsite::class.java) ?: emptyList()
                onChange(list)
            }
    }

    /**
     * Saves changes to an existing campsite, but only if the current user owns it.
     *
     * @param campsite the campsite to update; its [Campsite.id] must be set
     *   (i.e. it was loaded from Firestore, not freshly constructed).
     * @throws IllegalStateException if no user is signed in, or if the campsite
     *   is not owned by the current user.
     * @throws IllegalArgumentException if validation fails.
     */
    suspend fun updateCampsite(campsite: Campsite) {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not signed in")
        if (campsite.ownerId != uid) throw IllegalStateException("Not your campsite")
        campsite.validationError()?.let { throw IllegalArgumentException(it) }

        db.collection("campsites").document(campsite.id).set(campsite).await()
    }

    /**
     * Fetches a single campsite by its document ID.
     *
     * @param id the Firestore document ID of the campsite.
     * @return the campsite, or null if no document with that ID exists.
     */
     suspend fun getCampsite(id: String): Campsite? =
        db.collection("campsites").document(id).get().await()
            .toObject(Campsite::class.java)
}




