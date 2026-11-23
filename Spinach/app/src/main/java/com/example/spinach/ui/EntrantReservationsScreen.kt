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
 * Displays the entrant's active and past reservations.
 * Loads reservations from Firebase and allows active reservartions to be cancelled.
 *
 * Covers:
 * - US 01.06.01: View active reservations.
 * - US 01.06.02: View past reservations.
 * - US 01.07.01: Cancel a reservation.
 *
 * @param entrantId The ID of the signed-in entrant.
 * @param modifier Modifier used for the screen layout.
 * @param repository Repository used to load and cancel reservations.
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

/**
 * Displays active and past reservations in separate sections.
 * Past reservations are filtered to exclude cancelled and rejected bookings.
 *
 * @param reservations List of reservations loaded for the entrant.
 * @param message Success message to display, if any.
 * @param error Error message to display, if any.
 * @param today Current day used to separate active and past reservations.
 * @param onCancel Called when the entrant chooses to cancel a reservation.
 * @param modifier Modifier used for the scren layout.
 */
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

    //past reservations
    val past = remember(reservations, today) {
        ReservationQueries.past(reservations, today)
    }
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


            item {//US 01.06.02show entrants past reservations
                Text(text = "Past reservations (${past.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp),
                )
            }

            if (past.isEmpty()) {//show message if no past resrervations
                item {
                    Text(
                        text = "You have no past reservations.", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp,
                        ),
                    )
                }
            } else {//display each past res using card
                items(past) { reservation -> PastReservationCard(reservation)
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

/**
 * Displays a past reservation with its campsite name and reserved dates.
 * Past reservations are read-only and cannot be cancelled.
 *
 * @param reservation The past reservation to display.
 */
//This function was generated with help of CHATGPT, OpenAI, "CMPUT 301 past reservations implimentation", November 18, 2026
@Composable
private fun PastReservationCard(reservation: Reservation) {

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            Text(//show campsite name
                text = reservation.campsiteName.ifEmpty { "Unnamed campsite"
                }, style = MaterialTheme.typography.titleSmall,
            )

            Text(//show dates reserved
                text = ReservationQueries.describeDates(reservation.dates), style = MaterialTheme.typography.bodyMedium,
            )
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