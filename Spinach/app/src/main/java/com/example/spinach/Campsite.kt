package com.example.spinach
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.GeoPoint



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
    @get:Exclude
    val avgRating: Double
        get() = if(ratings.isEmpty()) 0.0 else ratings.average()

    @Exclude
    fun viewSummary(): String = "$name, $location, rating: $avgRating"

    @Exclude
    fun detailView(): String = "$name\n$location\n$description\nMaximum occupancy: $maxOccupancy\nMap coordinates: $mapCoordinates\nTags: $tags\nimages: $images\nQR code: $qrCode\nOwner: $ownerId\nRating: $avgRating\nReviews:\n$reviews\n"

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
}