package com.example.spinach.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import com.example.spinach.Lottery
import com.example.spinach.SearchScreen

private sealed interface EntrantScreen {
    data object Home : EntrantScreen
    data object Search : EntrantScreen
    data object Lotteries : EntrantScreen
    data object Reservations : EntrantScreen
    data class Reserve(val lottery: Lottery) : EntrantScreen
    data object Profile : EntrantScreen
    data object ScanQr : EntrantScreen
    data object Policy : EntrantScreen
}

// The entrant side of the app. entrantId must be the same id the rest of the app uses for this person.
@Composable
fun EntrantApp(entrantId: String, modifier: Modifier = Modifier) {
    var screen by remember { mutableStateOf<EntrantScreen>(EntrantScreen.Home) }
    val current = screen
    val back: () -> Unit = {
        screen = if (current is EntrantScreen.Reserve) EntrantScreen.Lotteries else EntrantScreen.Home
    }

    BackHandler(enabled = current != EntrantScreen.Home) { back() }

    Column(modifier = modifier.fillMaxSize()) {
        if (current != EntrantScreen.Home) {
            TextButton(onClick = back, modifier = Modifier.padding(start = 8.dp)) { Text("Back") }
        }
        when (current) {
            EntrantScreen.Home -> EntrantHome(onOpen = { screen = it }, modifier = Modifier.weight(1f))
            // US 01.01.01 to 01.01.06, 01.02.01 (Arnav)
            EntrantScreen.Search -> SearchScreen(modifier = Modifier.weight(1f))
            // US 01.02.02, 01.03.01, 01.03.02 (Karim)
            EntrantScreen.Lotteries -> EntrantLotteriesScreen(
                entrantId = entrantId,
                onReserve = { screen = EntrantScreen.Reserve(it) },
                modifier = Modifier.weight(1f),
            )
            // US 01.06.01, 01.07.01 (Karim)
            EntrantScreen.Reservations -> EntrantReservationsScreen(
                entrantId = entrantId,
                modifier = Modifier.weight(1f),
            )
            // US 01.04.01, 01.04.02, 01.05.01 (Karim)
            is EntrantScreen.Reserve -> ConfirmReservationScreen(
                entrantId = entrantId,
                lottery = current.lottery,
                modifier = Modifier.weight(1f),
            )
            // Stub for Anas: replace with his profile screen (UserProfileOptions) once it is on main
            EntrantScreen.Profile -> StubScreen(
                title = "My profile",
                body = "Coming soon: edit name, email and phone (US 01.07.03), delete profile (US 01.08.01), turn notifications off (US 01.10.01).",
                modifier = Modifier.weight(1f),
            )
            // Stub: scanning needs a camera library, and the QR format must match Ali's US 02.02.01
            EntrantScreen.ScanQr -> StubScreen(
                title = "Scan a QR code",
                body = "Coming soon: scan a campsite's promotional QR code to open its details (US 01.12.01).",
                modifier = Modifier.weight(1f),
            )
            EntrantScreen.Policy -> PolicyScreen(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun EntrantHome(onOpen: (EntrantScreen) -> Unit, modifier: Modifier = Modifier) {
    val entries = listOf<Pair<String, EntrantScreen>>(
        "Search campsites" to EntrantScreen.Search,
        "My lotteries" to EntrantScreen.Lotteries,
        "My reservations" to EntrantScreen.Reservations,
        "Scan a QR code" to EntrantScreen.ScanQr,
        "My profile" to EntrantScreen.Profile,
        "App policy and guidelines" to EntrantScreen.Policy,
    )
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Entrant", style = MaterialTheme.typography.titleLarge)
        for ((label, target) in entries) {
            OutlinedButton(onClick = { onOpen(target) }, modifier = Modifier.fillMaxWidth()) {
                Text(label)
            }
        }
    }
}

@Composable
private fun StubScreen(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body)
    }
}

// US 01.11.01
@Composable
private fun PolicyScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("App policy and guidelines", style = MaterialTheme.typography.titleLarge)
        Text("You are identified by this device. No username or password is needed.")
        Text("Joining a lottery puts you on its waiting list. Winners are drawn at random after registration closes, and everyone is notified of the result.")
        Text("If you win, choose your campsite and dates before the booking period ends, or decline so the spot can go to someone else.")
        Text("Read and follow each campsite's rules. Owners can reject reservations that break them.")
        Text("You can turn notifications off and delete your profile at any time from My profile.")
        Text("Admins can remove campsites, profiles and images that break these guidelines.")
    }
}
