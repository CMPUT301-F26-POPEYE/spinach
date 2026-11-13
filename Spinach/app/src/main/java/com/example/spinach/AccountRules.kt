package com.example.spinach

// This code was produced partly by Claude Opus 5.5, 6/11/2026 over the prompt "Here is how I want the login process to go: username/password accounts with an optional device login."

// Pure rules for creating an account, kept apart from Firebase so they can be unit tested.
object AccountRules {
    const val MIN_PASSWORD_LENGTH = 6 // Firebase Auth rejects anything shorter

    // Temporary for US 03.11.01. This ships inside the app, so it isn't real protection.
    private const val ADMIN_PASSWORD = "adminpass"

    // Firebase Auth logs in with an email, so each username maps to an address in the
    // project's own domain. Nothing is ever sent there, and Firebase keeps it unique.
    private const val AUTH_EMAIL_DOMAIN = "spinach-5f826.firebaseapp.com"
    private val USERNAME_REGEX = Regex("^[a-z0-9_]{3,20}$")

    fun normalizeUsername(username: String): String = username.trim().lowercase()

    fun authEmail(username: String): String = "${normalizeUsername(username)}@$AUTH_EMAIL_DOMAIN"

    fun usernameFromAuthEmail(email: String?): String = email.orEmpty().substringBefore("@")

    // Why this username and password can't make an account, or null if they can.
    fun credentialsError(username: String, password: String, confirmPassword: String): String? = when {
        !USERNAME_REGEX.matches(normalizeUsername(username)) ->
            "Usernames are 3 to 20 letters, numbers or underscores"
        password.length < MIN_PASSWORD_LENGTH ->
            "Passwords need at least $MIN_PASSWORD_LENGTH characters"
        password != confirmPassword -> "Passwords don't match"
        else -> null
    }

    // Why this name and email can't be saved, or null if they can.
    fun contactError(name: String, email: String): String? = when {
        name.isBlank() -> "Enter your name"
        email.isBlank() -> "Enter your email"
        !User.isValidEmail(email) -> "Enter a valid email"
        else -> null
    }

    // Why this password can't register an admin, or null if it can.
    fun adminPasswordError(password: String): String? =
        if (password == ADMIN_PASSWORD) null else "Incorrect admin password"

    // Whether a saved session may be used without logging in again (US 01.09.01). Only a
    // device the user registered can skip logging in. Any other device needs the password.
    fun canResumeSession(user: User, deviceId: String, loggedInWithPassword: Boolean): Boolean =
        user.trustsDevice(deviceId) || loggedInWithPassword
}
