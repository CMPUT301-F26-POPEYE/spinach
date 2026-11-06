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
import com.example.spinach.User
import com.example.spinach.UserRepository
import com.example.spinach.ui.theme.SpinachTheme

// This code was produced partly by Claude Opus 5.5, 6/11/2026 over the prompt "Here is how I want the login process to go: username/password accounts with an optional device login."

private sealed interface LoginStep {
    data object Checking : LoginStep
    data class LoadFailed(val message: String) : LoginStep
    data object LogIn : LoginStep
    data object CreateAccount : LoginStep
    data object Details : LoginStep
    data class OfferDeviceLogin(val user: User) : LoginStep
}

// A trusted device with a saved session goes straight to onLoggedIn. Anyone else logs in
// with a username and password, or creates an account: username and password first, then
// their details, then the offer to use this device to log in from now on.
@Composable
fun UserLoginScreen(onLoggedIn: (User) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val deviceId = remember { User.readDeviceId(context) }
    var step by remember { mutableStateOf<LoginStep>(LoginStep.Checking) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

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
                    user.trustsDevice(deviceId) || accounts.loggedInWithPassword -> onLoggedIn(user)
                    else -> {
                        accounts.logOut()
                        show(LoginStep.LogIn)
                    }
                }
            }
            .addOnFailureListener { show(LoginStep.LoadFailed(AccountRepository.errorMessage(it))) }
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
            onLogIn = { username, password ->
                busy = true
                status = ""
                AccountRepository().logIn(username, password)
                    .addOnSuccessListener { resumeSession() }
                    .addOnFailureListener { fail(it) }
            },
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
                        .addOnSuccessListener { show(LoginStep.Details) }
                        .addOnFailureListener { fail(it) }
                }
            },
            onBack = { show(LoginStep.LogIn) },
            modifier = modifier,
        )
        LoginStep.Details -> AccountDetailsForm(
            busy = busy,
            status = status,
            onSubmit = { name, email, phoneNumber, hosting ->
                val accounts = AccountRepository()
                val user = User.newAccount(accounts.currentUserId.orEmpty(), accounts.currentUsername, hosting)
                val problem = AccountRules.contactError(name, email)
                if (problem == null && user.updateProfile(name, email, phoneNumber)) {
                    busy = true
                    status = ""
                    UserRepository().save(user)
                        .addOnSuccessListener {
                            if (deviceId.isBlank()) onLoggedIn(user) else show(LoginStep.OfferDeviceLogin(user))
                        }
                        .addOnFailureListener { fail(it) }
                } else {
                    status = problem.orEmpty()
                }
            },
            modifier = modifier,
        )
        is LoginStep.OfferDeviceLogin -> DeviceLoginOffer(
            busy = busy,
            status = status,
            onAccept = {
                busy = true
                status = ""
                current.user.trustDevice(deviceId)
                UserRepository().save(current.user)
                    .addOnSuccessListener { onLoggedIn(current.user) }
                    .addOnFailureListener {
                        current.user.untrustDevice(deviceId)
                        fail(it)
                    }
            },
            onDecline = { onLoggedIn(current.user) },
            modifier = modifier,
        )
    }
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
    onSubmit: (name: String, email: String, phoneNumber: String, hosting: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var hosting by remember { mutableStateOf(false) }

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
        JoiningOption("Book campsites", selected = !hosting, enabled = !busy) { hosting = false }
        JoiningOption("Host campsites (you can book too)", selected = hosting, enabled = !busy) { hosting = true }
        Button(
            enabled = !busy,
            onClick = { onSubmit(name, email, phoneNumber, hosting) },
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
            "This device will log you in automatically, so you won't need your username " +
                "and password here. You can change this later in your profile."
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
        LogInForm(busy = false, status = "", onLogIn = { _, _ -> }, onCreateAccount = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountDetailsFormPreview() {
    SpinachTheme {
        AccountDetailsForm(busy = false, status = "", onSubmit = { _, _, _, _ -> })
    }
}

@Preview(showBackground = true)
@Composable
private fun DeviceLoginOfferPreview() {
    SpinachTheme {
        DeviceLoginOffer(busy = false, status = "", onAccept = {}, onDecline = {})
    }
}
