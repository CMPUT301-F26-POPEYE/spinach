package com.example.spinach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

//this code was partly produced by claude with the promt, "how would i go about adding these" nov 24 2026

/**
 * Tests the [User] side of the profile image story (add, update, remove).
 * [ProfileImage.encode] and [ProfileImage.decode] use Android's Bitmap and Base64,
 * so they are checked by hand on a device instead.
 */
class ProfileImageTest {

    @Test
    fun newAccountHasNoProfileImage() {
        assertNull(User.newAccount("u1", "sam", User.Role.ENTRANT).profileImage)
    }

    @Test
    fun profileImageCanBeAddedAndReplaced() {
        val user = User(id = "u1", name = "Sam")
        user.profileImage = "first"
        assertEquals("first", user.profileImage)
        user.profileImage = "second"
        assertEquals("second", user.profileImage)
    }

    @Test
    fun profileImageCanBeRemoved() {
        val user = User(id = "u1", name = "Sam", profileImage = "abc")
        user.profileImage = null
        assertNull(user.profileImage)
    }

    // US 01.08.01: deleting the profile also clears the image
    @Test
    fun clearProfileRemovesImage() {
        val user = User(id = "u1", name = "Sam", profileImage = "abc")
        user.clearProfile()
        assertNull(user.profileImage)
    }
}
