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
import com.example.spinach.Reservation
import com.example.spinach.ReservationQueries
import com.example.spinach.ReservationRepository
import com.example.spinach.ui.theme.SpinachTheme

// US 02.07.01 and US 02.07.02. ownerId is passed in until profiles exist (US 01.09.01).
@Composable
fun OwnerReservationsScreen(
    ownerId: String,
    modifier: Modifier = Modifier,
    repository: ReservationRepository = remember { ReservationRepository() },
) {
    var reservations by remember { mutableStateOf<List<Reservation>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

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

    OwnerReservationsContent(
        reservations = reservations,
        error = error,
        today = ReservationQueries.todayUtcMidnight(),
        modifier = modifier,
    )
}

// No Firebase in here, so it can be previewed with sample data.
@Composable
fun OwnerReservationsContent(
    reservations: List<Reservation>,
    error: String?,
    today: Long,
    modifier: Modifier = Modifier,
) {
    val active = remember(reservations, today) { ReservationQueries.active(reservations, today) }
    val past = remember(reservations, today) { ReservationQueries.past(reservations, today) }

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
            items(active) { ReservationCard(it) }
        }

        item { SectionHeader("Past reservations (${past.size})") }
        if (past.isEmpty()) {
            item { EmptyMessage("No past reservations.") }
        } else {
            items(past) { ReservationCard(it) }
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
private fun ReservationCard(reservation: Reservation) {
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
            campsiteName = "Lakeside Site 4",
            dates = mutableListOf(today + 3 * day, today + 4 * day, today + 5 * day),
        ),
        Reservation(campsiteName = "Pine Ridge", dates = mutableListOf(today + 10 * day)),
        Reservation(
            campsiteName = "Lakeside Site 4",
            dates = mutableListOf(today - 9 * day, today - 8 * day),
        ),
    )
    SpinachTheme {
        OwnerReservationsContent(reservations = sample, error = null, today = today)
    }
}