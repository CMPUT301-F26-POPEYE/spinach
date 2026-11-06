package com.example.spinach

import com.google.firebase.firestore.GeoPoint
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

// This code was produced partly by Claude Sonnet 5.5, 6/11/2026 over the prompt
// "make tests following the before pattern".

/**
 * SearchLogicTest
 * Unit tests for the search, filter and display functions in SearchLogic.kt.
 * The join / leave functions are tested in JoinLeaveLotteryTest.kt.
 * Lives in app/src/test/java/com/example/spinach/.
 *
 * To cover:
 * - US 01.01.01 As an entrant, I want to search for campsites.
 * - US 01.01.02 As an entrant, I want to filter campsites by location, date, and number of guests.
 * - US 01.01.03 As an entrant, I want to see a list of campsites based on my filters.
 * - US 01.01.04 As an entrant, I want to see campsite summary information including name, location, maximum occupancy, and rating.
 * - US 01.01.05 As an entrant, I want to see details of a campsite.
 */
class SearchLogicTest {

    private val day = ReservationQueries.DAY_MILLIS
    private val oldLocale = Locale.getDefault()

    // the rating text uses a decimal point, so fix the language for the tests
    @Before
    fun useEnglish() {
        Locale.setDefault(Locale.US)
    }

    @After
    fun putLocaleBack() {
        Locale.setDefault(oldLocale)
    }

    // builds a campsite, the tests only change what they care about
    private fun campsite(
        id: String = "c1",
        name: String = "Pine Hollow",
        location: String = "Banff",
        maxOccupancy: Int = 4,
        ratings: List<Double> = emptyList(),
        tags: List<String> = emptyList(),
        reviews: List<String> = emptyList(),
        description: String = "A quiet spot by the river",
    ) = Campsite(
        id = id,
        name = name,
        location = location,
        description = description,
        maxOccupancy = maxOccupancy,
        mapCoordinates = GeoPoint(51.5, -115.5),
        tags = tags.toMutableList(),
        ratings = ratings.toMutableList(),
        reviews = reviews.toMutableList(),
    )

    private fun reservation(
        campsiteId: String,
        dates: List<Long>,
        status: ReservationStatus = ReservationStatus.ACTIVE,
    ) = Reservation(
        id = "r-$campsiteId",
        campsiteId = campsiteId,
        dates = dates.toMutableList(),
        status = status,
    )

    // three campsites used by the search and filter tests
    private val pine = campsite(id = "c1", name = "Pine Hollow", location = "Banff", maxOccupancy = 4)
    private val lake = campsite(id = "c2", name = "Lakeside", location = "Jasper", maxOccupancy = 8)
    private val ridge = campsite(id = "c3", name = "Ridge View", location = "Banff", maxOccupancy = 2)
    private val all = listOf(pine, lake, ridge)

    // ----------------------------------------------------- US 01.01.01 search

    @Test
    fun searchMatchesTheName() {
        assertEquals(listOf(lake), searchByQuery(all, "lake"))
    }

    @Test
    fun searchMatchesTheLocation() {
        assertEquals(listOf(pine, ridge), searchByQuery(all, "banff"))
    }

    @Test
    fun searchIgnoresCaseAndSpaces() {
        assertEquals(listOf(pine), searchByQuery(all, "  PINE  "))
    }

    @Test
    fun blankSearchReturnsEverything() {
        assertEquals(all, searchByQuery(all, ""))
        assertEquals(all, searchByQuery(all, "   "))
    }

    @Test
    fun searchWithNoMatchReturnsAnEmptyList() {
        assertTrue(searchByQuery(all, "desert").isEmpty())
    }

    // ----------------------------------------------------- US 01.01.02 / 01.01.03 filter

    @Test
    fun noFiltersKeepsEverything() {
        assertEquals(all, filterByCriteria(all, emptyList()))
    }

    @Test
    fun filterByLocation() {
        assertEquals(listOf(lake), filterByCriteria(all, emptyList(), location = "jasper"))
    }

    @Test
    fun filterByGuestsKeepsCampsitesThatFitEveryone() {
        assertEquals(listOf(pine, lake), filterByCriteria(all, emptyList(), guests = 4))
    }

    @Test
    fun filterByDatesRemovesCampsitesWithAnActiveReservationInTheRange() {
        val reservations = listOf(reservation("c1", listOf(10 * day)))
        assertEquals(listOf(lake, ridge), filterByCriteria(all, reservations, startDate = 10 * day))
    }

    @Test
    fun filterWithOnlyAStartDateChecksThatOneDay() {
        val reservations = listOf(reservation("c1", listOf(11 * day)))
        assertEquals(all, filterByCriteria(all, reservations, startDate = 10 * day))
    }

    @Test
    fun dateRangeIncludesBothTheFirstAndLastDay() {
        val first = listOf(reservation("c1", listOf(10 * day)))
        val last = listOf(reservation("c1", listOf(12 * day)))
        assertEquals(listOf(lake, ridge), filterByCriteria(all, first, startDate = 10 * day, endDate = 12 * day))
        assertEquals(listOf(lake, ridge), filterByCriteria(all, last, startDate = 10 * day, endDate = 12 * day))
    }

    @Test
    fun reservationOutsideTheRangeDoesNotRemoveTheCampsite() {
        val reservations = listOf(reservation("c1", listOf(13 * day)))
        assertEquals(all, filterByCriteria(all, reservations, startDate = 10 * day, endDate = 12 * day))
    }

