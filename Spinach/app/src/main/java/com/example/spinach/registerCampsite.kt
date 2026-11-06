package com.example.spinach
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await


class CampsiteRepository{

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()


    suspend fun registerCampsite(campsite: Campsite): String {
        campsite.validationError()?.let{throw IllegalArgumentException(it)}
        val uid = auth.currentUser?.uid ?: throw IllegalArgumentException("Not signed in")
        campsite.ownerId = uid

        val docRef = db.collection("campsites").add(campsite).await()
        campsite.id = docRef.id
        return docRef.id



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

     suspend fun getCampsite(id: String): Campsite? =
        db.collection("campsites").document(id).get().await()
            .toObject(Campsite::class.java)
}




