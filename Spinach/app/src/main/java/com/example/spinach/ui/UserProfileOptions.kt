package com.example.spinach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spinach.AccountRepository
import com.example.spinach.AccountRules
import com.example.spinach.DeviceAccountStore
import com.example.spinach.User
import com.example.spinach.UserRepository
import com.example.spinach.ui.theme.SpinachTheme

// This code was produced partly by Claude Opus 5.5, 6/11/2026 over the prompt "I have to complete UserLoginScreen.kt, and UserProfileOptions.kt. Can you help with the two ui files?"

// The contact form, shared by the login screen and the profile screen.
@Composable
internal fun ContactFields(
    name: String,
    email: String,
    phoneNumber: String,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPhoneNumberChange: (String) -> Unit,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text("Name") },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = { Text("Email") },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = phoneNumber,
        onValueChange = onPhoneNumberChange,
        label = { Text("Phone number (optional)") },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
    )
}

// The logged in user's options: edit contact info (US 01.07.03), turn notifications on or
// off (US 01.10.01), turn device login on or off (US 01.09.01), switch role (US 03.11.01),
// log out, and delete the account (US 01.08.01).
// After onLoggedOut or onProfileDeleted, the caller must discard user and go back to the
// login screen.
@Composable
fun UserProfileOptions(
    user: User,
    deviceId: String,
    onRoleChanged: (User.Role) -> Unit,
    onLoggedOut: () -> Unit,
    onProfileDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember(user) { mutableStateOf(user.name) }
    var email by remember(user) { mutableStateOf(user.email.orEmpty()) }
    var phoneNumber by remember(user) { mutableStateOf(user.phoneNumber.orEmpty()) }
    var notificationsEnabled by remember(user) { mutableStateOf(user.notificationsEnabled) }
    val context = LocalContext.current
    var deviceLogin by remember(user) {
        mutableStateOf(user.trustsDevice(deviceId) && DeviceAccountStore(context).contains(user.username))
    }
    var activeRole by remember(user) { mutableStateOf(user.activeRole) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var confirmingDeviceLogin by remember { mutableStateOf(false) }
    var deviceLoginPassword by remember { mutableStateOf("") }
    var confirmingDelete by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }

    // Saves a change already made to user. If the save fails, undo puts it back so
    // user keeps matching what is in the database.
    fun saveChange(undo: () -> Unit, onSaved: () -> Unit) {
        busy = true
        status = ""
        UserRepository().save(user)
            .addOnSuccessListener {
                busy = false
                onSaved()
            }
            .addOnFailureListener {
                undo()
                busy = false
                status = it.message ?: "Could not save"
            }
    }

    fun saveContactInfo() {
        val problem = AccountRules.contactError(name, email)
        val oldName = user.name
        val oldEmail = user.email
        val oldPhoneNumber = user.phoneNumber
        if (problem != null || !user.updateProfile(name, email, phoneNumber)) {
            status = problem.orEmpty()
            return
        }
        saveChange(
            undo = {
                user.name = oldName
                user.email = oldEmail
                user.phoneNumber = oldPhoneNumber
            },
            onSaved = { status = "Profile saved" },
        )
    }

    fun setNotifications(enabled: Boolean) {
        user.notificationsEnabled = enabled
        notificationsEnabled = enabled
        saveChange(
            undo = {
                user.notificationsEnabled = !enabled
                notificationsEnabled = !enabled
            },
            onSaved = {},
        )
    }

    fun setDeviceTrusted(trusted: Boolean, onSaved: () -> Unit) {
        fun apply(on: Boolean) {
            if (on) user.trustDevice(deviceId) else user.untrustDevice(deviceId)
            deviceLogin = on
        }
        apply(trusted)
        saveChange(undo = { apply(!trusted) }, onSaved = onSaved)
    }

    fun closeDeviceLoginDialog() {
        confirmingDeviceLogin = false
        deviceLoginPassword = ""
    }

    // Registering the device keeps the password on it, so the password is checked first.
    fun enableDeviceLogin() {
        val password = deviceLoginPassword
        closeDeviceLoginDialog()
        busy = true
        status = ""
        AccountRepository().confirmPassword(password)
            .addOnSuccessListener {
                if (DeviceAccountStore(context).save(user.username, user.name, password)) {
                    setDeviceTrusted(true, onSaved = {})
                } else {
                    busy = false
                    status = "Could not register this device"
                }
            }
            .addOnFailureListener {
                busy = false
                status = AccountRepository.errorMessage(it)
            }
    }

    fun disableDeviceLogin() {
        setDeviceTrusted(false, onSaved = { DeviceAccountStore(context).remove(user.username) })
    }

    fun switchRole(role: User.Role) {
        val oldRole = user.activeRole
        if (role == oldRole || !user.switchRole(role)) return
        activeRole = role
        saveChange(
            undo = {
                user.activeRole = oldRole
                activeRole = oldRole
            },
            onSaved = { onRoleChanged(role) },
        )
    }

    fun closeDeleteDialog() {
        confirmingDelete = false
        deletePassword = ""
    }

    fun deleteAccount() {
        val accounts = AccountRepository()
        val password = deletePassword
        closeDeleteDialog()
        busy = true
        status = ""
        // The password check comes first so a wrong password changes nothing, and the
        // login goes last because the profile can't be deleted once logged out.
        accounts.confirmPassword(password)
            .continueWithTask { confirmTask ->
                confirmTask.result // throws if the password was wrong
                UserRepository().delete(user.id)
            }
            .continueWithTask { deleteTask ->
                deleteTask.result // throws if the delete failed, so the login is kept
                accounts.deleteAccount()
            }
            .addOnSuccessListener {
                DeviceAccountStore(context).remove(user.username)
                onProfileDeleted()
            }
            .addOnFailureListener {
                busy = false
                status = AccountRepository.errorMessage(it)
            }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Profile", style = MaterialTheme.typography.titleLarge)
        Text("Username: ${user.username}")
        ContactFields(
            name = name,
            email = email,
            phoneNumber = phoneNumber,
            onNameChange = { name = it },
            onEmailChange = { email = it },
            onPhoneNumberChange = { phoneNumber = it },
            enabled = !busy,
        )
        Button(
            enabled = !busy,
            onClick = { saveContactInfo() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "Saving..." else "Save changes")
        }

        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Receive notifications")
            Switch(
                checked = notificationsEnabled,
                onCheckedChange = { setNotifications(it) },
                enabled = !busy,
            )
        }
        if (deviceId.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Log in with this device")
                Switch(
                    checked = deviceLogin,
                    onCheckedChange = { if (it) confirmingDeviceLogin = true else disableDeviceLogin() },
                    enabled = !busy,
                )
            }
        }

        HorizontalDivider()
        Text("Acting as", style = MaterialTheme.typography.titleMedium)
        user.roles.forEach { role ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = role == activeRole,
                        enabled = !busy,
                        onClick = { switchRole(role) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = role == activeRole, onClick = null, enabled = !busy)
                Text(
                    role.name.lowercase().replaceFirstChar { it.uppercase() },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        HorizontalDivider()
        OutlinedButton(
            enabled = !busy,
            onClick = {
                AccountRepository().logOut()
                onLoggedOut()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Log out")
        }
        OutlinedButton(
            enabled = !busy,
            onClick = { confirmingDelete = true },
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Delete my account")
        }
        Text(status)
    }

    if (confirmingDeviceLogin) {
        AlertDialog(
            onDismissRequest = { closeDeviceLoginDialog() },
            title = { Text("Log in with this device?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Anyone using this device can log in as you with Login with passkey.")
                    PasswordField(deviceLoginPassword, { deviceLoginPassword = it }, enabled = true)
                }
            },
            confirmButton = {
                TextButton(enabled = deviceLoginPassword.isNotEmpty(), onClick = { enableDeviceLogin() }) {
                    Text("Use this device")
                }
            },
            dismissButton = {
                TextButton(onClick = { closeDeviceLoginDialog() }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { closeDeleteDialog() },
            title = { Text("Delete your account?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This can't be undone. Your active reservations will be cancelled, " +
                            "you will be removed from every lottery, and any campsites you " +
                            "own will be deleted."
                    )
                    PasswordField(deletePassword, { deletePassword = it }, enabled = true)
                }
            },
            confirmButton = {
                TextButton(enabled = deletePassword.isNotEmpty(), onClick = { deleteAccount() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { closeDeleteDialog() }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun UserProfileOptionsPreview() {
    SpinachTheme {
        UserProfileOptions(
            user = User(
                id = "preview",
                username = "alex_camper",
                name = "Alex Camper",
                email = "alex@example.com",
                notificationsEnabled = true,
                roles = mutableListOf(User.Role.ENTRANT, User.Role.OWNER),
            ),
            deviceId = "preview-device",
            onRoleChanged = {},
            onLoggedOut = {},
            onProfileDeleted = {},
        )
    }
}
