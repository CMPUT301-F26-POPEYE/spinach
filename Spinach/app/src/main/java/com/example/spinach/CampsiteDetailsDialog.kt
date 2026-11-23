package com.example.spinach

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.fillMaxWidth


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
 * - US 01.13.01: Rate a campsite.
 * - US 01.13.02: Write a campsite review.
 *
 *  Rating and review functionality developed with the help of ChatGPT, OpenAI, "CMPUT 301 Campsite Ratings and Reviews", November 23, 2026.
 *
 * @param campsite The campsite to show.
 * @param onDismiss Called when the popup should close.
 */

//made use of Claude to help with the popup window by asking the prompt 'How do i display a popup window and also have it dismiss after a certain action'
@Composable
fun CampsiteDetailsDialog(campsite: Campsite, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val entrantId = remember { deviceEntrantId(context) }

    var lottery by remember { mutableStateOf<Lottery?>(null) }
    var checked by remember { mutableStateOf(false) } // finished looking for an active lottery
    var joined by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // US 01.13.01: Rating state and Firebase repository
    val reviewRepository = remember { ReviewRepository() }
    val campsiteRepository = remember { CampsiteRepository() }


    // Keep track of the rating selected by the entrant
    var myRating by remember(campsite.id) {
        mutableStateOf(0)
    }

    // Keep a copy of the campsite so we can update its average rating
    var currentCampsite by remember(campsite.id) {
        mutableStateOf(campsite)
    }

    // Prevent multiple saves while Firebase is updating
    var savingRating by remember {
        mutableStateOf(false)
    }

    // Keep track of the review the entrant is writing
    var reviewText by remember(campsite.id) {
        mutableStateOf("")
    }

    // Stop multiple review submissions
    var savingReview by remember {
        mutableStateOf(false)
    }


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

    // US 01.13.01: Load the entrant's previous rating

    LaunchedEffect(campsite.id, entrantId) {
        try {
            // Load the entrant's saved rating
            myRating = reviewRepository.getMyRating(
                campsite.id,
                entrantId
            )

            // Load their previous review so they can edit it
            reviewText = reviewRepository.getMyReview(
                campsite.id,
                entrantId
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = e.message ?: "Could not load rating or review"
        }
    }


    val l = lottery

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(campsite.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // Show updated ratings and reviews after saving
                detailLines(currentCampsite).forEach { Text(it) }

                // US 01.13.01: Rating section
                Text("")
                Text("Rate this campsite")

                Row {
                    (1..5).forEach { star ->
                        TextButton(
                            onClick = { myRating = star }
                        ) {
                            Text(
                                if (star <= myRating) "★" else "☆"
                            )
                        }
                    }
                }

                Text(
                    if (myRating == 0) {
                        "Select a rating from 1 to 5"
                    } else {
                        "Your rating: $myRating / 5"
                    }
                )

                Button(
                    enabled = myRating in 1..5 && !savingRating,
                    onClick = {
                        savingRating = true

                        scope.launch {
                            try {
                                // Save or replace this entrant's rating
                                reviewRepository.saveRating(
                                    campsite.id,
                                    entrantId,
                                    myRating
                                )

                                // Reload the campsite to display its new average
                                currentCampsite =
                                    campsiteRepository.getCampsite(campsite.id)
                                        ?: currentCampsite

                                message = "Rating saved!"
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                message = e.message ?: "Could not save rating"
                            } finally {
                                savingRating = false
                            }
                        }
                    }
                ) {
                    Text(if (savingRating) "Saving..." else "Save rating")
                }


// US 01.13.02: Let entrants write a campsite review
                Text("")
                Text("Write a review")

                OutlinedTextField(
                    value = reviewText,
                    onValueChange = { reviewText = it },
                    label = { Text("Your review") },
                    placeholder = { Text("What did you think of this campsite?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                Button(
                    enabled = reviewText.isNotBlank() && !savingReview,
                    onClick = {
                        savingReview = true

                        scope.launch {
                            try {
                                // Save the written review in Firebase
                                reviewRepository.saveReview(
                                    campsite.id,
                                    entrantId,
                                    reviewText
                                )

                                // Reload campsite details to show the new review
                                currentCampsite =
                                    campsiteRepository.getCampsite(campsite.id)
                                        ?: currentCampsite

                                message = "Review submitted!"
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                message = e.message ?: "Could not save review"
                            } finally {
                                savingReview = false
                            }
                        }
                    }
                ) {
                    Text(if (savingReview) "Submitting..." else "Submit review")
                }



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