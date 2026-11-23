
package com.example.spinach

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Saves and loads campsite ratings using Firestore.
 *
 * Created with the help of ChatGPT, OpenAI,
 * "CMPUT 301 Campsite Ratings and Reviews", November 23, 2026.
 */
class ReviewRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /**
     * Saves a rating for a campsite.
     * If the entrant rated it before, their old rating is replaced.
     */
    suspend fun saveRating(
        campsiteId: String,
        entrantId: String,
        rating: Int
    ) {
        // Check the rating before saving
        val review = Review(
            campsiteId = campsiteId,
            authorId = entrantId,
            rating = rating
        )

        review.validationError()?.let {
            throw IllegalArgumentException(it)
        }

        val campsiteRef = db.collection("campsites").document(campsiteId)

        // One review document per entrant for each campsite
        val reviewRef = campsiteRef.collection("reviews").document(entrantId)

        db.runTransaction { transaction ->
            val campsite = transaction.get(campsiteRef)
                .toObject(Campsite::class.java)
                ?: throw IllegalArgumentException("Campsite not found")

            val previous = transaction.get(reviewRef)
                .toObject(Review::class.java)

            val ratings = campsite.ratings.toMutableList()

            // Remove this entrant's previous rating, if they had one
            if (previous != null) {
                ratings.remove(previous.rating.toDouble())
            }

            ratings.add(rating.toDouble())

            // Keep any comment already submitted by this entrant
            review.comment = previous?.comment.orEmpty()

            // Update the campsite's ratings and save the review together
            transaction.update(campsiteRef, "ratings", ratings)
            transaction.set(reviewRef, review)

            Unit
        }.await()
    }

    /**
     * Gets the rating this entrant previously gave a campsite.
     */
    suspend fun getMyRating(campsiteId: String, entrantId: String): Int {
        val snapshot = db.collection("campsites")
            .document(campsiteId)
            .collection("reviews")
            .document(entrantId)
            .get()
            .await()

        return snapshot.toObject(Review::class.java)?.rating ?: 0
    }
}


