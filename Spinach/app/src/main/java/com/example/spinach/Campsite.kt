package com.example.spinach
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.GeoPoint
/**
 * A campsite that an owner registers and entrants can browse.
 *
 * Designed to be stored in and read back from Firestore: every property has a
 * default value so Firestore can deserialize it with its no-argument constructor.
 *
 * @property id Firestore document ID, populated automatically on read via [DocumentId].
 * @property name display name of the campsite.
 * @property location human-readable location text.
 * @property description details and amenities.
 * @property maxOccupancy maximum number of people allowed; must be a positive whole number.
 * @property mapCoordinates geographic position; defaults to (0.0, 0.0) meaning "not set".
 * @property tags descriptive tags for the campsite.
 * @property images URLs of images for the campsite (optional).
 * @property qrCode generated QR code value for the campsite.
 * @property ownerId UID of the user who owns this campsite.
 * @property ratings individual ratings (1.0–5.0) left by entrants.
 * @property reviews review comments, parallel to [ratings].
 */



class Campsite (
    @DocumentId var id: String = "",
    var name: String = "",
    var location: String = "",
    var description: String = "",
    var maxOccupancy: Int = 0,
    var mapCoordinates: GeoPoint = GeoPoint(0.0,0.0),
    var tags: MutableList<String> = mutableListOf(),
    var images: MutableList<String> = mutableListOf(), //URL of the image can be stored
    var qrCode: String = "",
    var ownerId: String = "",
    var ratings: MutableList<Double> = mutableListOf(),
    var reviews: MutableList<String> = mutableListOf(),
    ){
    /** Average of all [ratings], or 0.0 if there are none. Not stored in Firestore. */
    @get:Exclude
    val avgRating: Double
        get() = if(ratings.isEmpty()) 0.0 else ratings.average()
    /** A one-line summary of the campsite for list views. */
    @Exclude
    fun viewSummary(): String = "$name, $location, rating: $avgRating"
    /** A full multi-line description of every field, for the details screen. */

    @Exclude
    fun detailView(): String = "$name\n$location\n$description\nMaximum occupancy: $maxOccupancy\nMap coordinates: $mapCoordinates\nTags: $tags\nimages: $images\nQR code: $qrCode\nOwner: $ownerId\nRating: $avgRating\nReviews:\n$reviews\n"
    /**
     * Updates the given fields in place. Any argument left null is ignored,
     * so only the fields you pass are changed.
     *
     * @param name new name, or null to leave unchanged.
     * @param location new location, or null to leave unchanged.
     * @param description new description, or null to leave unchanged.
     * @param maxOccupancy new maximum occupancy, or null to leave unchanged.
     * @param mapCoordinates new coordinates, or null to leave unchanged.
     * @param tags new tags, or null to leave unchanged.
     * @param images new images, or null to leave unchanged.
     * @param qrCode new QR code, or null to leave unchanged.
     * @param ownerId new owner ID, or null to leave unchanged.
     */

    fun updateDetails(
        name: String? = null,
        location: String? = null,
        description: String? = null,
        maxOccupancy: Int? = null,
        mapCoordinates: GeoPoint? = null,
        tags: List<String>? = null,
        images: List<String>? = null,
        qrCode: String? = null,
        ownerId: String? = null,

    ){
        name?.let { this.name = it }
        location?.let { this.location = it }
        description?.let { this.description = it }
        maxOccupancy?.let { this.maxOccupancy = it }
        mapCoordinates?.let { this.mapCoordinates = it }
        tags?.let { this.tags = it.toMutableList() }
        images?.let { this.images = it.toMutableList() }
        qrCode?.let { this.qrCode = it }
        ownerId?.let { this.ownerId = it }
    }
    fun addReview(rating: Double, comment: String){
        require(rating in 1.0..5.0){"Rating must be between 1 and 5, got $rating instead"}
        ratings.add(rating)
        reviews.add(comment)
    }
    @Exclude
    fun validationError(): String? {
        if(name.isBlank()||location.isBlank()||description.isBlank()||tags.isEmpty()||(mapCoordinates.latitude == 0.0 && mapCoordinates.longitude == 0.0)){
            return "All fields are required!"
        }
        if (maxOccupancy <= 0)
            return "Maximum occupancy must be greater than 0"

        return null
    }
}