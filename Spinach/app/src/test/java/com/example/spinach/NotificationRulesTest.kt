package com.example.spinach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationRulesTest {

    private fun lottery(id: String, drawn: Boolean, vararg entrants: String) =
        Lottery(id = id, drawn = drawn, waitlist = entrants.toMutableList())

    @Test
    fun blankMessageIsRejected() {
        assertEquals("Write a message first", NotificationRules.messageError("   "))
    }

    @Test
    fun realMessageIsAccepted() {
        assertNull(NotificationRules.messageError("The draw is tomorrow"))
    }

    @Test
    fun waitlistedPairs_skipsDrawnLotteries() {
        val open = lottery("l1", false, "a", "b")
        val done = lottery("l2", true, "c")
        assertEquals(listOf("l1" to "a", "l1" to "b"), NotificationRules.waitlistedPairs(listOf(open, done)))
    }

    @Test
    fun waitlistedPairs_removesDuplicateEntrants() {
        val l = lottery("l1", false, "a", "a", "b")
        assertEquals(listOf("l1" to "a", "l1" to "b"), NotificationRules.waitlistedPairs(listOf(l)))
    }

    @Test
    fun waitlistedPairs_sameEntrantOnTwoLotteriesGetsTwo() {
        val first = lottery("l1", false, "a")
        val second = lottery("l2", false, "a")
        assertEquals(
            listOf("l1" to "a", "l2" to "a"),
            NotificationRules.waitlistedPairs(listOf(first, second)),
        )
    }

    @Test
    fun recipients_dropsOptedOutEntrants() {
        val pairs = listOf("l1" to "a", "l1" to "b", "l1" to "c")
        assertEquals(listOf("l1" to "a", "l1" to "c"), NotificationRules.recipients(pairs, setOf("b")))
    }

    @Test
    fun recipients_emptyWhenEveryoneOptedOut() {
        val pairs = listOf("l1" to "a")
        assertEquals(emptyList<Pair<String, String>>(), NotificationRules.recipients(pairs, setOf("a")))
    }
}