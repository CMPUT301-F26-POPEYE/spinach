package com.example.spinach

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

// This code was produced partly by Claude Opus 5.5, 6/11/2026 over the prompt "I want JUnit tests for the User related classes that were just implemented."

// Runs on a device against the real Firebase project, so Email/Password sign-in must be
// on. Each test makes its own throwaway account and removes it afterwards. Running this
// logs out whoever is logged in to the app on that device.
@RunWith(AndroidJUnit4::class)
class AccountFirebaseTest {
    private val accounts = AccountRepository()
    private val users = UserRepository()
    private val username = "test_" + UUID.randomUUID().toString().replace("-", "").take(12)
    private val password = "test-password"
    private lateinit var userId: String

    private fun <T> await(task: Task<T>): T = Tasks.await(task, TIMEOUT_SECONDS, TimeUnit.SECONDS)

    private fun savedUser(role: User.Role = User.Role.ENTRANT): User {
        val user = User.newAccount(userId, username, role)
        user.updateProfile("Test User", "test@example.com", "5551234")
        await(users.save(user))
        return user
    }

    @Before
    fun createAccount() {
        await(accounts.createAccount(username, password))
        userId = checkNotNull(accounts.currentUserId)
    }

    @After
    fun removeAccount() {
        if (accounts.currentUserId != null) {
            runCatching { await(users.delete(userId)) }
            runCatching { await(accounts.deleteAccount()) }
        }
        accounts.logOut()
    }

    @Test
    fun creatingAnAccount_storesItInFirebase() {
        savedUser(User.Role.OWNER)

        val stored = await(users.get(userId))

        assertNotNull(stored)
        assertTrue(await(users.exists(userId)))
        assertEquals(username, stored?.username)
        assertEquals("Test User", stored?.name)
        assertEquals("test@example.com", stored?.email)
        assertEquals("5551234", stored?.phoneNumber)
        assertEquals(User.Role.OWNER, stored?.activeRole)
        assertEquals(listOf(User.Role.ENTRANT, User.Role.OWNER), stored?.roles)
    }

    @Test
    fun creatingAnAccount_withATakenUsernameIsRejected() {
        val failure = assertThrows(ExecutionException::class.java) {
            await(accounts.createAccount(username, "another-password"))
        }

        assertTrue(failure.cause is FirebaseAuthUserCollisionException)
    }

    @Test
    fun loggingIn_needsTheRightPassword() {
        accounts.logOut()

        assertThrows(ExecutionException::class.java) { await(accounts.logIn(username, "wrong-password")) }
        assertNull(accounts.currentUserId)

        await(accounts.logIn(username, password))
        assertEquals(userId, accounts.currentUserId)
    }

    @Test
    fun updatingDetails_changesThemInFirebase() {
        val user = savedUser()

        assertTrue(user.updateProfile("New Name", "new@example.com", ""))
        await(users.save(user))

        val stored = await(users.get(userId))
        assertEquals("New Name", stored?.name)
        assertEquals("new@example.com", stored?.email)
        assertNull(stored?.phoneNumber)
    }

    @Test
    fun switchingRoles_isStoredInFirebase() {
        val user = savedUser(User.Role.ADMIN)

        assertTrue(user.switchRole(User.Role.OWNER))
        await(users.save(user))

        assertEquals(User.Role.OWNER, await(users.get(userId))?.activeRole)
    }

    @Test
    fun registeredDevice_isTheOnlyOneThatCanResumeTheSession() {
        val user = savedUser()
        user.trustDevice("device-a")
        await(users.save(user))

        val stored = checkNotNull(await(users.get(userId)))

        assertTrue(AccountRules.canResumeSession(stored, "device-a", loggedInWithPassword = false))
        assertFalse(AccountRules.canResumeSession(stored, "device-b", loggedInWithPassword = false))
    }

    @Test
    fun deletingAnAccount_removesItAndWhatItOwns() {
        savedUser(User.Role.OWNER)
        val campsites = FirebaseFirestore.getInstance().collection("campsites")
        val campsite = await(campsites.add(Campsite(name = "Delete test campsite", ownerId = userId)))

        await(users.delete(userId))

        assertFalse(await(users.exists(userId)))
        assertNull(await(users.get(userId)))
        assertFalse(await(campsite.get()).exists())

        await(accounts.deleteAccount())

        assertNull(accounts.currentUserId)
        assertThrows(ExecutionException::class.java) { await(accounts.logIn(username, password)) }
    }

    companion object {
        private const val TIMEOUT_SECONDS = 30L
    }
}
