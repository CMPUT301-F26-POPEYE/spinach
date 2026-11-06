package com.example.spinach

import android.content.Context
import android.provider.Settings

// This code was produced partly by Claude Sonnet 5.5, 2/11/2026 over the prompt "How can I implement this CRC card in the wiki?"

/**
*User
* Represents a person using the app. User can be identified by their device ID.
* Stores user contact info, notification preference, roles they can be. A single user can be an owner/entrant/admin and can switch between the roles.
 *
 * Collaborators: Notification, Reservation, Lottery, Campsite. A user does not hold
 * these. Each of them stores the user's ID instead, and `UserRepository` looks them
 * up (getNotifications, getReservations, getJoinedLotteries, getOwnedCampsites).
*
* To cover: US 01.09.01, US 01.07.03, US 01.10.01, US 03.11.01, user info and object
* @constructor Creates a new User object.
* @property deviceId Unique, device-based identifier. Acts as the user's ID (US 01.09.01).
* @property name User's name.
* @property email User's email. (optional)
* @property phoneNumber User's phone number (optional)
* @property notificationsEnabled Whether the user wants to receive notifications (US 01.10.01).
* @property roles All roles this user is allowed to act as. Everyone is an ENTRANT by default.
* @property activeRole The role this user is currently acting as.
*
 */
class User (
    var deviceId: String = "",
    var name: String = "",
    var email: String? = null,
    var phoneNumber: String? = null,
    var notificationsEnabled: Boolean = false,
    var roles: MutableList<Role> = mutableListOf(Role.ENTRANT),
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
         * If validation fails, nothing is changed.
         *
         * @param name The new display name. Must not be `null` or blank.
         * @param email The new email, or `null`/blank to clear it. Must be valid if non-blank.
         * @param phoneNumber The new phone number, or `null`/blank to clear it.
         * @return `true` if the profile was updated, `false` if validation failed.
         *
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
         * Wipes only the current in-memory object. Does not affect the database.
         *
         */
        fun clearProfile() {
            name = ""
            email = null
            phoneNumber = null
            notificationsEnabled = false
        }

        // ---------------------------------------------------------------- roles
    /**
     * Checks if this user has a certain role.
     * @param role The role to check for.
     */
    fun hasRole(role: Role): Boolean = role in roles

    /** Grants a role to this user (e.g. when they register as an owner).
     * @param role The role to grant.
     */
    fun addRole(role: Role) {
        roles.add(role)
    }

        /** Removes a role. ENTRANT cannot be removed. Falls back to ENTRANT if it was active.
         * @param role The role to remove. */
        fun removeRole(role: Role) {
            if (role == Role.ENTRANT) return
            roles.remove(role)
            if (activeRole == role) activeRole = Role.ENTRANT
        }

        /**
         * Switches which role the user is currently acting as (US 03.11.01).
         * Returns false if the user doesn't hold that role.
         * @param role The role to switch to.
         */
        fun switchRole(role: Role): Boolean {
            if (role !in roles) return false
            activeRole = role
            return true
        }

        companion object {
            private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

            /**
             * Checks whether a string looks like a valid email address.
             *
             * This is a basic format check (something@domain.tld), not a guarantee that
             * the address exists. Surrounding whitespace is ignored.
             *
             * @param email The string to check. May be `null`.
             * @return `true` if [email] is non-null and matches the expected format.
             */
            fun isValidEmail(email: String?): Boolean =
                email != null && EMAIL_REGEX.matches(email.trim())

            /**
             * Reads this device's unique ID so the user can be identified without
             * a username/password (US 01.09.01).
             * @param context The app's context.
             * @return The device's unique ID.
             */
            fun readDeviceId(context: Context): String =
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""

            /** Creates a new User tied to this device.
             * @param context The app's context.
             * */
            fun fromDevice(context: Context, name: String): User =
                User(deviceId = readDeviceId(context), name = name)
        }
    }

