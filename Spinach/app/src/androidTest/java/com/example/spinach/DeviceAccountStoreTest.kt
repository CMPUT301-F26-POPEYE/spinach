package com.example.spinach

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// This code was produced partly by Claude Opus 5.5, 6/11/2026 over the prompt "I want JUnit tests for the User related classes that were just implemented."
// Only touches this device's own storage, so it needs no network or Firebase.
@RunWith(AndroidJUnit4::class)
class DeviceAccountStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = DeviceAccountStore(context)
    private val first = "store_test_a"
    private val second = "store_test_b"

    private fun testAccounts() = store.accounts().filter { it.username in listOf(first, second) }

    @After
    fun removeTestAccounts() {
        store.remove(first)
        store.remove(second)
    }

    @Test
    fun savedPasswordCanBeReadBack() {
        assertTrue(store.save(first, "Alex", "secret-one"))

        assertEquals("secret-one", store.password(first))
    }

    @Test
    fun passwordIsNotStoredInPlainText() {
        store.save(first, "Alex", "secret-one")

        val stored = context.getSharedPreferences("device_accounts", 0).getString(first, null)

        assertTrue(stored.orEmpty().isNotEmpty())
        assertFalse(stored.orEmpty().contains("secret-one"))
    }

    @Test
    fun severalAccountsCanBeRegisteredOnOneDevice() {
        store.save(second, "Sam", "secret-two")
        store.save(first, "Alex", "secret-one")

        assertEquals(
            listOf(DeviceAccount(first, "Alex"), DeviceAccount(second, "Sam")),
            testAccounts(),
        )
        assertEquals("secret-one", store.password(first))
        assertEquals("secret-two", store.password(second))
    }

    @Test
    fun removingAnAccountLeavesTheOthers() {
        store.save(first, "Alex", "secret-one")
        store.save(second, "Sam", "secret-two")

        store.remove(first)

        assertFalse(store.contains(first))
        assertNull(store.password(first))
        assertEquals(listOf(DeviceAccount(second, "Sam")), testAccounts())
    }

    @Test
    fun unregisteredAccountHasNoPassword() {
        assertFalse(store.contains(first))
        assertNull(store.password(first))
    }
}
