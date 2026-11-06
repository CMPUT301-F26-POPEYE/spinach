package com.example.spinach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spinach.MessageRepository
import com.example.spinach.Reservation
import com.example.spinach.ReservationQueries
import com.example.spinach.ReservationRepository
import com.example.spinach.ReservationStatus
import com.example.spinach.ui.theme.SpinachTheme

// US 02.07.01, 02.07.02, 02.07.03, 02.08.01 and 02.12.01.
// ownerId is passed in until profiles exist (US 01.09.01).
@Composable
fun OwnerReservationsScreen(
    ownerId: String,
    modifier: Modifier = Modifier,
    repository: ReservationRepository = remember { ReservationRepository() },
) {
    var reservations by remember { mutableStateOf<List<Reservation>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    var rejecting by remember { mutableStateOf<Reservation?>(null) }
    var messaging by remember { mutableStateOf<Reservation?>(null) }
    var messageText by remember { mutableStateOf("") }

    DisposableEffect(ownerId) {
        val registration = repository.listenForOwner(
            ownerId = ownerId,
            onResult = {
                reservations = it
                error = null
            },
            onError = { error = it.message ?: "Unknown error" },
        )
        onDispose { registration.remove() }
    }

    Column(modifier = modifier) {
        if (status.isNotEmpty()) {
            Text(status, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        OwnerReservationsContent(
            reservations = reservations,
            error = error,
            today = ReservationQueries.todayUtcMidnight(),
            onReject = { rejecting = it },
            onMessage = {
                messageText = ""
                messaging = it
            },
            modifier = Modifier.weight(1f),
        )
    }

    val toReject = rejecting
    if (toReject != null) {
        AlertDialog(
            onDismissRequest = { rejecting = null },
            title = { Text("Reject this reservation?") },
            text = {
                Text(
                    "${toReject.campsiteName.ifEmpty { "Unnamed campsite" }}, " +
                        ReservationQueries.describeDates(toReject.dates) +
                        ". The dates become free again.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    rejecting = null
                    repository.reject(toReject, ownerId) { result ->
                        status = result.fold({ "Reservation rejected" }, { it.message ?: "Could not reject" })
                    }
                }) { Text("Reject") }
            },
            dismissButton = { TextButton(onClick = { rejecting = null }) { Text("Keep") } },
        )
    }

    val toMessage = messaging
    if (toMessage != null) {
        AlertDialog(
            onDismissRequest = { messaging = null },
            title = { Text("Message the entrant") },
            text = {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    label = { Text("Message") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    messaging = null
                    MessageRepository().send(toMessage, ownerId, messageText) { result ->
                        status = result.fold({ "Message sent" }, { it.message ?: "Could not send" })
                    }
                }) { Text("Send") }
            },
            dismissButton = { TextButton(onClick = { messaging = null }) { Text("Cancel") } },
        )
    }
}

// No Firebase in here, so it can be previewed with sample data.
@Composable
fun OwnerReservationsContent(
    reservations: List<Reservation>,
    error: String?,
    today: Long,
    modifier: Modifier = Modifier,
    onReject: (Reservation) -> Unit = {},
    onMessage: (Reservation) -> Unit = {},
) {
    val active = remember(reservations, today) { ReservationQueries.active(reservations, today) }
    val past = remember(reservations, today) { ReservationQueries.past(reservations, today) }
    val cancelled = remember(reservations) { ReservationQueries.cancelled(reservations) }
    val rejected = remember(reservations) { ReservationQueries.rejected(reservations) }

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        if (error != null) {
            item {
                Text(
                    text = "Could not load reservations: $error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        item { SectionHeader("Active reservations (${active.size})") }
        if (active.isEmpty()) {
            item { EmptyMessage("No active reservations.") }
        } else {
            items(active) { reservation ->
                ReservationCard(
                    reservation = reservation,
                    canReject = ReservationQueries.canReject(reservation, today),
                    onReject = onReject,
                    onMessage = onMessage,
                )
            }
        }

        item { SectionHeader("Past reservations (${past.size})") }
        if (past.isEmpty()) {
            item { EmptyMessage("No past reservations.") }
        } else {
            items(past) { ReservationCard(reservation = it, onMessage = onMessage) }
        }

        item { SectionHeader("Cancelled reservations (${cancelled.size})") }
        if (cancelled.isEmpty()) {
            item { EmptyMessage("No cancelled reservations.") }
        } else {
            items(cancelled) { ReservationCard(reservation = it, onMessage = onMessage) }
        }

        item { SectionHeader("Rejected reservations (${rejected.size})") }
        if (rejected.isEmpty()) {
            item { EmptyMessage("No rejected reservations.") }
        } else {
            items(rejected) { ReservationCard(reservation = it) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun EmptyMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ReservationCard(
    reservation: Reservation,
    canReject: Boolean = false,
    onReject: (Reservation) -> Unit = {},
    onMessage: ((Reservation) -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = reservation.campsiteName.ifEmpty { "Unnamed campsite" },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = ReservationQueries.describeDates(reservation.dates),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (reservation.status == ReservationStatus.ACTIVE && onMessage != null || canReject) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onMessage != null && reservation.entrantId.isNotBlank()) {
                        OutlinedButton(onClick = { onMessage(reservation) }) { Text("Message") }
                    }
                    if (canReject) {
                        OutlinedButton(onClick = { onReject(reservation) }) { Text("Reject") }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OwnerReservationsPreview() {
    val today = ReservationQueries.todayUtcMidnight()
    val day = ReservationQueries.DAY_MILLIS
    val sample = listOf(
        Reservation(
            id = "r1",
            entrantId = "entrant-1",
            campsiteName = "Lakeside Site 4",
            dates = mutableListOf(today + 3 * day, today + 4 * day, today + 5 * day),
        ),
        Reservation(id = "r2", campsiteName = "Pine Ridge", dates = mutableListOf(today + 10 * day)),
        Reservation(
            id = "r3",
            campsiteName = "Lakeside Site 4",
            dates = mutableListOf(today - 9 * day, today - 8 * day),
        ),
        Reservation(
            id = "r4",
            campsiteName = "Pine Ridge",
            dates = mutableListOf(today + 20 * day),
            status = ReservationStatus.CANCELLED,
        ),
    )
    SpinachTheme {
        OwnerReservationsContent(reservations = sample, error = null, today = today)
    }
}
