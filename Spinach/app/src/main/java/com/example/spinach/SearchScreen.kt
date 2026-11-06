package com.example.spinach

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
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
import kotlinx.coroutines.CancellationException

private const val NO_RESULTS = "Sorry, There doesn't seem to be anything matching your search."


/**
 * SearchScreen
 * The entrant's search screen. Flow: search bar -> searchCampsites -> filterCampsites (popup filters)
 * -> CampsiteList (summaries) -> tap an item -> CampsiteDetailsDialog (details + join the lottery waiting list).
 * The list is loaded again whenever the query or the applied filters change.
 *
 * To cover:
 * - US 01.01.01 As an entrant, I want to search for campsites.
 * - US 01.01.02 As an entrant, I want to filter campsites by location, date, and number of guests.
 * - US 01.01.03 As an entrant, I want to see a list of campsites based on my filters.
 * - US 01.01.04 As an entrant, I want to see campsite summary information including name, location, maximum occupancy, and rating.
 * - US 01.01.05 As an entrant, I want to see details of a campsite.
 * - US 01.01.06 As an entrant, I want to join an active lottery from the campsite details screen.
 * - US 01.02.01 As an entrant, I want to join the waiting list for a campsite lottery.
 *
 * @param modifier The modifier for the screen's layout.
 */
@Composable
fun SearchScreen(modifier: Modifier = Modifier.Companion) {
    var query by remember { mutableStateOf("") }
    var filters by remember { mutableStateOf(CampsiteFilters()) }
    var results by remember { mutableStateOf<List<Campsite>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Campsite?>(null) }

    // Runs on first display and again whenever the query or the applied filters change (the old run is cancelled).
    LaunchedEffect(query, filters) {
        try {
            results = filterCampsites(
                searchCampsites(query),
                filters.location,
                filters.startDate,
                filters.endDate,
                filters.guests,
            )
            message = if (results.isEmpty()) NO_RESULTS else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            results = emptyList()
            message = e.message ?: "Could not load campsites"
        }
    }

    Column(modifier = modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search campsites") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = { showFilters = true },
                modifier = Modifier.padding(start = 8.dp),
            ) { Text(if (filters.isActive) "Filters (on)" else "Filters") }
        }

        message?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }

        CampsiteList(results, onCampsiteClick = { id -> selected = results.find { it.id == id } })
    }

    if (showFilters) {
        FilterDialog(
            filters = filters,
            onApply = { filters = it; showFilters = false },
            onDismiss = { showFilters = false },
        )
    }

    selected?.let { CampsiteDetailsDialog(it) { selected = null } }
}