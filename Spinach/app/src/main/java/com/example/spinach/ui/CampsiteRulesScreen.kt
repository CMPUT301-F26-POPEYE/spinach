package com.example.spinach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spinach.CampsiteRulesRepository
import com.example.spinach.RulesPolicy
import com.example.spinach.ui.theme.SpinachTheme

// US 02.11.01, owner side: write the rules and policies for one campsite.
@Composable
fun CampsiteRulesScreen(
    campsiteId: String,
    campsiteName: String,
    ownerId: String,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    DisposableEffect(campsiteId) {
        val registration = CampsiteRulesRepository().listen(campsiteId) { rules ->
            // fill the box once, so typing is not overwritten by later updates
            if (!loaded) {
                text = rules?.text.orEmpty()
                loaded = true
            }
        }
        onDispose { registration.remove() }
    }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Rules for $campsiteName", style = MaterialTheme.typography.titleMedium)
        Text("Entrants must confirm they have read these before they reserve. Leave it empty for no rules.")
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Rules and policies") },
            minLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("${text.length} / ${RulesPolicy.MAX_LENGTH}", style = MaterialTheme.typography.bodySmall)
        Button(
            enabled = loaded && !saving,
            onClick = {
                saving = true
                status = ""
                CampsiteRulesRepository().save(campsiteId, ownerId, text) { result ->
                    saving = false
                    status = result.fold({ "Saved" }, { it.message ?: "Could not save" })
                }
            },
        ) {
            Text(if (saving) "Saving..." else "Save rules")
        }
        Text(status)
    }
}

// US 02.11.01, entrant side. Put this on the reservation screen and only enable the Reserve
// button when RulesPolicy.canConfirm(rulesText, accepted) is true. Save accepted into
// Reservation.rulesAcknowledged. It shows nothing when the campsite has no rules.
@Composable
fun RulesAgreement(
    rulesText: String,
    accepted: Boolean,
    onAcceptedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (rulesText.isBlank()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Campsite rules", style = MaterialTheme.typography.titleSmall)
        Text(rulesText, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = accepted, onCheckedChange = onAcceptedChange)
            Text("I have read the campsite rules and policies")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RulesAgreementPreview() {
    SpinachTheme {
        RulesAgreement(
            rulesText = "Quiet hours start at 10 pm. Fires only in the fire pit.",
            accepted = false,
            onAcceptedChange = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
