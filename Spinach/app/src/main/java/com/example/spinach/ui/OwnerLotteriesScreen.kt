
package com.example.spinach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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

/**
 * Shows the lotteries made by the signed-in owner.
 * The owner can select a lottery and edit it if the draw hasn't happened.
 *
 * US 02.04.06: Update lottery listing.
 *
 * Created with the help of ChatGPT, OpenAI,
 * "CMPUT 301 Update Lottery Listing", November 24, 2026.
 */
@Composable
fun OwnerLotteriesScreen(
    ownerId: String,
    campsites: List<Campsite>,
    modifier: Modifier = Modifier
) {
    val repository = remember { LotteryRepository() }

    var lotteries by remember { mutableStateOf<List<Lottery>>(emptyList()) }
    var selectedLottery by remember { mutableStateOf<Lottery?>(null) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    // Listen for lotteries belonging to this owner
    DisposableEffect(ownerId) {
        val listener = repository.listenForOwner(
            ownerId = ownerId,
            onResult = {lotteries = it
                loading = false
                error = ""
            },
            onError = {
                error = it.message ?: "Could not load lotteries"
                loading = false
            }
        )

        // Stop listening when this screen closes
        onDispose { listener.remove() }
    }

    val selected = selectedLottery

    if (selected != null) {
        Column(modifier = modifier) {

            Button(
                onClick = { selectedLottery = null },
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Back to my lotteries")
            }

            // Open the existing form with this lottery's saved details
            CreateLotteryScreen(campsites = campsites, userId = ownerId, existing = selected, onSaved = { selectedLottery = null }, modifier = Modifier.weight(1f)
            )
        }

    } else {
        LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "My lotteries",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (error.isNotBlank()) {
                item { Text(error) }
            }

            if (loading) {
                item { Text("Loading lotteries...") }
            } else if (lotteries.isEmpty() && error.isBlank()) {
                item { Text("No lotteries found.") }
            }

            items(lotteries, key = { it.id }) { lottery ->

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Show the names of the campsites in this lottery
                    val names = lottery.campsites().map { id ->
                        campsites.find { it.id == id }?.name ?: id
                    }

                    Text(
                        names.joinToString(", ").ifBlank { "Unnamed lottery" },
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text("Winners: ${lottery.winnerCount}")

                    Text(
                        "Waitlist capacity: " +
                                (lottery.waitlistCapacity?.toString() ?: "Unlimited")
                    )

                    Text(
                        if (lottery.drawn) "Status: Drawn"
                        else "Status: Not drawn"
                    )

                    // Editing is only available before the draw
                    Button(enabled = !lottery.drawn, onClick = { selectedLottery = lottery }, modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (lottery.drawn) "Editing unavailable"
                            else "Edit lottery"
                        )
                    }
                }
            }
        }
    }
}

