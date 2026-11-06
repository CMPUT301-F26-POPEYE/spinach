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
        val user = User.newAccount("id", "alex", User.Role.ENTRANT)

        assertEquals(listOf(User.Role.ENTRANT), user.roles)
        assertEquals(User.Role.ENTRANT, user.activeRole)
    }

    @Test
    fun hostIsAnOwnerAndAnEntrant() {
        val user = User.newAccount("id", "alex", User.Role.OWNER)

        assertTrue(user.hasRole(User.Role.ENTRANT))
        assertTrue(user.hasRole(User.Role.OWNER))
        assertEquals(User.Role.OWNER, user.activeRole)
    }

    @Test
    fun newAccountsStartWithNotificationsOff() {
        assertFalse(User.newAccount("id", "alex", User.Role.ENTRANT).notificationsEnabled)
        assertFalse(User.newAccount("id", "alex", User.Role.OWNER).notificationsEnabled)
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

    @Test
    fun missingPasswordIsRejected() {
        assertEquals(
            "Passwords need at least 6 characters",
            AccountRules.credentialsError("alex", "", ""),
        )
    }

    @Test
    fun malformedEmailsAreRejected() {
        val malformed = listOf(
            "alex", "alex@", "@example.com", "alex@example", "alex@example.c",
            "alex example@test.com", "alex@exa mple.com", "alex@@example.com",
        )
        for (email in malformed) {
            assertFalse(email, User.isValidEmail(email))
            assertEquals("Enter a valid email", AccountRules.contactError("Alex", email))
        }
    }

    @Test
    fun wellFormedEmailsAreAccepted() {
        for (email in listOf("alex@example.com", "a.b+c_d@sub.example.ca", " alex@example.com ")) {
            assertTrue(email, User.isValidEmail(email))
        }
    }

    @Test
    fun adminPassword_onlyTheExactPasswordIsAccepted() {
        assertNull(AccountRules.adminPasswordError("adminpass"))
        for (password in listOf("", "wrong", "ADMINPASS", " adminpass")) {
            assertEquals("Incorrect admin password", AccountRules.adminPasswordError(password))
        }
    }

    @Test
    fun adminCanAlsoBeAnOwnerAndAnEntrant() {
        val user = User.newAccount("id", "alex", User.Role.ADMIN)

        assertTrue(user.hasRole(User.Role.ADMIN))
        assertTrue(user.hasRole(User.Role.OWNER))
        assertTrue(user.hasRole(User.Role.ENTRANT))
        assertEquals(User.Role.ADMIN, user.activeRole)
    }

    @Test
    fun rolesTheUserHoldsCanBeSwitchedTo() {
        val user = User.newAccount("id", "alex", User.Role.ADMIN)

        for (role in listOf(User.Role.OWNER, User.Role.ENTRANT, User.Role.ADMIN)) {
            assertTrue(user.switchRole(role))
            assertEquals(role, user.activeRole)
        }
    }

    @Test
    fun rolesTheUserDoesNotHoldCannotBeSwitchedTo() {
        val user = User.newAccount("id", "alex", User.Role.ENTRANT)

        assertFalse(user.switchRole(User.Role.OWNER))
        assertFalse(user.switchRole(User.Role.ADMIN))
        assertEquals(User.Role.ENTRANT, user.activeRole)
    }

    @Test
    fun updateProfile_changesTheDetails() {
        val user = User(name = "Alex", email = "old@example.com", phoneNumber = "111")

        assertTrue(user.updateProfile(" Sam ", "new@example.com", ""))

        assertEquals("Sam", user.name)
        assertEquals("new@example.com", user.email)
        assertNull(user.phoneNumber)
    }

    @Test
    fun updateProfile_changesNothingWhenInvalid() {
        val user = User(name = "Alex", email = "old@example.com")

        assertFalse(user.updateProfile("Sam", "not-an-email", "111"))
        assertFalse(user.updateProfile(" ", "new@example.com", "111"))

        assertEquals("Alex", user.name)
        assertEquals("old@example.com", user.email)
        assertNull(user.phoneNumber)
    }

    @Test
    fun onlyTheRegisteredDeviceIsTrusted() {
        val user = User()
        user.trustDevice("device-a")

        assertTrue(user.trustsDevice("device-a"))
        assertFalse(user.trustsDevice("device-b"))
    }

    @Test
    fun savedSession_resumesOnlyOnARegisteredDevice() {
        val user = User()
        user.trustDevice("device-a")

        assertTrue(AccountRules.canResumeSession(user, "device-a", loggedInWithPassword = false))
        assertFalse(AccountRules.canResumeSession(user, "device-b", loggedInWithPassword = false))
        assertFalse(AccountRules.canResumeSession(user, "", loggedInWithPassword = false))
    }

    @Test
    fun savedSession_resumesOnAnyDeviceAfterThePasswordWasEntered() {
        assertTrue(AccountRules.canResumeSession(User(), "device-b", loggedInWithPassword = true))
    }
}
