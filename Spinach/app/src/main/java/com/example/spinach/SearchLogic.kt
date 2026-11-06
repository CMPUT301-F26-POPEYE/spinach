package com.example.spinach


/**
 * Searches campsites by name or location (US 01.01.01).
 * The match ignores case, and a blank query returns every campsite.
 *
 * @param campsites The campsites to search.
 * @param query The text to look for in the name or location.
 * @return The campsites whose name or location contains the query.
 */
fun searchByQuery(campsites: List<Campsite>, query: String): List<Campsite> {
    val q = query.trim()
    if (q.isEmpty()) return campsites
    return campsites.filter { c ->
        c.name.contains(q, ignoreCase = true) ||
                c.location.contains(q, ignoreCase = true)
    }
}

/**
 * Filters campsites by location, dates and number of guests (US 01.01.02).
 * The filters can be used alone or combined, and a filter left out (blank or null) is not applied.
 * A campsite is kept only if its maximum occupancy is at least [guests] and no ACTIVE reservation
 * has a day inside the date range.
 *
 * @param campsites The campsites to filter.
 * @param reservations The reservations to check the dates against.
 * @param location Text the campsite's location must contain. Blank means no location filter.
 * @param startDate First day of the range as UTC midnight millis, or `null` for no date filter.
 * @param endDate Last day of the range (included), or `null` to use only [startDate].
 * @param guests Number of guests the campsite must hold, or `null` for no guest filter.
 * @return The campsites that match every filter that is set.
 */
fun filterByCriteria(
    campsites: List<Campsite>,
    reservations: List<Reservation>,
    location: String = "",
    startDate: Long? = null,
    endDate: Long? = null,
    guests: Int? = null,
): List<Campsite> {
    val bookedIds: Set<String> =
        if (startDate == null) {
            emptySet()
        } else {
            val first = startDate / ReservationQueries.DAY_MILLIS * ReservationQueries.DAY_MILLIS
            val last = (endDate ?: startDate) / ReservationQueries.DAY_MILLIS * ReservationQueries.DAY_MILLIS
            reservations
                .filter { r -> r.status == ReservationStatus.ACTIVE && r.dates.any { it in first..last } }
                .map { it.campsiteId }
                .toSet()
        }

    return campsites.filter { c ->
        (location.isBlank() || c.location.contains(location.trim(), ignoreCase = true)) &&
                (guests == null || c.maxOccupancy >= guests) &&
                c.id !in bookedIds
    }
}

/**
 * Text for a campsite's rating (US 01.01.04).
 * The rating is the average of the campsite's ratings, or "Unrated" when it has none.
 *
 * @param campsite The campsite to describe.
 * @return For example "★ 4.5", or "Unrated".
 */
fun ratingLabel(campsite: Campsite): String =
    if (campsite.ratings.isEmpty()) "Unrated" else "★ %.1f".format(campsite.avgRating)

/**
 * The lines shown in each list item (US 01.01.04): name, location, maximum occupancy and rating.
 *
 * @param campsite The campsite to summarize.
 * @return The four lines, in the order they are shown.
 */
fun summaryLines(campsite: Campsite): List<String> = listOf(
    campsite.name,
    campsite.location,
    "Max occupancy: ${campsite.maxOccupancy}",
    ratingLabel(campsite),
)

/**
 * The lines shown in the details popup (US 01.01.05). The name is the popup's title, so it is not included.
 *
 * @param campsite The campsite to describe.
 * @return Location, rating, occupancy, tags (if any), map position, description and reviews.
 */
fun detailLines(campsite: Campsite): List<String> = buildList {
    add(campsite.location)
    add(ratingLabel(campsite))
    add("Max occupancy: ${campsite.maxOccupancy}")
    if (campsite.tags.isNotEmpty()) add("Tags: ${campsite.tags.joinToString(", ")}")
    add("Map: ${campsite.mapCoordinates.latitude}, ${campsite.mapCoordinates.longitude}")
    add("")
    add(campsite.description)
    add("")
    add("Reviews:")
    if (campsite.reviews.isEmpty()) add("No reviews yet") else campsite.reviews.forEach { add("• $it") }
}