    @Test
    fun cancelledAndRejectedReservationsDoNotBlockDates() {
        val reservations = listOf(
            reservation("c1", listOf(10 * day), ReservationStatus.CANCELLED),
            reservation("c2", listOf(10 * day), ReservationStatus.REJECTED),
        )
        assertEquals(all, filterByCriteria(all, reservations, startDate = 10 * day))
    }

    @Test
    fun filtersCanBeCombined() {
        val reservations = listOf(reservation("c3", listOf(10 * day)))
        // Banff + 2 guests + day 10 free: Pine Hollow only, Ridge View is booked
        assertEquals(
            listOf(pine),
            filterByCriteria(all, reservations, location = "banff", startDate = 10 * day, guests = 2),
        )
    }

    // ----------------------------------------------------- US 01.01.04 summary

    @Test
    fun campsiteWithoutRatingsIsUnrated() {
        assertEquals("Unrated", ratingLabel(campsite()))
    }

    @Test
    fun ratingIsTheAverageWithOneDecimal() {
        assertEquals("★ 4.0", ratingLabel(campsite(ratings = listOf(5.0, 3.0))))
        assertEquals("★ 3.7", ratingLabel(campsite(ratings = listOf(4.0, 4.0, 3.0))))
    }

    @Test
    fun summaryShowsNameLocationOccupancyAndRating() {
        assertEquals(
            listOf("Pine Hollow", "Banff", "Max occupancy: 4", "Unrated"),
            summaryLines(campsite()),
        )
    }

    // ----------------------------------------------------- US 01.01.05 details

    @Test
    fun detailsShowLocationRatingOccupancyMapAndDescription() {
        val lines = detailLines(campsite(ratings = listOf(5.0)))
        assertTrue("Banff" in lines)
        assertTrue("★ 5.0" in lines)
        assertTrue("Max occupancy: 4" in lines)
        assertTrue("Map: 51.5, -115.5" in lines)
        assertTrue("A quiet spot by the river" in lines)
    }

    @Test
    fun detailsOnlyShowTagsWhenThereAreSome() {
        assertTrue(detailLines(campsite()).none { it.startsWith("Tags:") })
        assertTrue("Tags: lake, quiet" in detailLines(campsite(tags = listOf("lake", "quiet"))))
    }

    @Test
    fun detailsSayWhenThereAreNoReviews() {
        val lines = detailLines(campsite())
        assertTrue("Reviews:" in lines)
        assertTrue("No reviews yet" in lines)
    }

    @Test
    fun detailsListEveryReview() {
        val lines = detailLines(campsite(reviews = listOf("Great view", "Very clean")))
        assertTrue("• Great view" in lines)
        assertTrue("• Very clean" in lines)
        assertTrue("No reviews yet" !in lines)
    }

    // ----------------------------------------------------- US 01.01.06 lottery section

    private fun lottery(
        id: String = "l1",
        campsiteId: String = "c1",
        capacity: Int? = null,
        waitlist: List<String> = emptyList(),
    ) = Lottery(
        id = id,
        campsiteId = campsiteId,
        registrationStart = day,
        registrationEnd = 10 * day,
        bookingStart = 20 * day,
        bookingEnd = 25 * day,
        winnerCount = 3,
        waitlist = waitlist.toMutableList(),
        waitlistCapacity = capacity,
    )

    @Test
    fun lotteryLinesShowRegistrationBookingAndWinners() {
        val lines = lotteryLines(lottery())
        assertTrue("Lottery:" in lines)
        assertTrue("Registration closes: ${ReservationQueries.formatDay(10 * day)}" in lines)
        assertTrue(
            "Booking period: ${ReservationQueries.formatDay(20 * day)} to ${ReservationQueries.formatDay(25 * day)}" in lines,
        )
        assertTrue("Winners: 3" in lines)
    }

    @Test
    fun lotteryLinesShowTheWaitingListCount() {
        assertTrue("Waiting list: 2" in lotteryLines(lottery(waitlist = listOf("a", "b"))))
    }

    @Test
    fun lotteryLinesShowTheCapacityWhenThereIsOne() {
        assertTrue("Waiting list: 2 of 5" in lotteryLines(lottery(capacity = 5, waitlist = listOf("a", "b"))))
    }

    // ----------------------------------------------------- finding the active lottery

    private val now = 5 * day

    @Test
    fun findsTheOpenLotteryOfTheCampsite() {
        val open = lottery()
        assertSame(open, findActiveLottery(listOf(open), "c1", now))
    }

    @Test
    fun noLotteryWhenRegistrationHasClosed() {
        assertNull(findActiveLottery(listOf(lottery()), "c1", 20 * day))
    }

    @Test
    fun noLotteryForADifferentCampsite() {
        assertNull(findActiveLottery(listOf(lottery(campsiteId = "c2")), "c1", now))
    }

    @Test
    fun skipsLotteriesThatDoNotMatchAndPicksTheOpenOne() {
        val other = lottery(id = "l1", campsiteId = "c2")
        val mine = lottery(id = "l2", campsiteId = "c1")
        assertSame(mine, findActiveLottery(listOf(other, mine), "c1", now))
    }

    @Test
    fun findsALotteryThatCoversSeveralCampsites() {
        val multi = Lottery(
            id = "l3",
            campsiteIds = mutableListOf("c1", "c2"),
            registrationStart = day,
            registrationEnd = 10 * day,
        )
        assertSame(multi, findActiveLottery(listOf(multi), "c2", now))
    }

    @Test
    fun noLotteryWhenThereAreNone() {
        assertNull(findActiveLottery(emptyList(), "c1", now))
    }
}
