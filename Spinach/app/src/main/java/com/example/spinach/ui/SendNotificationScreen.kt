package com.example.spinach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.spinach.Lottery
import com.example.spinach.LotteryRepository
import com.example.spinach.NotificationRepository
import com.example.spinach.NotificationRules

// US 02.10.01: owner writes one message and it goes to every entrant still waiting on a
// lottery for this campsite, except entrants who turned notifications off.
@Composable
fun SendNotificationScreen(
    campsiteId: String,
    campsiteName: String,
    senderId: String,
    modifier: Modifier = Modifier,
) {
    var lotteries by remember { mutableStateOf<List<Lottery>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    DisposableEffect(campsiteId) {
        val registration = LotteryRepository().listenForCampsite(campsiteId) { lotteries = it }
        onDispose { registration.remove() }
    }

    val waiting = NotificationRules.waitlistedPairs(lotteries).map { it.second }.distinct().size

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Notify the waitlist for $campsiteName", style = MaterialTheme.typography.titleMedium)
        Text("$waiting entrants are waiting for a draw.")
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Message") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = !sending,
            onClick = {
                sending = true
                status = ""
                NotificationRepository().sendToWaitlists(lotteries, senderId, text) { result ->
                    sending = false
                    status = result.fold(
                        { "Sent $it notifications" },
                        { it.message ?: "Could not send" },
                    )
                    if (result.isSuccess) text = ""
                }
            },
        ) {
            Text(if (sending) "Sending..." else "Send to waitlist")
        }
        Text(status)
    }
}