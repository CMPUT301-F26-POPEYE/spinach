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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
    data object SentNotifications : OwnerScreen
    data object RegisterCampsite : OwnerScreen
    data class EditCampsite(val id: String, val name: String) : OwnerScreen
    data class CampsiteMenu(val id: String, val name: String) : OwnerScreen
    data class BookedDates(val id: String, val name: String) : OwnerScreen
    data class Waitlist(val id: String, val name: String) : OwnerScreen
    data class Notify(val id: String, val name: String) : OwnerScreen
    data class Rules(val id: String, val name: String) : OwnerScreen
}

private fun backTarget(screen: OwnerScreen): OwnerScreen = when (screen) {
    is OwnerScreen.BookedDates -> OwnerScreen.CampsiteMenu(screen.id, screen.name)
    is OwnerScreen.Waitlist -> OwnerScreen.CampsiteMenu(screen.id, screen.name)
    is OwnerScreen.Notify -> OwnerScreen.CampsiteMenu(screen.id, screen.name)
    is OwnerScreen.Rules -> OwnerScreen.CampsiteMenu(screen.id, screen.name)
    is OwnerScreen.EditCampsite -> OwnerScreen.CampsiteMenu(screen.id, screen.name)
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
                onSentNotifications = { screen = OwnerScreen.SentNotifications },
                onRegisterCampsite = { screen = OwnerScreen.RegisterCampsite },
                modifier = Modifier.weight(1f),
            )
            OwnerScreen.Reservations -> OwnerReservationsScreen(
                ownerId = ownerId,
                modifier = Modifier.weight(1f),
            )
            OwnerScreen.SentNotifications -> SentNotificationsScreen(
                senderId = ownerId,
                modifier = Modifier.weight(1f),
            )
            OwnerScreen.CreateLottery -> CreateLotteryScreen(
                campsites = campsites,
                userId = ownerId,
                modifier = Modifier.weight(1f),
            )
            // The new campsite shows up in the list straight away through ownerCampsites' listener.
            OwnerScreen.RegisterCampsite -> CampsiteFormScreen(
                existing = null,
                onSaved = { screen = OwnerScreen.Home },
                modifier = Modifier.weight(1f),
            )
            is OwnerScreen.EditCampsite -> {
                // campsites only holds this owner's campsites, so another owner's id finds nothing.
                val campsite = campsites.find { it.id == current.id }
                if (campsite == null) {
                    NotYourCampsite(Modifier.weight(1f))
                } else {
                    CampsiteFormScreen(
                        existing = campsite,
                        onSaved = { screen = OwnerScreen.CampsiteMenu(current.id, current.name) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            is OwnerScreen.CampsiteMenu -> CampsiteMenu(
                campsite = campsites.find { it.id == current.id },
                onEdit = { screen = OwnerScreen.EditCampsite(current.id, current.name) },
                onBookedDates = { screen = OwnerScreen.BookedDates(current.id, current.name) },
                onWaitlist = { screen = OwnerScreen.Waitlist(current.id, current.name) },
                onNotify = { screen = OwnerScreen.Notify(current.id, current.name) },
                onRules = { screen = OwnerScreen.Rules(current.id, current.name) },
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
            is OwnerScreen.Rules -> CampsiteRulesScreen(
                campsiteId = current.id,
                campsiteName = current.name,
                ownerId = ownerId,
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
    onSentNotifications: () -> Unit,
    onRegisterCampsite: () -> Unit,
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
        item {
            OutlinedButton(onClick = onSentNotifications, modifier = Modifier.fillMaxWidth()) {
                Text("Sent notifications")
            }
        }
        item { Text("My campsites", style = MaterialTheme.typography.titleMedium) }
        item {
            Button(onClick = onRegisterCampsite, modifier = Modifier.fillMaxWidth()) {
                Text("Register a campsite")
            }
        }
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

/**
 * CampsiteMenu
 * The owner view of one of their campsites: every stored detail, a button to edit them,
 * and the buttons for booked dates, the lottery waitlist and notifications.
 *
 * To cover:
 * - US 02.01.04 As a campsite owner, I want to view details of my campsite.
 *
 * @param campsite The campsite to show, or null when it is not one of this owner's campsites.
 * @param onEdit Called when the owner taps Edit details.
 * @param onBookedDates Called when the owner taps Booked dates.
 * @param onWaitlist Called when the owner taps Lottery waitlist.
 * @param onNotify Called when the owner taps Notify the waitlist.
 * @param modifier Modifier for the screen's outer column.
 */
@Composable
private fun CampsiteMenu(
    campsite: Campsite?,
    onEdit: () -> Unit,
    onBookedDates: () -> Unit,
    onWaitlist: () -> Unit,
    onNotify: () -> Unit,
    onRules: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // campsites only holds this owner's campsites, so another owner's campsite is not found.
    if (campsite == null) {
        NotYourCampsite(modifier)
        return
    }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(campsite.name.ifEmpty { "Unnamed campsite" }, style = MaterialTheme.typography.titleLarge)
        ownerDetailLines(campsite).forEach { Text(it) }
        Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
            Text("Edit details")
        }
        OutlinedButton(onClick = onBookedDates, modifier = Modifier.fillMaxWidth()) {
            Text("Booked dates")
        }
        OutlinedButton(onClick = onWaitlist, modifier = Modifier.fillMaxWidth()) {
            Text("Lottery waitlist")
        }
        OutlinedButton(onClick = onNotify, modifier = Modifier.fillMaxWidth()) {
            Text("Notify the waitlist")
        }
        OutlinedButton(onClick = onRules, modifier = Modifier.fillMaxWidth()) {
            Text("Campsite rules")
        }
    }
}

/**
 * Shown instead of the owner view or the edit form when the campsite is not one of the
 * current owner's campsites (US 02.01.03, US 02.01.04).
 *
 * @param modifier Modifier for the message.
 */
@Composable
private fun NotYourCampsite(modifier: Modifier = Modifier) {
    Text(
        "You can only view and edit campsites you own.",
        modifier = modifier.padding(16.dp),
    )
}

/**
 * The detail lines of the owner view (US 02.01.04).
 *
 * @param campsite The campsite to describe.
 * @return Its location, description, maximum occupancy, map coordinates, tags, image and QR code.
 */
fun ownerDetailLines(campsite: Campsite): List<String> = buildList {
    add("Location: ${campsite.location}")
    add("Description: ${campsite.description}")
    add("Max occupancy: ${campsite.maxOccupancy}")
    add("Map: ${campsite.mapCoordinates.latitude}, ${campsite.mapCoordinates.longitude}")
    add("Tags: " + campsite.tags.joinToString(", ").ifEmpty { "None" })
    add("Image: " + campsite.images.joinToString(", ").ifEmpty { "None" })
    add("QR code: " + campsite.qrCode.ifEmpty { "Not generated yet" })
}

@Preview(showBackground = true)
@Composable
private fun OwnerAppPreview() {
    SpinachTheme {
        OwnerApp(
            ownerId = "preview",
            campsites = listOf(
                Campsite(id = "c1", name = "Lakeside Site 4", location = "Jasper, AB", maxOccupancy = 6),
                Campsite(id = "c2", name = "Pine Ridge"),
            ),
        )
    }
}