package com.example.spinach.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spinach.EntrantLotteryRepository
import com.example.spinach.EntrantLotteryRules
import com.example.spinach.Lottery
import com.example.spinach.LotteryResult
import com.example.spinach.ReservationQueries
import com.example.spinach.ui.theme.SpinachTheme


/**
 * EntrantLotteriesScreen
 * Shows the lotteries the entrant is waiting in (with a Leave button) and the results of
 * lotteries that were drawn (won or lost). A winner can go on to reserve.
 *
 * To cover:
 * - US 01.02.02 As an entrant, I want to leave the waiting list for a campsite lottery.
 * - US 01.03.01 As an entrant, I want to receive a notification when I win the campsite lottery.
 * - US 01.03.02 As an entrant, I want to receive a notification when I lose the campsite lottery.
 *
 * @param entrantId The id of the signed-in entrant (the Firebase uid, like ownerId).
 * @param onReserve Called when a winner taps Reserve, with the lottery they won.
 */
@Composable
fun EntrantLotteriesScreen(
    entrantId: String,
    onReserve: (Lottery) -> Unit,
    modifier: Modifier = Modifier,
    repository: EntrantLotteryRepository = remember { EntrantLotteryRepository() },
) {
    var waiting by remember { mutableStateOf<List<Lottery>>(emptyList()) }
    var won by remember { mutableStateOf<List<Lottery>>(emptyList()) }
    var lost by remember { mutableStateOf<List<Lottery>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(entrantId) {
        val waitingRegistration = repository.listenWaitingFor(
            entrantId = entrantId,
            onResult = { waiting = it },
            onError = { error = it.message ?: "Unknown error" },
        )
        val wonRegistration = repository.listenWonBy(
            entrantId = entrantId,
            onResult = { won = it },
            onError = { error = it.message ?: "Unknown error" },
        )
        val lostRegistration = repository.listenLostBy(
            entrantId = entrantId,
            onResult = { lost = it },
            onError = { error = it.message ?: "Unknown error" },
        )
        onDispose {
            waitingRegistration.remove()
            wonRegistration.remove()
            lostRegistration.remove()
        }
    }

    EntrantLotteriesContent(
        waiting = waiting,
        won = won,
        lost = lost,
        message = message,
        error = error,
        onLeave = { lottery ->
            repository.leaveWaitlist(lottery, entrantId) { result ->
                result
                    .onSuccess {
                        message = "You left the waiting list"
                        error = null
                    }
                    .onFailure {
                        error = it.message ?: "Could not leave the waiting list"
                        message = null
                    }
            }
        },
        onReserve = onReserve,
        modifier = modifier,
    )
}

@Composable
fun EntrantLotteriesContent(
    waiting: List<Lottery>,
    won: List<Lottery>,
    lost: List<Lottery>,
    message: String?,
    error: String?,
    onLeave: (Lottery) -> Unit,
    onReserve: (Lottery) -> Unit,
    modifier: Modifier = Modifier,
) {
    // the lottery the entrant tapped Leave on, while we ask them to confirm
    var toLeave by remember { mutableStateOf<Lottery?>(null) }

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        if (error != null) {
            item {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        if (message != null) {
            item {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        // US 01.02.02
        item {
            Text(
                text = "Waiting for the draw (${waiting.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        if (waiting.isEmpty()) {
            item {
                Text(
                    text = "You are not on any waiting list.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            items(waiting) { lottery ->
                WaitingLotteryCard(lottery, onLeave = { toLeave = lottery })
            }
        }

        // US 01.03.01 and US 01.03.02
        item {
            Text(
                text = "Results (${won.size + lost.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        if (won.isEmpty() && lost.isEmpty()) {
            item {
                Text(
                    text = "No results yet.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        items(won) { lottery ->
            ResultCard(lottery, LotteryResult.WON, onReserve = { onReserve(lottery) })
        }
        items(lost) { lottery ->
            ResultCard(lottery, LotteryResult.LOST, onReserve = null)
        }
    }

    toLeave?.let { lottery ->
        AlertDialog(
            onDismissRequest = { toLeave = null },
            title = { Text("Leave waiting list?") },
            text = { Text("You will no longer be in this lottery's draw.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onLeave(lottery)
                        toLeave = null
                    },
                ) {
                    Text("Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { toLeave = null }) {
                    Text("Stay")
                }
            },
        )
    }
}

@Composable
private fun WaitingLotteryCard(lottery: Lottery, onLeave: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Registration " +
                        ReservationQueries.formatDay(lottery.registrationStart) + " to " +
                        ReservationQueries.formatDay(lottery.registrationEnd),
                style = MaterialTheme.typography.titleSmall,
            )
            TextButton(onClick = onLeave) {
                Text("Leave waiting list")
            }
        }
    }
}

// onReserve is null for a lost lottery, so there is no button
@Composable
private fun ResultCard(lottery: Lottery, result: LotteryResult, onReserve: (() -> Unit)?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = EntrantLotteryRules.resultTitle(result).orEmpty(),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = EntrantLotteryRules.resultMessage(result).orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (result == LotteryResult.WON) {
                Text(
                    text = "Booking " +
                            ReservationQueries.formatDay(lottery.bookingStart) + " to " +
                            ReservationQueries.formatDay(lottery.bookingEnd),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (onReserve != null) {
                Button(onClick = onReserve, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Reserve")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EntrantLotteriesPreview() {
    val day = ReservationQueries.DAY_MILLIS
    val today = ReservationQueries.todayUtcMidnight()
    SpinachTheme {
        EntrantLotteriesContent(
            waiting = listOf(
                Lottery(
                    id = "l1",
                    registrationStart = today,
                    registrationEnd = today + 7 * day,
                ),
            ),
            won = listOf(
                Lottery(
                    id = "l2",
                    bookingStart = today + 10 * day,
                    bookingEnd = today + 20 * day,
                    drawn = true,
                ),
            ),
            lost = listOf(Lottery(id = "l3", drawn = true)),
            message = null,
            error = null,
            onLeave = {},
            onReserve = {},
        )
    }
}