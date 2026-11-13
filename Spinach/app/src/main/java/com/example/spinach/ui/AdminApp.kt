package com.example.spinach.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.spinach.AdminRepository
import com.example.spinach.AdminRules
import com.example.spinach.Campsite
import com.example.spinach.ImageRef
import com.example.spinach.Message
import com.example.spinach.MessageRepository
import com.example.spinach.MessageRules
import com.example.spinach.Notification
import com.example.spinach.NotificationRepository
import com.example.spinach.NotificationRules
import com.example.spinach.ProfileSummary

private enum class AdminScreen(val title: String) {
    Home("Admin"),
    Campsites("Campsites"),
    Profiles("Profiles"),
    Images("Images"),
    Notifications("Notification log"),
    Messages("Message log"),
}

// The admin side of the app. Show it only to a user for whom AdminRepository.isAdmin is true.
// US 03.01.01 to 03.08.01, 03.12.01. US 03.11.01 is the switch in MainActivity back to the owner screens.
@Composable
fun AdminApp(modifier: Modifier = Modifier) {
    var screen by remember { mutableStateOf(AdminScreen.Home) }

    BackHandler(enabled = screen != AdminScreen.Home) { screen = AdminScreen.Home }

    Column(modifier = modifier.fillMaxSize()) {
        if (screen != AdminScreen.Home) {
            TextButton(onClick = { screen = AdminScreen.Home }, modifier = Modifier.padding(start = 8.dp)) {
                Text("Back")
            }
        }
        when (screen) {
            AdminScreen.Home -> AdminHome(onOpen = { screen = it }, modifier = Modifier.weight(1f))
            AdminScreen.Campsites -> AdminCampsitesScreen(modifier = Modifier.weight(1f))
            AdminScreen.Profiles -> AdminProfilesScreen(modifier = Modifier.weight(1f))
            AdminScreen.Images -> AdminImagesScreen(modifier = Modifier.weight(1f))
            AdminScreen.Notifications -> AdminNotificationsScreen(modifier = Modifier.weight(1f))
            AdminScreen.Messages -> AdminMessagesScreen(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun AdminHome(onOpen: (AdminScreen) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Admin", style = MaterialTheme.typography.titleLarge)
        for (target in AdminScreen.entries.filter { it != AdminScreen.Home }) {
            OutlinedButton(onClick = { onOpen(target) }, modifier = Modifier.fillMaxWidth()) {
                Text(target.title)
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SearchBox(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        label = { Text("Search") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ScreenMessage(text: String, isError: Boolean = false) {
    Text(
        text = text,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

private enum class CampsiteAction { REMOVE_CAMPSITE, REMOVE_OWNER }

// US 03.04.01 browse, US 03.01.01 remove a campsite, US 03.07.01 remove an owner
@Composable
private fun AdminCampsitesScreen(modifier: Modifier = Modifier) {
    var campsites by remember { mutableStateOf<List<Campsite>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Pair<Campsite, CampsiteAction>?>(null) }

    DisposableEffect(Unit) {
        val registration = AdminRepository().listenCampsites(
            onResult = {
                campsites = it
                error = null
            },
            onError = { error = it.message ?: "Unknown error" },
        )
        onDispose { registration.remove() }
    }

    val shown = campsites.filter { AdminRules.matches(query, it.name, it.location, it.ownerId) }

    Column(modifier = modifier) {
        SearchBox(query) { query = it }
        error?.let { ScreenMessage("Could not load campsites: $it", isError = true) }
        if (status.isNotEmpty()) ScreenMessage(status)
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            if (shown.isEmpty()) item { ScreenMessage("No campsites.") }
            items(shown) { campsite ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(campsite.name.ifEmpty { "Unnamed campsite" }, style = MaterialTheme.typography.titleSmall)
                        Text(campsite.location, style = MaterialTheme.typography.bodyMedium)
                        Text("Owner ${campsite.ownerId}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { pending = campsite to CampsiteAction.REMOVE_CAMPSITE }) {
                                Text("Remove campsite")
                            }
                            OutlinedButton(onClick = { pending = campsite to CampsiteAction.REMOVE_OWNER }) {
                                Text("Remove owner")
                            }
                        }
                    }
                }
            }
        }
    }

    val current = pending
    if (current != null) {
        val (campsite, action) = current
        val removeOwner = action == CampsiteAction.REMOVE_OWNER
        ConfirmDialog(
            title = if (removeOwner) "Remove this owner?" else "Remove this campsite?",
            text = if (removeOwner) {
                "This deletes the owner's profile and every campsite and lottery they own."
            } else {
                "This deletes ${campsite.name.ifEmpty { "this campsite" }}."
            },
            confirmLabel = "Remove",
            onConfirm = {
                pending = null
                val finished: (Result<Unit>) -> Unit = { result ->
                    status = result.fold({ "Removed" }, { it.message ?: "Could not remove" })
                }
                if (removeOwner) AdminRepository().removeOwner(campsite.ownerId, finished)
                else AdminRepository().deleteCampsite(campsite.id, finished)
            },
            onDismiss = { pending = null },
        )
    }
}

// US 03.05.01 browse, US 03.02.01 remove
@Composable
private fun AdminProfilesScreen(modifier: Modifier = Modifier) {
    var profiles by remember { mutableStateOf<List<ProfileSummary>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<ProfileSummary?>(null) }

    DisposableEffect(Unit) {
        val registration = AdminRepository().listenProfiles(
            onResult = {
                profiles = it
                error = null
            },
            onError = { error = it.message ?: "Unknown error" },
        )
        onDispose { registration.remove() }
    }

    val shown = profiles.filter { AdminRules.matches(query, it.name, it.email, it.phone, it.id) }

    Column(modifier = modifier) {
        SearchBox(query) { query = it }
        error?.let { ScreenMessage("Could not load profiles: $it", isError = true) }
        if (status.isNotEmpty()) ScreenMessage(status)
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            if (shown.isEmpty()) item { ScreenMessage("No profiles.") }
            items(shown) { profile ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(profile.name.ifEmpty { "No name" }, style = MaterialTheme.typography.titleSmall)
                        if (profile.email.isNotEmpty()) Text(profile.email)
                        if (profile.phone.isNotEmpty()) Text(profile.phone)
                        Text(profile.id, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { pending = profile }) { Text("Remove profile") }
                    }
                }
            }
        }
    }

    val current = pending
    if (current != null) {
        ConfirmDialog(
            title = "Remove this profile?",
            text = current.name.ifEmpty { current.id },
            confirmLabel = "Remove",
            onConfirm = {
                pending = null
                AdminRepository().deleteProfile(current.id) { result ->
                    status = result.fold({ "Profile removed" }, { it.message ?: "Could not remove" })
                }
            },
            onDismiss = { pending = null },
        )
    }
}

// US 03.06.01 browse, US 03.03.01 remove. Shows the image address because the app has no image loader yet.
@Composable
private fun AdminImagesScreen(modifier: Modifier = Modifier) {
    var campsites by remember { mutableStateOf<List<Campsite>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<ImageRef?>(null) }

    DisposableEffect(Unit) {
        val registration = AdminRepository().listenCampsites(
            onResult = {
                campsites = it
                error = null
            },
            onError = { error = it.message ?: "Unknown error" },
        )
        onDispose { registration.remove() }
    }

    val images = remember(campsites) { AdminRules.images(campsites) }

    Column(modifier = modifier) {
        error?.let { ScreenMessage("Could not load images: $it", isError = true) }
        if (status.isNotEmpty()) ScreenMessage(status)
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            if (images.isEmpty()) item { ScreenMessage("No uploaded images.") }
            items(images) { image ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(image.campsiteName.ifEmpty { "Unnamed campsite" }, style = MaterialTheme.typography.titleSmall)
                        Text(image.url, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { pending = image }) { Text("Remove image") }
                    }
                }
            }
        }
    }

    val current = pending
    if (current != null) {
        ConfirmDialog(
            title = "Remove this image?",
            text = current.url,
            confirmLabel = "Remove",
            onConfirm = {
                pending = null
                AdminRepository().removeImage(current.campsiteId, current.url) { result ->
                    status = result.fold({ "Image removed" }, { it.message ?: "Could not remove" })
                }
            },
            onDismiss = { pending = null },
        )
    }
}

// US 03.08.01
@Composable
private fun AdminNotificationsScreen(modifier: Modifier = Modifier) {
    var notifications by remember { mutableStateOf<List<Notification>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val registration = NotificationRepository().listenAll(
            onResult = {
                notifications = it
                error = null
            },
            onError = { error = it.message ?: "Unknown error" },
        )
        onDispose { registration.remove() }
    }

    val sorted = remember(notifications) { NotificationRules.newestFirst(notifications) }

    Column(modifier = modifier) {
        error?.let { ScreenMessage("Could not load notifications: $it", isError = true) }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item { Text("All notifications (${sorted.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp)) }
            items(sorted) { notification ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(notification.message)
                        Text(
                            "From ${notification.senderId} to ${notification.recipientId}, ${formatDateTime(notification.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

// US 03.12.01
@Composable
private fun AdminMessagesScreen(modifier: Modifier = Modifier) {
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val registration = MessageRepository().listenAll(
            onResult = {
                messages = it
                error = null
            },
            onError = { error = it.message ?: "Unknown error" },
        )
        onDispose { registration.remove() }
    }

    val sorted = remember(messages) { MessageRules.newestFirst(messages) }

    Column(modifier = modifier) {
        error?.let { ScreenMessage("Could not load messages: $it", isError = true) }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item { Text("All owner messages (${sorted.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp)) }
            items(sorted) { message ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(message.text)
                        Text(
                            "${message.campsiteName.ifEmpty { "Campsite" }}: ${message.senderId} to ${message.recipientId}, ${formatDateTime(message.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
