package com.example.spinach.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spinach.AccountRepository
import com.example.spinach.AccountRules
import com.example.spinach.DeviceAccount
import com.example.spinach.DeviceAccountStore
import com.example.spinach.User
import com.example.spinach.UserRepository
import com.example.spinach.ui.theme.SpinachTheme
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException

// This code was produced partly by Claude Opus 5.5, 6/11/2026 over the prompt "Here is how I want the login process to go: username/password accounts with an optional device login."

private sealed interface LoginStep {
    data object Checking : LoginStep
    data class LoadFailed(val message: String) : LoginStep
    data object LogIn : LoginStep
    data object CreateAccount : LoginStep
    data object Details : LoginStep
    data class OfferNotifications(val user: User) : LoginStep
    data class OfferDeviceLogin(val user: User) : LoginStep
}

// A trusted device with a saved session goes straight to onLoggedIn. Anyone else logs in
// with a passkey on a registered device, with a username and password, or creates an
// account: username and password first, then their details, then a pop-up asking to turn
// on notifications, then the offer to use this device to log in from now on.
@Composable
fun UserLoginScreen(onLoggedIn: (User) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val deviceId = remember { User.readDeviceId(context) }
    val deviceAccounts = remember { DeviceAccountStore(context) }
    var step by remember { mutableStateOf<LoginStep>(LoginStep.Checking) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var pendingPassword by remember { mutableStateOf<String?>(null) }
    var accountChoices by remember { mutableStateOf<List<DeviceAccount>>(emptyList()) }

    fun show(next: LoginStep) {
        step = next
        busy = false
        status = ""
    }

    fun fail(e: Exception) {
        busy = false
        status = AccountRepository.errorMessage(e)
    }

    // Decides where a saved session goes. Details is reached here if the app was closed
    // after creating an account but before the details were saved.
    fun resumeSession() {
        val accounts = AccountRepository()
        val userId = accounts.currentUserId
        if (userId == null) {
            show(LoginStep.LogIn)
            return
        }
        show(LoginStep.Checking)
        UserRepository().get(userId)
            .addOnSuccessListener { user ->
                when {
                    user == null -> show(LoginStep.Details)
                    AccountRules.canResumeSession(user, deviceId, accounts.loggedInWithPassword) -> {
                        // Registers the device again after a reinstall.
                        val password = pendingPassword
                        if (password != null && user.trustsDevice(deviceId)) {
                            deviceAccounts.save(user.username, user.name, password)
                        }
                        onLoggedIn(user)
                    }
                    else -> {
                        accounts.logOut()
                        show(LoginStep.LogIn)
                    }
                }
            }
            .addOnFailureListener { show(LoginStep.LoadFailed(AccountRepository.errorMessage(it))) }
    }

    fun logIn(username: String, password: String, onRejected: (Exception) -> Unit = {}) {
        busy = true
        status = ""
        pendingPassword = password
        AccountRepository().logIn(username, password)
            .addOnSuccessListener { resumeSession() }
            .addOnFailureListener {
                pendingPassword = null
                onRejected(it)
                fail(it)
            }
    }

    fun logInWithPasskey(account: DeviceAccount) {
        accountChoices = emptyList()
        val password = deviceAccounts.password(account.username)
        if (password == null) {
            deviceAccounts.remove(account.username)
            status = "Log in with your password to use this device again"
            return
        }
        logIn(account.username, password) {
            // The saved password no longer works, so don't keep offering it.
            if (it is FirebaseAuthInvalidCredentialsException) deviceAccounts.remove(account.username)
        }
    }

    fun onPasskeyClicked() {
        val saved = deviceAccounts.accounts()
        when (saved.size) {
            0 -> status = "No accounts are registered on this device"
            1 -> logInWithPasskey(saved.single())
            else -> accountChoices = saved
        }
    }

    // Without the password the device can't be registered here, so the Profile screen
    // offers it instead.
    fun offerDeviceLogin(user: User) {
        if (deviceId.isBlank() || pendingPassword == null) {
            onLoggedIn(user)
        } else {
            show(LoginStep.OfferDeviceLogin(user))
        }
    }

    LaunchedEffect(Unit) { resumeSession() }
    BackHandler(enabled = step == LoginStep.CreateAccount) { show(LoginStep.LogIn) }

    when (val current = step) {
        LoginStep.Checking -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is LoginStep.LoadFailed -> LoginColumn(modifier) {
            Text(current.message)
            Button(onClick = { resumeSession() }) {
                Text("Try again")
            }
        }
        LoginStep.LogIn -> LogInForm(
            busy = busy,
            status = status,
            onLogIn = { username, password -> logIn(username, password) },
            onPasskey = { onPasskeyClicked() },
            onCreateAccount = { show(LoginStep.CreateAccount) },
            modifier = modifier,
        )
        LoginStep.CreateAccount -> CreateAccountForm(
            busy = busy,
            status = status,
            onSubmit = { username, password, confirmPassword ->
                val problem = AccountRules.credentialsError(username, password, confirmPassword)
                if (problem != null) {
                    status = problem
                } else {
                    busy = true
                    status = ""
                    AccountRepository().createAccount(username, password)
                        .addOnSuccessListener {
                            pendingPassword = password
                            show(LoginStep.Details)
                        }
                        .addOnFailureListener { fail(it) }
                }
            },
            onBack = { show(LoginStep.LogIn) },
            modifier = modifier,
        )
        LoginStep.Details -> AccountDetailsForm(
            busy = busy,
            status = status,
            onSubmit = { name, email, phoneNumber, role, adminPassword ->
                val accounts = AccountRepository()
                val user = User.newAccount(accounts.currentUserId.orEmpty(), accounts.currentUsername, role)
                val problem = AccountRules.contactError(name, email)
                    ?: if (role == User.Role.ADMIN) AccountRules.adminPasswordError(adminPassword) else null
                if (problem == null && user.updateProfile(name, email, phoneNumber)) {
                    busy = true
                    status = ""
                    UserRepository().save(user)
                        .addOnSuccessListener { show(LoginStep.OfferNotifications(user)) }
                        .addOnFailureListener { fail(it) }
                } else {
                    status = problem.orEmpty()
                }
            },
            modifier = modifier,
        )
        is LoginStep.OfferNotifications -> NotificationsPrompt(
            busy = busy,
            status = status,
            onTurnOn = {
                busy = true
                status = ""
                current.user.notificationsEnabled = true
                UserRepository().save(current.user)
                    .addOnSuccessListener { offerDeviceLogin(current.user) }
                    .addOnFailureListener {
                        current.user.notificationsEnabled = false
                        fail(it)
                    }
            },
            onNotNow = { offerDeviceLogin(current.user) },
        )
        is LoginStep.OfferDeviceLogin -> DeviceLoginOffer(
            busy = busy,
            status = status,
            onAccept = {
                val user = current.user
                if (deviceAccounts.save(user.username, user.name, pendingPassword.orEmpty())) {
                    busy = true
                    status = ""
                    user.trustDevice(deviceId)
                    UserRepository().save(user)
                        .addOnSuccessListener { onLoggedIn(user) }
                        .addOnFailureListener {
                            user.untrustDevice(deviceId)
                            deviceAccounts.remove(user.username)
                            fail(it)
                        }
                } else {
                    status = "Could not register this device"
                }
            },
            onDecline = { onLoggedIn(current.user) },
            modifier = modifier,
        )
    }

    if (accountChoices.isNotEmpty()) {
        DeviceAccountPicker(
            accounts = accountChoices,
            onPick = { logInWithPasskey(it) },
            onDismiss = { accountChoices = emptyList() },
        )
    }
}

// US 01.10.01: notifications stay off unless the user chooses to turn them on here or on
// the Profile screen. The user has to choose, so tapping outside doesn't close the pop-up.
@Composable
private fun NotificationsPrompt(
    busy: Boolean,
    status: String,
    onTurnOn: () -> Unit,
    onNotNow: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Turn on notifications?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Get updates about your lotteries and reservations. You can change this later in your profile.")
                if (status.isNotEmpty()) Text(status)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = onTurnOn) {
                Text(if (busy) "Saving..." else "Turn on")
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onNotNow) {
                Text("Not now")
            }
        },
    )
}

