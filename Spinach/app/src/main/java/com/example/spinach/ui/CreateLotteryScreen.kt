package com.example.spinach.ui
 
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.spinach.Campsite
import com.example.spinach.Lottery
import com.example.spinach.LotteryRepository
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.Date

// US 02.04.01, 02.04.02, 02.04.03, 02.04.04, 02.04.05, 02.04.07. Dates are typed as yyyy-MM-dd.

/**
 * Lets campsite owners create or edit a lottery.
 * When editing, the form shows the lottery's saved information.
 *
 * US 02.04.06: Update an existing lottery listing.
 *
 * Editing functionality added with the help of ChatGPT, OpenAI,
 * "CMPUT 301 Update Lottery Listing", November 24, 2026.
 */
@Composable
fun CreateLotteryScreen(campsites: List<Campsite>, userId: String, modifier: Modifier = Modifier, existing: Lottery? = null,onSaved: () -> Unit = {} ) {

    val repository = remember { LotteryRepository() }
    val editing = existing != null // checks if we're updating an existing lottery

// Load saved campsites when editing, otherwise start with none selected
    var selected by remember(existing?.id) {
        mutableStateOf(existing?.campsites()?.toSet() ?: emptySet<String>())
    }

// Fill in the old dates when editing
    var registrationStart by remember(existing?.id) {
        mutableStateOf(formatDay(existing?.registrationStart ?: 0L))
    }
    var registrationEnd by remember(existing?.id) {
        mutableStateOf(formatDay(existing?.registrationEnd ?: 0L))
    }
    var bookingStart by remember(existing?.id) {
        mutableStateOf(formatDay(existing?.bookingStart ?: 0L))
    }
    var bookingEnd by remember(existing?.id) {
        mutableStateOf(formatDay(existing?.bookingEnd ?: 0L))
    }

    var winners by remember(existing?.id) {
        mutableStateOf(existing?.winnerCount?.toString() ?: "")
    }

    var capacity by remember(existing?.id) {
        mutableStateOf(existing?.waitlistCapacity?.toString() ?: "")
    }

    var message by remember(existing?.id) { mutableStateOf("") }
    var saving by remember(existing?.id) { mutableStateOf(false) }


    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Show whether this form is for creating or editing
        Text(
            if (editing) "Edit lottery" else "Create lottery",
            style = MaterialTheme.typography.titleLarge
        )
        Text("Campsites in this lottery", style = MaterialTheme.typography.titleSmall)
        if (campsites.isEmpty()) {
            Text("You have no campsites yet. Register a campsite first.")
        }
        campsites.forEach { campsite ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selected = toggle(selected, campsite.id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = campsite.id in selected,
                    onCheckedChange = { selected = toggle(selected, campsite.id) },
                )
                Text(campsite.name.ifEmpty { "Unnamed campsite" })
            }
        }

        Field("Registration start (yyyy-MM-dd)", registrationStart) { registrationStart = it }
        Field("Registration end (yyyy-MM-dd)", registrationEnd) { registrationEnd = it }
        Field("Booking start (yyyy-MM-dd)", bookingStart) { bookingStart = it }
        Field("Booking end (yyyy-MM-dd)", bookingEnd) { bookingEnd = it }
        Field("Number of winners", winners) { winners = it }
        Field("Waitlist capacity (optional)", capacity) { capacity = it }
        // Drawn lotteries can't be changed
        if (editing && existing?.drawn == true) {
            Text("This lottery has already been drawn and cannot be edited.")
        }


        Button(
            enabled = !saving && existing?.drawn != true,
            onClick = {
                // Check that the selected campsites still belong to this owner
                val ids = campsites.map { it.id }.filter { it in selected }

                if (ids.isEmpty()) {
                    message = "Select at least one campsite"
                } else {
                    val lottery = Lottery(
                        id = existing?.id ?: "", // keep the document ID when editing
                        campsiteId = ids.first(),
                        campsiteIds = ids.toMutableList(),
                        registrationStart = parseDay(registrationStart),
                        registrationEnd = parseDay(registrationEnd),
                        bookingStart = parseDay(bookingStart),
                        bookingEnd = parseDay(bookingEnd),
                        winnerCount = winners.toIntOrNull() ?: 0,
                        // blank means unlimited; invalid input fails validation
                        waitlistCapacity = if (capacity.isBlank()) {
                            null
                        } else {
                            capacity.toIntOrNull() ?: 0
                        }
                    )

                    saving = true // prevent repeated clicks while saving

                    if (editing) {
                        // Update the existing lottery in Firebase
                        repository.update(lottery, userId) { result ->
                            saving = false

                            result.fold(
                                onSuccess = {
                                    message = "Lottery updated!"
                                    onSaved()
                                },
                                onFailure = {
                                    message = it.message ?: "Could not update lottery"
                                }
                            )
                        }
                    } else {
                        // Keep the original lottery creation process
                        repository.create(lottery, userId) { result ->
                            saving = false

                            result.fold(
                                onSuccess = {
                                    message = "Lottery saved!"
                                    onSaved()
                                },
                                onFailure = {
                                    message = it.message ?: "Could not save lottery"
                                }
                            )
                        }
                    }
                }
            }
        ) {
            Text(
                when {
                    saving -> "Saving..."
                    editing -> "Update lottery"
                    else -> "Save lottery"
                }
            )
        }




        Text(message)
    }
}

private fun toggle(set: Set<String>, id: String): Set<String> =
    if (id in set) set - id else set + id

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun parseDay(text: String): Long {// returns 0 when text is not a real date
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    format.timeZone = TimeZone.getTimeZone("UTC")
    format.isLenient = false
    return try {
        format.parse(text)!!.time
    } catch (e: Exception) {
        0L
    }
}


/**
 * Converts a saved lottery date into text for the edit form.
 *
 * Added with the help of ChatGPT, OpenAI,
 * "CMPUT 301 Update Lottery Listing", November 24, 2026.
 */
private fun formatDay(time: Long): String {
    if (time == 0L) return ""

    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    format.timeZone = TimeZone.getTimeZone("UTC")

    return format.format(Date(time)) // show saved date
}

// US 02.05.01, needs to be called from MainActivity
@Composable
fun LotteryDrawEffect() {
    LaunchedEffect(Unit) { LotteryRepository().drawDueLotteries() }
}
