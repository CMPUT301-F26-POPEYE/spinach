package com.example.spinach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.spinach.Campsite
import com.example.spinach.Lottery
import com.example.spinach.LotteryRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

//this code was partly produced by claude with the promt, "how would i go about adding these" nov 24 2026

/**
 * SharedLotteriesScreen
 * Lists the lotteries other owners have opened up, and lets this owner add their own
 * campsites to them. Each lottery shows its registration and booking periods, how many
 * campsites and owners it has, and an "Add" button for each of this owner's campsites
 * that isn't in it yet.
 *
 * To cover:
 * - As a campsite owner, I want be able to list multiple campsites with other campsite
 *   owners in a lottery for a certain period.
 *
 * @param campsites This owner's campsites.
 * @param userId The signed-in owner's ID.
 * @param modifier Modifier for the screen's list.
 */
@Composable
fun SharedLotteriesScreen(campsites: List<Campsite>, userId: String, modifier: Modifier = Modifier) {
    val repository = remember { LotteryRepository() }
    var lotteries by remember { mutableStateOf<List<Lottery>>(emptyList()) }
    var message by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        val registration = repository.listenForSharedLotteries { lotteries = it }
        onDispose { registration.remove() }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("Shared lotteries", style = MaterialTheme.typography.titleLarge) }
        if (message.isNotEmpty()) item { Text(message) }
        if (lotteries.isEmpty()) item { Text("No lotteries are open to other owners right now.") }
        items(lotteries, key = { it.id }) { lottery ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Registration: ${formatDay(lottery.registrationStart)} to ${formatDay(lottery.registrationEnd)}")
                    Text("Booking: ${formatDay(lottery.bookingStart)} to ${formatDay(lottery.bookingEnd)}")
                    Text("${lottery.campsites().size} campsite(s) from ${lottery.owners().size} owner(s)")
                    val addable = campsites.filter { it.id !in lottery.campsites() }
                    if (addable.isEmpty()) Text("All your campsites are already in this lottery.")
                    addable.forEach { campsite ->
                        val name = campsite.name.ifEmpty { "Unnamed campsite" }
                        OutlinedButton(
                            onClick = {
                                repository.addCampsite(lottery.id, campsite.id, userId) { result ->
                                    message = result.fold({ "Added $name" }, { it.message ?: "Could not add" })
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Add $name")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Formats a lottery date. Lottery dates are stored at UTC midnight, so they are shown in
 * UTC to keep the same day in every time zone.
 *
 * @param millis The date in epoch millis.
 * @return The date, for example "6 Nov 2026".
 */
private fun formatDay(millis: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(millis))
