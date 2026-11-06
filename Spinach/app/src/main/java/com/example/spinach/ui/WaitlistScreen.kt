package com.example.spinach.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import com.example.spinach.Lottery
import com.example.spinach.LotteryRepository
import com.example.spinach.ReservationQueries
import com.example.spinach.ui.theme.SpinachTheme

// US 02.09.01: entrants on the waitlist of each lottery for one campsite.
// After the draw, the lottery shows winners and not selected instead.
@Composable
fun WaitlistScreen(
    campsiteId: String,
    campsiteName: String,
    modifier: Modifier = Modifier,
) {
    var lotteries by remember { mutableStateOf<List<Lottery>>(emptyList()) }

    DisposableEffect(campsiteId) {
        val registration = LotteryRepository().listenForCampsite(campsiteId) { lotteries = it }
        onDispose { registration.remove() }
    }

    WaitlistContent(campsiteName = campsiteName, lotteries = lotteries, modifier = modifier)
}

// No Firebase in here, so it can be previewed with sample data.
@Composable
fun WaitlistContent(
    campsiteName: String,
    lotteries: List<Lottery>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        item {
            Text(
                text = "Waitlist for $campsiteName",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        if (lotteries.isEmpty()) {
            item {
                Text(
                    text = "No lottery for this campsite yet.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        lotteries.forEach { lottery ->
            item {
                Text(
                    text = "Registration " +
                            ReservationQueries.formatDay(lottery.registrationStart) + " to " +
                            ReservationQueries.formatDay(lottery.registrationEnd),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                )
            }
            if (lottery.drawn) {
                entrantSection("Winners (${lottery.winners.size})", lottery.winners, "Nobody was selected.")
                entrantSection("Not selected (${lottery.losers.size})", lottery.losers, "Nobody missed out.")
            } else {
                val capacity = lottery.waitlistCapacity
                val title = if (capacity == null) {
                    "Waitlist (${lottery.waitlist.size})"
                } else {
                    "Waitlist (${lottery.waitlist.size} of $capacity)"
                }
                entrantSection(title, lottery.waitlist, "Nobody has joined yet.")
            }
        }
    }
}

private fun LazyListScope.entrantSection(title: String, ids: List<String>, emptyText: String) {
    item {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        )
    }
    if (ids.isEmpty()) {
        item {
            Text(text = emptyText, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
    } else {
        items(ids) { id ->
            Text(text = id, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WaitlistPreview() {
    val day = ReservationQueries.DAY_MILLIS
    val today = ReservationQueries.todayUtcMidnight()
    val sample = listOf(
        Lottery(
            id = "l1",
            registrationStart = today,
            registrationEnd = today + 7 * day,
            waitlist = mutableListOf("entrant-1", "entrant-2", "entrant-3"),
            waitlistCapacity = 10,
        ),
        Lottery(
            id = "l2",
            registrationStart = today - 14 * day,
            registrationEnd = today - 7 * day,
            winners = mutableListOf("entrant-4"),
            losers = mutableListOf("entrant-5"),
            drawn = true,
        ),
    )
    SpinachTheme {
        WaitlistContent(campsiteName = "Lakeside Site 4", lotteries = sample)
    }
}