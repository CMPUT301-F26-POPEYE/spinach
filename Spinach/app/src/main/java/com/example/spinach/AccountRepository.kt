package com.example.spinach

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException

// This code was produced partly by Claude Opus 5.5, 6/11/2026 over the prompt "Here is how I want the login process to go: username/password accounts with an optional device login."

/**
 * Handles usernames and passwords through Firebase Authentication, which stores the
 * password. Passwords never go in Firestore. The profile itself is in [UserRepository].
 *
 * Firebase keeps a session on the device between launches. That session is the real
 * proof of identity: device login only decides whether a saved session may be reused,
 * because a device ID on its own is not a secret. See [loggedInWithPassword].
 */
class AccountRepository(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {

    /** The logged in account's ID, or `null` if nobody is logged in. Same as [User.id]. */
    val currentUserId: String?
        get() = auth.currentUser?.takeUnless { it.isAnonymous }?.uid

    /** The logged in account's username. */
    val currentUsername: String
        get() = AccountRules.usernameFromAuthEmail(auth.currentUser?.email)

    /**
     * Whether the password was entered since the app process started. Lets a session
     * survive a screen rotation without device login, while closing the app ends it.
     */
    val loggedInWithPassword: Boolean
        get() = passwordEntered

    /** Creates the account and logs in. Fails if the username is taken. */
    fun createAccount(username: String, password: String): Task<AuthResult> =
        auth.createUserWithEmailAndPassword(AccountRules.authEmail(username), password)
            .addOnSuccessListener { passwordEntered = true }

    /** Logs in with a username and password. */
    fun logIn(username: String, password: String): Task<AuthResult> =
        auth.signInWithEmailAndPassword(AccountRules.authEmail(username), password)
            .addOnSuccessListener { passwordEntered = true }

    /** Ends the session. The next launch needs the password, even on a trusted device. */
    fun logOut() {
        auth.signOut()
        passwordEntered = false
    }

    /** Checks the password again. Firebase requires this shortly before [deleteAccount]. */
    fun confirmPassword(password: String): Task<Void> {
        val user = auth.currentUser
            ?: return Tasks.forException(IllegalStateException("Not logged in"))
        return user.reauthenticate(EmailAuthProvider.getCredential(user.email.orEmpty(), password))
    }

    /** Deletes the login, which frees the username. The profile is [UserRepository.delete]. */
    fun deleteAccount(): Task<Void> {
        val user = auth.currentUser
            ?: return Tasks.forException(IllegalStateException("Not logged in"))
        return user.delete().addOnSuccessListener { passwordEntered = false }
    }

    companion object {
        private var passwordEntered = false

        /** Turns a failed task's exception into text to show the user. */
        fun errorMessage(e: Exception): String = when (e) {
            is FirebaseAuthUserCollisionException -> "That username is taken"
            is FirebaseAuthInvalidCredentialsException,
            is FirebaseAuthInvalidUserException -> "Wrong username or password"
            is FirebaseNetworkException -> "Check your connection and try again"
            else -> e.message ?: "Something went wrong"
        }
    }
}
