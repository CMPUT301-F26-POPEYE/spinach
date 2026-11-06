package com.example.spinach.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

// US 02.06.01: upcoming booked dates for one campsite, from ACTIVE reservations.
@Composable
fun BookedDatesScreen(
    campsiteId: String,
    campsiteName: String,
    modifier: Modifier = Modifier,
    repository: ReservationRepository = remember { ReservationRepository() },
) {
    var reservations by remember { mutableStateOf<List<Reservation>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(campsiteId) {
        val registration = repository.listenForCampsite(
            campsiteId = campsiteId,
            onResult = {
                reservations = it
                error = null
            },
            onError = { error = it.message ?: "Unknown error" },
        )
        onDispose { registration.remove() }
    }

    val today = ReservationQueries.todayUtcMidnight()
    BookedDatesContent(
        campsiteName = campsiteName,
        bookedDates = ReservationQueries.bookedDates(reservations, campsiteId, today),
        error = error,
        modifier = modifier,
    )
}

@Composable
fun BookedDatesContent(
    campsiteName: String,
    bookedDates: List<Long>,
    error: String?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        item {
            Text(
                text = "Booked dates for $campsiteName",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        if (error != null) {
            item {
                Text(
                    text = "Could not load booked dates: $error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        if (bookedDates.isEmpty()) {
            item {
                Text(
                    text = "No upcoming booked dates.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            items(bookedDates) { day ->
                Text(
                    text = ReservationQueries.formatDay(day),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BookedDatesPreview() {
    val today = ReservationQueries.todayUtcMidnight()
    val day = ReservationQueries.DAY_MILLIS
    SpinachTheme {
        BookedDatesContent(
            campsiteName = "Lakeside Site 4",
            bookedDates = listOf(today + 3 * day, today + 4 * day, today + 5 * day, today + 12 * day),
            error = null,
        )
    }
}