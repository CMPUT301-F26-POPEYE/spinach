package com.example.spinach
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

//US 01.01.01: As an entrant, I want to search for campsites.
//To do: Take in query -> Match to Name | Location -> Return Campsite Summaries
//If Match = Null, then return 'Sorry, There doesn't seem to be anything matching your search

//will do AI citations later


/**
 * Loads the campsites from Firestore and searches them by name or location (US 01.01.01).
 * The matching itself is searchByQuery in SearchLogic.kt so it can be unit tested without Firebase.
 *
 * @param query The text to look for in the name or location. A blank query returns every campsite.
 * @return The campsites that match the query.
 * @throws Exception with the "nothing matches" message when a non-blank query matches no campsite,
 * because the function only returns a list.
 */
suspend fun searchCampsites(query: String): List<Campsite> {
    val campsites = FirebaseFirestore.getInstance()
        .collection("campsites")
        .get()
        .await()
        .toObjects(Campsite::class.java) //converts firebase object to campsite

    val EmptyResultsMessage:String = "Sorry, There doesn't seem to be anything matching your search."

    val filteredResults = searchByQuery(campsites, query)

    //function only returns list<Campsite> so to make sure Match = Null condition is still true we
    //throw an exception
    if (query.isNotBlank() && filteredResults.isEmpty()){
        throw Exception(EmptyResultsMessage)
    }

    return filteredResults
}

/**
 * Filters campsites by location, dates and number of guests (US 01.01.02).
 * See filterByCriteria in SearchLogic.kt for the rules.
 * Reservations are only read from Firestore when a date filter is set.
 *
 * @param campsites The campsites to filter.
 * @param location Text the campsite's location must contain. Blank means no location filter.
 * @param startDate First day of the range as UTC midnight millis, or `null` for no date filter.
 * @param endDate Last day of the range (included), or `null` to use only [startDate].
 * @param guests Number of guests the campsite must hold, or `null` for no guest filter.
 * @return The campsites that match every filter that is set.
 */
suspend fun filterCampsites(
    campsites: List<Campsite>,
    location: String = "",
    startDate: Long? = null,
    endDate: Long? = null,
    guests: Int? = null,
): List<Campsite> {
    //CITE AI FOR THIS PORTION
    val reservations: List<Reservation> =
        if (startDate == null) {
            emptyList()
        } else {
            FirebaseFirestore.getInstance().collection("reservations")
                .whereEqualTo("status", ReservationStatus.ACTIVE.name)
                .get().await()
                .toObjects(Reservation::class.java)
        }
    return filterByCriteria(campsites, reservations, location, startDate, endDate, guests)
}