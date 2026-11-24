package com.example.spinach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

//this code was partly produced by claude with the promt, "how would i go about adding these" nov 24 2026

/**
 * Tests the shared lottery story: several campsite owners listing campsites in one
 * lottery for the same period ([Lottery.addCampsiteError] and [Lottery.owners]).
 */
class SharedLotteryTest {

    private fun lottery(open: Boolean = true, drawn: Boolean = false) = Lottery(
        id = "L1",
        campsiteId = "c1",
        campsiteIds = mutableListOf("c1"),
        ownerId = "owner1",
        openToOtherOwners = open,
        drawn = drawn,
    )

    @Test
    fun otherOwnerCanAddOwnCampsiteToOpenLottery() {
        assertNull(lottery().addCampsiteError("owner2", "c2", "owner2"))
    }

    @Test
    fun cannotAddToLotteryThatIsNotOpen() {
        assertEquals(
            "This lottery is not open to other owners",
            lottery(open = false).addCampsiteError("owner2", "c2", "owner2"),
        )
    }

    @Test
    fun creatorCanAddEvenIfNotOpen() {
        assertNull(lottery(open = false).addCampsiteError("owner1", "c3", "owner1"))
    }

    @Test
    fun cannotAddSomeoneElsesCampsite() {
        assertEquals(
            "You can only add your own campsites",
            lottery().addCampsiteError("owner2", "c9", "owner3"),
        )
    }

    @Test
    fun cannotAddMissingCampsite() {
        assertNotNull(lottery().addCampsiteError("owner2", "gone", null))
    }

    @Test
    fun cannotAddAfterDraw() {
        assertEquals(
            "This lottery has already been drawn",
            lottery(drawn = true).addCampsiteError("owner2", "c2", "owner2"),
        )
    }

    @Test
    fun cannotAddCampsiteTwice() {
        assertEquals(
            "That campsite is already in this lottery",
            lottery().addCampsiteError("owner1", "c1", "owner1"),
        )
    }

    @Test
    fun ownersListsCreatorFirstWithoutDuplicates() {
        val shared = lottery()
        shared.ownerIds = mutableListOf("owner2", "owner1", "owner2")
        assertEquals(listOf("owner1", "owner2"), shared.owners())
    }

    @Test
    fun newLotteryIsNotSharedByDefault() {
        val plain = Lottery()
        assertEquals(false, plain.openToOtherOwners)
        assertEquals(emptyList<String>(), plain.ownerIds)
    }
}