// Shown when several accounts are registered on this device.
@Composable
private fun DeviceAccountPicker(
    accounts: List<DeviceAccount>,
    onPick: (DeviceAccount) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an account") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                accounts.forEach { account ->
                    OutlinedButton(
                        onClick = { onPick(account) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${account.name} (${account.username})")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun LoginColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun UsernameField(value: String, onValueChange: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Username") },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

// The password field, shared with the delete confirmation on the profile screen.
@Composable
internal fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    label: String = "Password",
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LogInForm(
    busy: Boolean,
    status: String,
    onLogIn: (username: String, password: String) -> Unit,
    onPasskey: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LoginColumn(modifier) {
        Text("Welcome to Spinach", style = MaterialTheme.typography.titleLarge)
        UsernameField(username, { username = it }, enabled = !busy)
        PasswordField(password, { password = it }, enabled = !busy)
        Button(
            enabled = !busy && username.isNotBlank() && password.isNotEmpty(),
            onClick = { onLogIn(username, password) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "Logging in..." else "Log in")
        }
        OutlinedButton(
            enabled = !busy,
            onClick = onPasskey,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Login with passkey")
        }
        OutlinedButton(
            enabled = !busy,
            onClick = onCreateAccount,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Create an account")
        }
        Text(status)
    }
}

@Composable
private fun CreateAccountForm(
    busy: Boolean,
    status: String,
    onSubmit: (username: String, password: String, confirmPassword: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    LoginColumn(modifier) {
        TextButton(onClick = onBack, enabled = !busy) {
            Text("Back")
        }
        Text("Create your account", style = MaterialTheme.typography.titleLarge)
        UsernameField(username, { username = it }, enabled = !busy)
        PasswordField(password, { password = it }, enabled = !busy)
        PasswordField(confirmPassword, { confirmPassword = it }, enabled = !busy, label = "Confirm password")
        Button(
            enabled = !busy,
            onClick = { onSubmit(username, password, confirmPassword) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "Creating..." else "Continue")
        }
        Text(status)
    }
}

@Composable
private fun AccountDetailsForm(
    busy: Boolean,
    status: String,
    onSubmit: (
        name: String,
        email: String,
        phoneNumber: String,
        role: User.Role,
        adminPassword: String,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(User.Role.ENTRANT) }
    var adminPassword by remember { mutableStateOf("") }

    LoginColumn(modifier) {
        Text("Your details", style = MaterialTheme.typography.titleLarge)
        ContactFields(
            name = name,
            email = email,
            phoneNumber = phoneNumber,
            onNameChange = { name = it },
            onEmailChange = { email = it },
            onPhoneNumberChange = { phoneNumber = it },
            enabled = !busy,
        )
        Text("I'm joining to", style = MaterialTheme.typography.titleMedium)
        JoiningOption("Book campsites", selected = role == User.Role.ENTRANT, enabled = !busy) {
            role = User.Role.ENTRANT
        }
        JoiningOption("Host campsites (you can book too)", selected = role == User.Role.OWNER, enabled = !busy) {
            role = User.Role.OWNER
        }
        JoiningOption("Register as Admin", selected = role == User.Role.ADMIN, enabled = !busy) {
            role = User.Role.ADMIN
        }
        if (role == User.Role.ADMIN) {
            PasswordField(adminPassword, { adminPassword = it }, enabled = !busy, label = "Admin password")
        }
        Button(
            enabled = !busy,
            onClick = { onSubmit(name, email, phoneNumber, role, adminPassword) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "Saving..." else "Continue")
        }
        Text(status)
    }
}

@Composable
private fun JoiningOption(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun DeviceLoginOffer(
    busy: Boolean,
    status: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LoginColumn(modifier) {
        Text("Log in with this device?", style = MaterialTheme.typography.titleLarge)
        Text(
            "You won't need your username and password on this device. Anyone using it " +
                "can log in as you with Login with passkey. You can change this later " +
                "in your profile."
        )
        Button(enabled = !busy, onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Saving..." else "Use this device")
        }
        OutlinedButton(enabled = !busy, onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
            Text("Keep using my password")
        }
        Text(status)
    }
}

@Preview(showBackground = true)
@Composable
private fun LogInFormPreview() {
    SpinachTheme {
        LogInForm(busy = false, status = "", onLogIn = { _, _ -> }, onPasskey = {}, onCreateAccount = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun NotificationsPromptPreview() {
    SpinachTheme {
        NotificationsPrompt(busy = false, status = "", onTurnOn = {}, onNotNow = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun DeviceAccountPickerPreview() {
    SpinachTheme {
        DeviceAccountPicker(
            accounts = listOf(
                DeviceAccount("alex_camper", "Alex Camper"),
                DeviceAccount("sam_hikes", "Sam Hikes"),
            ),
            onPick = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountDetailsFormPreview() {
    SpinachTheme {
        AccountDetailsForm(busy = false, status = "", onSubmit = { _, _, _, _, _ -> })
    }
}

@Preview(showBackground = true)
@Composable
private fun DeviceLoginOfferPreview() {
    SpinachTheme {
        DeviceLoginOffer(busy = false, status = "", onAccept = {}, onDecline = {})
    }
}
