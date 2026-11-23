
package com.example.spinach

import com.google.firebase.firestore.DocumentId

/**
 * Stores a rating and optional review for a campsite.
 *
 * Created with the help of ChatGPT, OpenAI,
 * "CMPUT 301 Campsite Ratings and Reviews", November 23, 2026.
 */
class Review(
    @DocumentId var id: String = "",
    var campsiteId: String = "",
    var authorId: String = "",
    var rating: Int = 0,
    var comment: String = "",
) {

    /**
     * Checks that the rating is between 1 and 5.
     */
    fun validationError(): String? {
        if (campsiteId.isBlank()) return "Campsite is required"
        if (authorId.isBlank()) return "Entrant is required"
        if (rating !in 1..5) return "Rating must be between 1 and 5"

        return null
    }
}


