package com.example.spinach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * The filters chosen in the popup. A default value means that filter is off.
 *
 * @property location Text the campsite's location must contain. Blank means no location filter.
 * @property guests Number of guests the campsite must hold, or `null` for no guest filter.
 * @property startDate First day of the range as UTC midnight millis (what the date picker returns), or `null`.
 * @property endDate Last day of the range as UTC midnight millis, or `null`.
 */
data class CampsiteFilters(
    val location: String = "",
    val guests: Int? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
) {
    /** `true` when at least one filter is set, so the button can show that filters are on. */
    val isActive: Boolean
        get() = location.isNotBlank() || guests != null || startDate != null
}

/**
 * FilterDialog
 * Popup for the location, dates and number of guests filters (US 01.01.02).
 * The changes only count when the entrant taps Apply. "Clear all" applies empty filters.
 *
 * @param filters The filters that are currently applied, shown when the popup opens.
 * @param onApply Called with the new filters when the entrant applies or clears them.
 * @param onDismiss Called when the entrant closes the popup without applying.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDialog(
    filters: CampsiteFilters,
    onApply: (CampsiteFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var location by remember { mutableStateOf(filters.location) }
    var guests by remember { mutableStateOf(filters.guests?.toString() ?: "") }
    var start by remember { mutableStateOf(filters.startDate) }
    var end by remember { mutableStateOf(filters.endDate) }
    var showPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filters") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = guests,
                    onValueChange = { guests = it.filter(Char::isDigit) },
                    label = { Text("Number of guests") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row {
                    OutlinedButton(onClick = { showPicker = true }) {
                        val s = start
                        val e = end ?: s
                        Text(
                            if (s != null && e != null) {
                                "${ReservationQueries.formatDay(s)} to ${ReservationQueries.formatDay(e)}"
                            } else {
                                "Select dates"
                            }
                        )
                    }
                    if (start != null) {
                        TextButton(onClick = { start = null; end = null }) { Text("Clear dates") }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(
                        CampsiteFilters(
                            location = location.trim(),
                            guests = guests.toIntOrNull()?.takeIf { it > 0 },
                            startDate = start,
                            endDate = end ?: start,
                        )
                    )
                }
            ) { Text("Apply") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onApply(CampsiteFilters()) }) { Text("Clear all") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )

    if (showPicker) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = start,
            initialSelectedEndDateMillis = end,
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null,
                    onClick = {
                        start = pickerState.selectedStartDateMillis
                        end = pickerState.selectedEndDateMillis ?: pickerState.selectedStartDateMillis
                        showPicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(state = pickerState, modifier = Modifier.height(500.dp))
        }
    }
}