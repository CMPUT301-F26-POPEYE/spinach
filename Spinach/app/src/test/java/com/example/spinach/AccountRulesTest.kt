package com.example.spinach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRulesTest {

    @Test
    fun validCredentialsAreAccepted() {
        assertNull(AccountRules.credentialsError("alex_camper", "secret1", "secret1"))
    }

    @Test
    fun badUsernamesAreRejected() {
        for (username in listOf("", "ab", "has space", "a@b", "a".repeat(21))) {
            assertEquals(
                "Usernames are 3 to 20 letters, numbers or underscores",
                AccountRules.credentialsError(username, "secret1", "secret1"),
            )
        }
    }

    @Test
    fun shortPasswordIsRejected() {
        assertEquals(
            "Passwords need at least 6 characters",
            AccountRules.credentialsError("alex", "12345", "12345"),
        )
    }

    @Test
    fun mismatchedPasswordsAreRejected() {
        assertEquals(
            "Passwords don't match",
            AccountRules.credentialsError("alex", "secret1", "secret2"),
        )
    }

    @Test
    fun usernamesIgnoreCaseAndSpacesWhenLoggingIn() {
        assertEquals(AccountRules.authEmail("alex"), AccountRules.authEmail("  ALEX "))
    }

    @Test
    fun usernameRoundTripsThroughAuthEmail() {
        assertEquals("alex", AccountRules.usernameFromAuthEmail(AccountRules.authEmail("Alex")))
    }

    @Test
    fun contactError_requiresNameAndValidEmail() {
        assertEquals("Enter your name", AccountRules.contactError(" ", "a@b.co"))
        assertEquals("Enter your email", AccountRules.contactError("Alex", " "))
        assertEquals("Enter a valid email", AccountRules.contactError("Alex", "not-an-email"))
        assertNull(AccountRules.contactError("Alex", "a@b.co"))
    }

    @Test
    fun bookerIsOnlyAnEntrant() {
        val user = User.newAccount("id", "alex", hosting = false)

        assertEquals(listOf(User.Role.ENTRANT), user.roles)
        assertEquals(User.Role.ENTRANT, user.activeRole)
    }

    @Test
    fun hostIsAnOwnerAndAnEntrant() {
        val user = User.newAccount("id", "alex", hosting = true)

        assertTrue(user.hasRole(User.Role.ENTRANT))
        assertTrue(user.hasRole(User.Role.OWNER))
        assertEquals(User.Role.OWNER, user.activeRole)
    }

    @Test
    fun newAccountsReceiveNotifications() {
        assertTrue(User.newAccount("id", "alex", hosting = false).notificationsEnabled)
    }

    @Test
    fun trustingADeviceTwiceAddsItOnce() {
        val user = User()

        user.trustDevice("device")
        user.trustDevice("device")

        assertEquals(listOf("device"), user.trustedDeviceIds)
        assertTrue(user.trustsDevice("device"))
    }

    @Test
    fun untrustedAndBlankDevicesAreNotTrusted() {
        val user = User()
        user.trustDevice("")
        user.trustDevice("device")
        user.untrustDevice("device")

        assertFalse(user.trustsDevice("device"))
        assertFalse(user.trustsDevice(""))
    }
}
