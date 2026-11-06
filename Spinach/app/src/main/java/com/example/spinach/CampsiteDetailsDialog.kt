package com.example.spinach

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch


/**
 * CampsiteDetailsDialog
 * A popup with all the details of a campsite and, when the campsite has an active lottery,
 * its lottery information. The button at the bottom joins the lottery's waiting list,
 * or leaves it if the entrant is already on it.
 *
 * To cover:
 * - US 01.01.05 As an entrant, I want to see details of a campsite.
 * - US 01.01.06 As an entrant, I want to join an active lottery from the campsite details screen.
 * - US 01.02.01 As an entrant, I want to join the waiting list for a campsite lottery.
 * - US 01.02.02 As an entrant, I want to leave the waiting list for a campsite lottery.
 *
 * @param campsite The campsite to show.
 * @param onDismiss Called when the popup should close.
 */
@Composable
fun CampsiteDetailsDialog(campsite: Campsite, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val entrantId = remember { deviceEntrantId(context) }

    var lottery by remember { mutableStateOf<Lottery?>(null) }
    var checked by remember { mutableStateOf(false) } // finished looking for an active lottery
    var joined by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(campsite.id) {
        try {
            lottery = getActiveLottery(campsite.id)
            joined = lottery?.waitlist?.contains(entrantId) == true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = e.message
        }
        checked = true
    }

    val l = lottery

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(campsite.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                detailLines(campsite).forEach { Text(it) }
                if (l != null) lotteryLines(l).forEach { Text(it) }
                message?.let {
                    Text("")
                    Text(it)
                }
            }
        },
        confirmButton = {
            // US 01.01.06: the button only shows when an active lottery includes this campsite.
            // It joins the waiting list, or leaves it when the entrant is already on it.
            if (l != null && joined) {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                leaveLottery(l.id, entrantId)
                                joined = false
                                message = "You left the lottery's waiting list"
                                // refresh so the waiting list count no longer includes this entrant
                                runCatching { getActiveLottery(campsite.id) }.getOrNull()?.let { lottery = it }
                            } catch (e: Exception) {
                                message = e.message ?: "Could not leave the lottery"
                            }
                        }
                    },
                ) {
                    Text("Leave lottery")
                }
            } else if (l != null) {
                Button(
                    enabled = canJoin(joined, l),
                    onClick = {
                        scope.launch {
                            try {
                                joinLottery(l.id, entrantId)
                                joined = true
                                message = "You joined the lottery's waiting list!"
                                // refresh so the waiting list count includes this entrant
                                runCatching { getActiveLottery(campsite.id) }.getOrNull()?.let { lottery = it }
                            } catch (e: Exception) {
                                message = e.message ?: "Could not join the lottery"
                            }
                        }
                    },
                ) {
                    Text(joinButtonLabel(checked, joined, l))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}