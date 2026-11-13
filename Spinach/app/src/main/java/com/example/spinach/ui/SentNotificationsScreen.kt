package com.example.spinach.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spinach.Notification
import com.example.spinach.NotificationRepository
import com.example.spinach.NotificationRules
import com.example.spinach.ui.theme.SpinachTheme

// US 02.10.02: every notification this owner has sent, newest first.
@Composable
fun SentNotificationsScreen(
    senderId: String,
    modifier: Modifier = Modifier,
) {
    var notifications by remember { mutableStateOf<List<Notification>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(senderId) {
        val registration = NotificationRepository().listenForSender(
            senderId = senderId,
            onResult = {
                notifications = it
                error = null
            },
            onError = { error = it.message ?: "Unknown error" },
        )
        onDispose { registration.remove() }
    }

    SentNotificationsContent(notifications = notifications, error = error, modifier = modifier)
}

@Composable
fun SentNotificationsContent(
    notifications: List<Notification>,
    error: String?,
    modifier: Modifier = Modifier,
) {
    val sorted = remember(notifications) { NotificationRules.newestFirst(notifications) }

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        item {
            Text(
                text = "Sent notifications (${sorted.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        if (error != null) {
            item {
                Text(
                    text = "Could not load notifications: $error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        if (sorted.isEmpty()) {
            item { Text("You have not sent any notifications.", modifier = Modifier.padding(horizontal = 16.dp)) }
        } else {
            items(sorted) { notification ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(notification.message, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "To ${notification.recipientId} on ${formatDateTime(notification.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SentNotificationsPreview() {
    SpinachTheme {
        SentNotificationsContent(
            notifications = listOf(
                Notification(recipientId = "entrant-1", message = "The draw is on Friday", createdAt = 1_794_441_600_000L),
                Notification(recipientId = "entrant-3", message = "Bring your own firewood", createdAt = 1_794_528_000_000L),
            ),
            error = null,
        )
    }
}
