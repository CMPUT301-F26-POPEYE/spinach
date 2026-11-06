package com.example.spinach
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date

//US 01.01.01: As an entrant, I want to search for campsites.
//To do: Take in query -> Match to Name | Location -> Return Campsite Summaries
//If Match = Null, then return 'Sorry, There doesn't seem to be anything matching your search

//will fix into KDocs later
//will do AI citations later

suspend fun searchCampsites(query: String): List<Campsite> {
    val campsites = FirebaseFirestore.getInstance()
        .collection("campsites")
        .get()
        .await()
        .toObjects(Campsite::class.java) //converts firebase object to campsite

    val EmptyResultsMessage:String = "Sorry, There doesn't seem to be anything matching your search."

    val q = query.trim() //removes leading and ending whitespace
    if (q.isEmpty()) return campsites

    val filteredResults = campsites.filter { c ->
                c.name.contains(q, ignoreCase = true) ||
                c.location.contains(q, ignoreCase = true)
    }

    //function only returns list<Campsite> so to make sure Match = Null condition is still true we
    //throw an exception
    if (filteredResults.isEmpty()){
        throw Exception(EmptyResultsMessage)
    }

    return filteredResults
}


suspend fun filterCampsites(
    campsites: List<Campsite>,
    location: String = "",
    date: Date? = null,
    guests: Int? = null,
): List<Campsite> {
    val bookedIds: Set<String> =
        if (date == null) {
            emptySet()
        } else {
            // Reservation.dates are UTC midnight, so round the Date down to the start of its day
            //CITE AI FOR THIS PORTION
            val day = date.time / ReservationQueries.DAY_MILLIS * ReservationQueries.DAY_MILLIS
            FirebaseFirestore.getInstance().collection("reservations")
                .whereArrayContains("dates", day)
                .get().await()
                .toObjects(Reservation::class.java)
                .filter { it.status == ReservationStatus.ACTIVE }
                .map { it.campsiteId }
                .toSet()
        }

    return campsites.filter { c ->
        (location.isBlank() || c.location.contains(location.trim(), ignoreCase = true)) &&
                (guests == null || c.maxOccupancy >= guests) &&
                c.id !in bookedIds
    }
}