package com.example.spinach

import android.content.Context
import android.provider.Settings
/*
*User
* Represents a person using the app. User can be identified by their device ID.
* Stores user contact info, notification preference, roles they can be. A single user can be an owner/entrant/admin and can switch between the roles.
*
* To cover: (todo) US 01.09.01, US 01.07.03, US 01.10.01, (todo) US 03.11.01
*
 */
class User (
    /** Unique, device-based identifier. Acts as the user's ID (US 01.09.01). */
    var deviceId: String = "",
    var name: String? = null,
    var email: String? = null,        // optional
    var phoneNumber: String? = null,  // optional

    /** true = opted in to notifications (US 01.10.01). Default is opted out. */
    var notificationsEnabled: Boolean = false,

    /** All roles this user is allowed to act as. Everyone is an ENTRANT by default. */
    var roles: MutableSet<Role> = mutableSetOf(Role.ENTRANT),

    /** The role the user is currently acting as. */
    var activeRole: Role = Role.ENTRANT
    ) {

        /** Roles a single user can act as (US 03.11.01). */
        enum class Role {
            ENTRANT,
            OWNER,
            ADMIN
        }

        // -------------------------------------------------------------- profile

        /**
         * Updates contact info (US 01.07.03). Returns false and changes nothing if
         * the name is empty or a non-empty email is malformed.
         */
        fun updateProfile(name: String?, email: String?, phoneNumber: String?): Boolean {
            if (name.isNullOrBlank()) return false
            if (!email.isNullOrBlank() && !isValidEmail(email)) return false

            this.name = name.trim()
            this.email = email?.trim()?.takeIf { it.isNotEmpty() }
            this.phoneNumber = phoneNumber?.trim()?.takeIf { it.isNotEmpty() }
            return true
        }

        /**
         * Clears the personal info on this object (US 01.08.01).
         * NOTE: this only wipes the local object. Actually removing the user's record
         * from the database should be done by whatever handles persistence.
         */
        fun clearProfile() {
            name = null
            email = null
            phoneNumber = null
            notificationsEnabled = false
        }

        // ---------------------------------------------------------------- roles

        fun hasRole(role: Role): Boolean = role in roles

        /** Grants a role to this user (e.g. when they register as an owner). */
        fun addRole(role: Role) {
            roles.add(role)
        }

        /** Removes a role. ENTRANT cannot be removed. Falls back to ENTRANT if it was active. */
        fun removeRole(role: Role) {
            if (role == Role.ENTRANT) return
            roles.remove(role)
            if (activeRole == role) activeRole = Role.ENTRANT
        }

        /**
         * Switches which role the user is currently acting as (US 03.11.01).
         * Returns false if the user doesn't hold that role.
         */
        fun switchRole(role: Role): Boolean {
            if (role !in roles) return false
            activeRole = role
            return true
        }

        companion object {
            private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

            fun isValidEmail(email: String?): Boolean =
                email != null && EMAIL_REGEX.matches(email.trim())

            /**
             * Reads this device's unique ID so the user can be identified without
             * a username/password (US 01.09.01).
             */
            fun readDeviceId(context: Context): String =
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""

            /** Convenience factory: creates a new User tied to this device. */
            fun fromDevice(context: Context, name: String?): User =
                User(deviceId = readDeviceId(context), name = name)
        }

        // =====================================================================
        // STUBS: depend on classes that don't exist yet.
        // Uncomment / fill in once Notification, Reservation, Message, Lottery exist.
        // =====================================================================

        // --- Notification (collaborator) ---
        // fun getNotifications(): List<Notification> = TODO()

        // --- Reservation (collaborator) ---
        // fun getReservations(): List<Reservation> = TODO() // reservations where userId == deviceId

        // --- Message (collaborator) ---
        // fun getMessages(): List<Message> = TODO() // messages where receiverId == deviceId

        // --- Lottery (collaborator) ---
        // fun getJoinedLotteries(): List<Lottery> = TODO() // lotteries this user is waitlisted in
    }