/**
 * The lottery section of the details popup (US 01.01.06, US 01.02.01).
 *
 * @param lottery The active lottery of the campsite.
 * @return When registration closes, the booking period, the number of winners and the waiting list size.
 */
fun lotteryLines(lottery: Lottery): List<String> = listOf(
    "",
    "Lottery:",
    "Registration closes: ${ReservationQueries.formatDay(lottery.registrationEnd)}",
    "Booking period: ${ReservationQueries.formatDay(lottery.bookingStart)} to ${ReservationQueries.formatDay(lottery.bookingEnd)}",
    "Winners: ${lottery.winnerCount}",
    "Waiting list: ${lottery.waitlist.size}" + (lottery.waitlistCapacity?.let { " of $it" } ?: ""),
)

/**
 * Finds the lottery that is accepting entrants for a campsite.
 * Lottery.campsites() covers lotteries that only set campsiteId as well as multi-campsite ones (US 02.04.02).
 *
 * @param lotteries The lotteries to look through.
 * @param campsiteId The id of the campsite.
 * @param now The current time in epoch millis.
 * @return The first lottery of this campsite whose registration is open, or `null` if there is none.
 */
fun findActiveLottery(lotteries: List<Lottery>, campsiteId: String, now: Long): Lottery? =
    lotteries.firstOrNull { campsiteId in it.campsites() && it.isRegistrationOpen(now) }

/**
 * Checks if an entrant can join a lottery's waiting list (US 01.01.06, US 01.02.01).
 *
 * @param lottery The lottery the entrant wants to join, or `null` if it no longer exists.
 * @param entrantId The id of the entrant.
 * @param now The current time in epoch millis.
 * @return `null` if the entrant can join, otherwise the reason they cannot (closed, already joined or full).
 */
fun joinProblem(lottery: Lottery?, entrantId: String, now: Long): String? = when {
    lottery == null -> "This lottery no longer exists"
    !lottery.isRegistrationOpen(now) -> "Registration for this lottery has closed"
    entrantId in lottery.waitlist -> "You are already on this waiting list"
    !lottery.hasWaitlistRoom() -> "The waiting list for this lottery is full"
    else -> null
}

/**
 * Checks if an entrant can leave a lottery's waiting list (US 01.01.06, US 01.02.02).
 * After the draw the waitlist is empty and the entrant is a winner or a loser, so they cannot leave.
 *
 * @param lottery The lottery the entrant wants to leave, or `null` if it no longer exists.
 * @param entrantId The id of the entrant.
 * @return `null` if the entrant can leave, otherwise the reason they cannot.
 */
fun leaveProblem(lottery: Lottery?, entrantId: String): String? = when {
    lottery == null -> "This lottery no longer exists"
    lottery.drawn -> "The draw already happened"
    entrantId !in lottery.waitlist -> "You are not on this waiting list"
    else -> null
}

/**
 * Whether the join button can be tapped.
 *
 * @param joined `true` if the entrant is already on the waitlist.
 * @param lottery The active lottery, or `null` if there is none.
 * @return `true` if there is a lottery, the entrant has not joined yet and the waitlist has room.
 */
fun canJoin(joined: Boolean, lottery: Lottery?): Boolean =
    lottery != null && !joined && lottery.hasWaitlistRoom()

/**
 * The join button's text.
 *
 * @param checked `true` once the popup has finished looking for an active lottery.
 * @param joined `true` if the entrant is already on the waitlist.
 * @param lottery The active lottery, or `null` if there is none.
 * @return For example "Join lottery", "Joined ✓" or "Waiting list full".
 */
fun joinButtonLabel(checked: Boolean, joined: Boolean, lottery: Lottery?): String = when {
    joined -> "Joined ✓"
    !checked -> "Checking lottery…"
    lottery == null -> "No active lottery"
    !lottery.hasWaitlistRoom() -> "Waiting list full"
    else -> "Join lottery"
}