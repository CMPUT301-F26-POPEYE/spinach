package com.example.spinach

// An uploaded image, found through the campsite that holds its URL.
data class ImageRef(val campsiteId: String, val campsiteName: String, val url: String)

// What the admin sees of a profile. Read from the "users" collection field by field,
// so it does not depend on the User class.
data class ProfileSummary(
    val id: String,
    val name: String,
    val email: String,
    val phone: String,
)

// Plain functions, no Firebase, so JUnit can test them.
object AdminRules {

    // US 03.03.01 and US 03.06.01
    fun images(campsites: List<Campsite>): List<ImageRef> =
        campsites.flatMap { campsite ->
            campsite.images
                .filter { it.isNotBlank() }
                .map { ImageRef(campsite.id, campsite.name, it) }
        }

    // Search box: a blank query matches everything.
    fun matches(query: String, vararg fields: String): Boolean =
        query.isBlank() || fields.any { it.contains(query.trim(), ignoreCase = true) }
}
