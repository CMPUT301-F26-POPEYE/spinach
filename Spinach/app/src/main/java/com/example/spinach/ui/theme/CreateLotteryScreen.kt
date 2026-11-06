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

// US 02.04.01, 02.04.02, 02.04.03, 02.04.04, 02.04.05, 02.04.07. Dates are typed as yyyy-MM-dd.
@Composable
fun CreateLotteryScreen(campsites: List<Campsite>, userId: String, modifier: Modifier = Modifier) {
    val repository = remember { LotteryRepository() }
    var selected by remember { mutableStateOf(setOf<String>()) }// campsite ids ticked for this lottery
    var registrationStart by remember { mutableStateOf("") }
    var registrationEnd by remember { mutableStateOf("") }
    var bookingStart by remember { mutableStateOf("") }
    var bookingEnd by remember { mutableStateOf("") }
    var winners by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("") }// optional, blank means no limit
    var message by remember { mutableStateOf("") }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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

        Button(onClick = {
            if (selected.isEmpty()) {
                message = "Select at least one campsite"
            } else {
                val ids = campsites.map { it.id }.filter { it in selected }
                val lottery = Lottery(
                    campsiteId = ids.first(),
                    campsiteIds = ids.toMutableList(),
                    registrationStart = parseDay(registrationStart),
                    registrationEnd = parseDay(registrationEnd),
                    bookingStart = parseDay(bookingStart),
                    bookingEnd = parseDay(bookingEnd),
                    winnerCount = winners.toIntOrNull() ?: 0,
                    // blank means no limit, text that is not a number becomes 0 so validation rejects it
                    waitlistCapacity = if (capacity.isBlank()) null else (capacity.toIntOrNull() ?: 0),
                )
                repository.create(lottery, userId) { result ->
                    message = result.fold({ "Lottery saved" }, { it.message ?: "Could not save" })
                }
            }
        }) {
            Text("Save lottery")
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

// US 02.05.01, needs to be called from MainActivity
@Composable
fun LotteryDrawEffect() {
    LaunchedEffect(Unit) { LotteryRepository().drawDueLotteries() }
}
