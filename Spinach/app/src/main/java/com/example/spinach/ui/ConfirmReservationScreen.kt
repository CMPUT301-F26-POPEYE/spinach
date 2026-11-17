package com.example.spinach.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spinach.CampsiteRepository
import com.example.spinach.EntrantReservationRepository
import com.example.spinach.EntrantReservationRules
import com.example.spinach.Lottery
import com.example.spinach.Reservation
import com.example.spinach.ReservationQueries
import com.example.spinach.ReservationRepository
import com.example.spinach.ui.theme.SpinachTheme


/**
 * ConfirmReservationScreen
 * Lets a lottery winner pick a campsite and dates, accept the rules and confirm the reservation.
 * Dates that are already booked (US 02.06.01) cannot be picked. After saving, the screen shows
 * the confirmation.
 *
 * To cover:
 * - US 01.05.01 As an entrant, I want to receive confirmation of my campsite reservation.
 *
 * @param entrantId The id of the signed-in entrant (the Firebase uid, like ownerId).
 * @param lottery The lottery the entrant won.
 */
@Composable
fun ConfirmReservationScreen(
    entrantId: String,
    lottery: Lottery,
    modifier: Modifier = Modifier,
    repository: EntrantReservationRepository = remember { EntrantReservationRepository() },
    ownerReservations: ReservationRepository = remember { ReservationRepository() },
    campsiteRepository: CampsiteRepository = remember { CampsiteRepository() },
) {
    val campsiteIds = remember(lottery) { lottery.campsites().filter { it.isNotBlank() } }
    // every day of the booking period
    val days = remember(lottery) {
        if (lottery.bookingEnd < lottery.bookingStart) {
            emptyList<Long>()
        } else {
            (lottery.bookingStart..lottery.bookingEnd step ReservationQueries.DAY_MILLIS).toList()
        }
    }

    var names by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var campsiteId by remember { mutableStateOf(campsiteIds.firstOrNull().orEmpty()) }
    var booked by remember { mutableStateOf<List<Long>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var rulesAcknowledged by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<String?>(null) }

    // the reservation stores the campsite name, so load the names of the lottery's campsites
    LaunchedEffect(lottery.id) {
        names = campsiteIds.associateWith { id ->
            try {
                campsiteRepository.getCampsite(id)?.name.orEmpty()
            } catch (e: Exception) {
                ""
            }
        }
    }

    // US 02.06.01: dates of the chosen campsite that other reservations already took
    DisposableEffect(campsiteId) {
        val registration = ownerReservations.listenForCampsite(
            campsiteId = campsiteId,
            onResult = {
                booked = ReservationQueries.bookedDates(it, campsiteId, ReservationQueries.todayUtcMidnight())
            },
            onError = { error = it.message ?: "Unknown error" },
        )
        onDispose { registration.remove() }
    }

    ConfirmReservationContent(
        campsiteIds = campsiteIds,
        names = names,
        selectedCampsiteId = campsiteId,
        onCampsiteSelected = {
            campsiteId = it
            // the dates picked for another campsite may not be free here
            selected = emptySet()
        },
        days = days,
        bookedDates = booked,
        selectedDays = selected,
        onToggleDay = { day -> selected = if (day in selected) selected - day else selected + day },
        rulesAcknowledged = rulesAcknowledged,
        onRulesChange = { rulesAcknowledged = it },
        error = error,
        confirmation = confirmation,
        onConfirm = {
            val reservation = Reservation(
                campsiteId = campsiteId,
                campsiteName = names[campsiteId].orEmpty().ifEmpty { "Unnamed campsite" },
                dates = selected.sorted().toMutableList(),
                rulesAcknowledged = rulesAcknowledged,
            )
            repository.confirm(reservation, lottery, entrantId, System.currentTimeMillis()) { result ->
                result
                    .onSuccess {
                        confirmation = EntrantReservationRules.confirmationMessage(reservation)
                        error = null
                    }
                    .onFailure {
                        error = it.message ?: "Could not confirm the reservation"
                        confirmation = null
                    }
            }
        },
        modifier = modifier,
    )
}


@Composable
fun ConfirmReservationContent(
    campsiteIds: List<String>,
    names: Map<String, String>,
    selectedCampsiteId: String,
    onCampsiteSelected: (String) -> Unit,
    days: List<Long>,
    bookedDates: List<Long>,
    selectedDays: Set<Long>,
    onToggleDay: (Long) -> Unit,
    rulesAcknowledged: Boolean,
    onRulesChange: (Boolean) -> Unit,
    error: String?,
    confirmation: String?,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        item {
            Text(
                text = "Reserve your campsite",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
        }

        if (confirmation != null) {
            // US 01.05.01: the confirmation
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = EntrantReservationRules.CONFIRMATION_TITLE,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(text = confirmation, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else {
            // with several campsites in one lottery the winner picks one
            if (campsiteIds.size > 1) {
                item {
                    Text(
                        text = "Campsite",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(campsiteIds) { id ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        RadioButton(
                            selected = id == selectedCampsiteId,
                            onClick = { onCampsiteSelected(id) },
                        )
                        Text(names[id].orEmpty().ifEmpty { "Unnamed campsite" })
                    }
                }
            }

            item {
                Text(
                    text = "Pick your dates",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (days.isEmpty()) {
                item {
                    Text(
                        text = "This lottery has no booking dates.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(days) { day ->
                val isBooked = day in bookedDates
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Checkbox(
                        checked = day in selectedDays,
                        onCheckedChange = { onToggleDay(day) },
                        enabled = !isBooked,
                    )
                    Text(ReservationQueries.formatDay(day) + if (isBooked) " (booked)" else "")
                }
            }

            // US 02.11.01: the entrant must confirm they read the rules
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Checkbox(checked = rulesAcknowledged, onCheckedChange = onRulesChange)
                    Text("I have read the campsite rules and policies")
                }
            }

            if (error != null) {
                item {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            item {
                Button(
                    onClick = onConfirm,
                    enabled = selectedDays.isNotEmpty() && rulesAcknowledged,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text("Confirm reservation")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConfirmReservationPreview() {
    val day = ReservationQueries.DAY_MILLIS
    val today = ReservationQueries.todayUtcMidnight()
    val days = (0L..4L).map { today + it * day }
    SpinachTheme {
        ConfirmReservationContent(
            campsiteIds = listOf("c1", "c2"),
            names = mapOf("c1" to "Lakeside Site 4", "c2" to "Pine Ridge"),
            selectedCampsiteId = "c1",
            onCampsiteSelected = {},
            days = days,
            bookedDates = listOf(days[1]),
            selectedDays = setOf(days[2], days[3]),
            onToggleDay = {},
            rulesAcknowledged = true,
            error = null,
            confirmation = null,
            onRulesChange = {},
            onConfirm = {},
        )
    }
}