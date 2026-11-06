package com.example.spinach.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import com.example.spinach.EntrantReservationRepository
import com.example.spinach.Reservation
import com.example.spinach.ReservationQueries
import com.example.spinach.ui.theme.SpinachTheme



/**
 * EntrantReservationsScreen
 * Lists the entrant's active reservations and lets them cancel one.
 *
 * To cover:
 * - US 01.06.01 As an entrant, I want to view a list of my active reservations.
 * - US 01.07.01 As an entrant, I want to cancel my campsite reservation.
 *
 * @param entrantId The id of the signed-in entrant (the Firebase uid, like ownerId).
 */
@Composable
fun EntrantReservationsScreen(
    entrantId: String,
    modifier: Modifier = Modifier,
    repository: EntrantReservationRepository = remember { EntrantReservationRepository() },
) {
    var reservations by remember { mutableStateOf<List<Reservation>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(entrantId) {
        val registration = repository.listenForEntrant(
            entrantId = entrantId,
            onResult = {
                reservations = it
                error = null
            },
            onError = { error = it.message ?: "Unknown error" },
        )
        onDispose { registration.remove() }
    }

    EntrantReservationsContent(
        reservations = reservations,
        message = message,
        error = error,
        today = ReservationQueries.todayUtcMidnight(),
        onCancel = { reservation ->
            repository.cancel(reservation, entrantId, ReservationQueries.todayUtcMidnight()) { result ->
                result
                    .onSuccess {
                        message = "Reservation cancelled"
                        error = null
                    }
                    .onFailure {
                        error = it.message ?: "Could not cancel the reservation"
                        message = null
                    }
            }
        },
        modifier = modifier,
    )
}


@Composable
fun EntrantReservationsContent(
    reservations: List<Reservation>,
    message: String?,
    error: String?,
    today: Long,
    onCancel: (Reservation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = remember(reservations, today) { ReservationQueries.active(reservations, today) }
    // the reservation the entrant tapped Cancel on, while we ask them to confirm
    var toCancel by remember { mutableStateOf<Reservation?>(null) }

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        if (error != null) {
            item {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        if (message != null) {
            item {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        item {
            Text(
                text = "My reservations (${active.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        if (active.isEmpty()) {
            item {
                Text(
                    text = "You have no active reservations.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            items(active) { reservation ->
                MyReservationCard(reservation, onCancel = { toCancel = reservation })
            }
        }
    }

    toCancel?.let { reservation ->
        AlertDialog(
            onDismissRequest = { toCancel = null },
            title = { Text("Cancel reservation?") },
            text = {
                Text("Cancel your stay at ${reservation.campsiteName.ifEmpty { "Unnamed campsite" }}?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCancel(reservation)
                        toCancel = null
                    },
                ) {
                    Text("Cancel reservation")
                }
            },
            dismissButton = {
                TextButton(onClick = { toCancel = null }) {
                    Text("Keep it")
                }
            },
        )
    }
}

@Composable
private fun MyReservationCard(reservation: Reservation, onCancel: () -> Unit) {
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
            TextButton(onClick = onCancel) {
                Text("Cancel reservation")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EntrantReservationsPreview() {
    val today = ReservationQueries.todayUtcMidnight()
    val day = ReservationQueries.DAY_MILLIS
    val sample = listOf(
        Reservation(
            campsiteName = "Lakeside Site 4",
            dates = mutableListOf(today + 3 * day, today + 4 * day, today + 5 * day),
        ),
        Reservation(campsiteName = "Pine Ridge", dates = mutableListOf(today + 10 * day)),
    )
    SpinachTheme {
        EntrantReservationsContent(
            reservations = sample,
            message = null,
            error = null,
            today = today,
            onCancel = {},
        )
    }
}