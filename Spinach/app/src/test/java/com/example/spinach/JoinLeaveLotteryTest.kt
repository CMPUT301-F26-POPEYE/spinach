package com.example.spinach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// This code was produced partly by Claude Sonnet 5.5, 6/11/2026 over the prompt
// "make these search files logically correct".

/**
 * JoinLeaveLotteryTest
 * Unit tests for joinProblem, leaveProblem, canJoin and joinButtonLabel in SearchLogic.kt.
 * Lives in app/src/test/java/com/example/spinach/.
 *
 * To cover:
 * - US 01.01.06 As an entrant, I want to join an active lottery from the campsite details screen.
 * - US 01.02.01 As an entrant, I want to join the waiting list for a campsite lottery.
 */
class JoinLeaveLotteryTest {

    private val day = ReservationQueries.DAY_MILLIS
    // fixed time inside the registration period so the tests do not depend on the real date
    private val now = 5 * day

    // a lottery that is open for registration from day 1 to day 10
    private fun lottery(
        waitlist: List<String> = emptyList(),
        capacity: Int? = null,
        drawn: Boolean = false,
    ) = Lottery(
        id = "l1",
        campsiteId = "c1",
        registrationStart = day,
        registrationEnd = 10 * day,
        waitlist = waitlist.toMutableList(),
        waitlistCapacity = capacity,
        drawn = drawn,
    )

    // ---------------------------------------------- US 01.02.01 join the waitlist

    // null means "allowed"
    @Test
    fun canJoinAnOpenLottery() {
        assertNull(joinProblem(lottery(), "e1", now))
    }

    @Test
    fun cannotJoinALotteryThatNoLongerExists() {
        assertEquals("This lottery no longer exists", joinProblem(null, "e1", now))
    }

    @Test
    fun cannotJoinAfterRegistrationCloses() {
        assertEquals(
            "Registration for this lottery has closed",
            joinProblem(lottery(), "e1", 20 * day),
        )
    }

    @Test
    fun cannotJoinTwice() {
        assertEquals(
            "You are already on this waiting list",
            joinProblem(lottery(waitlist = listOf("e1")), "e1", now),
        )
    }

    @Test
    fun cannotJoinAFullWaitlist() {
        val full = lottery(waitlist = listOf("a", "b"), capacity = 2)
        assertEquals("The waiting list for this lottery is full", joinProblem(full, "e1", now))
    }

    // ------------------------------------------------ US 01.01.06 join / leave button

    @Test
    fun joinButtonTextFollowsTheState() {
        assertEquals("Checking lottery…", joinButtonLabel(false, false, null))
        assertEquals("No active lottery", joinButtonLabel(true, false, null))
        assertEquals("Join lottery", joinButtonLabel(true, false, lottery()))
        assertEquals("Joined ✓", joinButtonLabel(true, true, lottery()))
        assertEquals("Waiting list full", joinButtonLabel(true, false, lottery(listOf("a"), capacity = 1)))
    }

    @Test
    fun canOnlyTapJoinWhenNotJoinedAndThereIsRoom() {
        assertTrue(canJoin(false, lottery()))
        assertFalse(canJoin(true, lottery()))
        assertFalse(canJoin(false, null))
        assertFalse(canJoin(false, lottery(listOf("a"), capacity = 1)))
    }

    // ---------------------------------------------- leave the waitlist

    @Test
    fun canLeaveWhenOnTheWaitlist() {
        assertNull(leaveProblem(lottery(waitlist = listOf("e1")), "e1"))
    }

    @Test
    fun cannotLeaveWhenNotOnTheWaitlist() {
        assertEquals("You are not on this waiting list", leaveProblem(lottery(), "e1"))
    }

    @Test
    fun cannotLeaveAfterTheDraw() {
        assertEquals("The draw already happened", leaveProblem(lottery(drawn = true), "e1"))
    }

    @Test
    fun cannotLeaveALotteryThatNoLongerExists() {
        assertEquals("This lottery no longer exists", leaveProblem(null, "e1"))
    }
}
