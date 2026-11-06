package com.example.spinach.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spinach.Campsite
import com.example.spinach.ui.theme.SpinachTheme

private sealed interface OwnerScreen {
    data object Home : OwnerScreen
    data object Reservations : OwnerScreen
    data object CreateLottery : OwnerScreen
    data class CampsiteMenu(val id: String, val name: String) : OwnerScreen
    data class BookedDates(val id: String, val name: String) : OwnerScreen
    data class Waitlist(val id: String, val name: String) : OwnerScreen
    data class Notify(val id: String, val name: String) : OwnerScreen
}

private fun backTarget(screen: OwnerScreen): OwnerScreen = when (screen) {
    is OwnerScreen.BookedDates -> OwnerScreen.CampsiteMenu(screen.id, screen.name)
    is OwnerScreen.Waitlist -> OwnerScreen.CampsiteMenu(screen.id, screen.name)
    is OwnerScreen.Notify -> OwnerScreen.CampsiteMenu(screen.id, screen.name)
    else -> OwnerScreen.Home
}

// The owner side of the app. campsites are the owner's own campsites.
@Composable
fun OwnerApp(ownerId: String, campsites: List<Campsite>, modifier: Modifier = Modifier) {
    var screen by remember { mutableStateOf<OwnerScreen>(OwnerScreen.Home) }
    val current = screen

    BackHandler(enabled = current != OwnerScreen.Home) { screen = backTarget(current) }

    Column(modifier = modifier.fillMaxSize()) {
        if (current != OwnerScreen.Home) {
            TextButton(
                onClick = { screen = backTarget(current) },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("Back")
            }
        }
        when (current) {
            OwnerScreen.Home -> OwnerHomeScreen(
                campsites = campsites,
                onOpenCampsite = { screen = OwnerScreen.CampsiteMenu(it.id, it.name) },
                onOpenReservations = { screen = OwnerScreen.Reservations },
                onCreateLottery = { screen = OwnerScreen.CreateLottery },
                modifier = Modifier.weight(1f),
            )
            OwnerScreen.Reservations -> OwnerReservationsScreen(
                ownerId = ownerId,
                modifier = Modifier.weight(1f),
            )
            OwnerScreen.CreateLottery -> CreateLotteryScreen(
                campsites = campsites,
                userId = ownerId,
                modifier = Modifier.weight(1f),
            )
            is OwnerScreen.CampsiteMenu -> CampsiteMenu(
                name = current.name,
                onBookedDates = { screen = OwnerScreen.BookedDates(current.id, current.name) },
                onWaitlist = { screen = OwnerScreen.Waitlist(current.id, current.name) },
                onNotify = { screen = OwnerScreen.Notify(current.id, current.name) },
                modifier = Modifier.weight(1f),
            )
            is OwnerScreen.BookedDates -> BookedDatesScreen(
                campsiteId = current.id,
                campsiteName = current.name,
                modifier = Modifier.weight(1f),
            )
            is OwnerScreen.Waitlist -> WaitlistScreen(
                campsiteId = current.id,
                campsiteName = current.name,
                modifier = Modifier.weight(1f),
            )
            is OwnerScreen.Notify -> SendNotificationScreen(
                campsiteId = current.id,
                campsiteName = current.name,
                senderId = ownerId,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun OwnerHomeScreen(
    campsites: List<Campsite>,
    onOpenCampsite: (Campsite) -> Unit,
    onOpenReservations: () -> Unit,
    onCreateLottery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("Owner", style = MaterialTheme.typography.titleLarge) }
        item {
            OutlinedButton(onClick = onOpenReservations, modifier = Modifier.fillMaxWidth()) {
                Text("Reservations")
            }
        }
        item {
            OutlinedButton(onClick = onCreateLottery, modifier = Modifier.fillMaxWidth()) {
                Text("Create a lottery")
            }
        }
        item { Text("My campsites", style = MaterialTheme.typography.titleMedium) }
        if (campsites.isEmpty()) {
            item { Text("No campsites yet.") }
        } else {
            items(campsites) { campsite ->
                OutlinedButton(
                    onClick = { onOpenCampsite(campsite) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(campsite.name.ifEmpty { "Unnamed campsite" })
                }
            }
        }
    }
}

@Composable
private fun CampsiteMenu(
    name: String,
    onBookedDates: () -> Unit,
    onWaitlist: () -> Unit,
    onNotify: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(name, style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = onBookedDates, modifier = Modifier.fillMaxWidth()) {
            Text("Booked dates")
        }
        OutlinedButton(onClick = onWaitlist, modifier = Modifier.fillMaxWidth()) {
            Text("Lottery waitlist")
        }
        OutlinedButton(onClick = onNotify, modifier = Modifier.fillMaxWidth()) {
            Text("Notify the waitlist")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OwnerAppPreview() {
    SpinachTheme {
        OwnerApp(
            ownerId = "preview",
            campsites = listOf(
                Campsite(id = "c1", name = "Lakeside Site 4"),
                Campsite(id = "c2", name = "Pine Ridge"),
            ),
        )
    }
}