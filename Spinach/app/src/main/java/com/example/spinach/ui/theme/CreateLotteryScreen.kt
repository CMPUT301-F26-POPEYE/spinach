package com.example.spinach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.spinach.Lottery
import com.example.spinach.LotteryRepository
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// US 02.04.01, 02.04.03, 02.04.04, 02.04.05. Dates are typed as yyyy-MM-dd.
@Composable
fun CreateLotteryScreen(campsiteId: String, userId: String, modifier: Modifier = Modifier) {
    val repository = remember { LotteryRepository() }
    var registrationStart by remember { mutableStateOf("") }// keeping track of entries
    var registrationEnd by remember { mutableStateOf("") }
    var bookingStart by remember { mutableStateOf("") }
    var bookingEnd by remember { mutableStateOf("") }
    var winners by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }//show message after owner saves

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Field("Registration start (yyyy-MM-dd)", registrationStart) { registrationStart = it }//feilds for registration and book dates
        Field("Registration end (yyyy-MM-dd)", registrationEnd) { registrationEnd = it }
        Field("Booking start (yyyy-MM-dd)", bookingStart) { bookingStart = it }
        Field("Booking end (yyyy-MM-dd)", bookingEnd) { bookingEnd = it }
        Field("Number of winners", winners) { winners = it }

        Button(onClick = {
            val lottery = Lottery(//create lottery obj
                campsiteId = campsiteId,
                registrationStart = parseDay(registrationStart),
                registrationEnd = parseDay(registrationEnd),
                bookingStart = parseDay(bookingStart),
                bookingEnd = parseDay(bookingEnd),
                winnerCount = winners.toIntOrNull() ?: 0,
            )
            repository.create(lottery, userId) { result ->// try saving to firebase
                message = result.fold({ "Lottery saved" }, { it.message ?: "Could not save" })
            }
        }) {
            Text("Save lottery")
        }
        Text(message)
    }
}

@Composable// resusable text field
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun parseDay(text: String): Long {// returns 0 when text not real date
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)//expected format
    format.timeZone = TimeZone.getTimeZone("UTC")
    format.isLenient = false
    return try {
        format.parse(text)!!.time//convert to millisecond
    } catch (e: Exception) {
        0L
    }
}

// US 02.05.01, for mainactivity needs to be called
@Composable
fun LotteryDrawEffect() {
    LaunchedEffect(Unit) { LotteryRepository().drawDueLotteries() }
}