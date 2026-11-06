package com.example.spinach

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp



/**
 * CampsiteList
 * A scrollable list of campsites. Each item shows the campsite's name, location,
 * maximum occupancy and rating (the average of its ratings, or "Unrated" when it has none).
 * Tapping an item calls onCampsiteClick with that campsite's id so the details can be opened.
 *
 * To cover:
 * - US 01.01.03 As an entrant, I want to see a list of campsites based on my filters.
 * - US 01.01.04 As an entrant, I want to see campsite summary information including name, location, maximum occupancy, and rating.
 *
 * @param campsites The campsites to show.
 * @param onCampsiteClick Called with the id of the campsite that was tapped.
 * @param modifier Modifier for the list.
 */
@Composable
fun CampsiteList(
    campsites: List<Campsite>,
    onCampsiteClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier) {
        items(campsites, key = { it.id }) { c ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { onCampsiteClick(c.id) }
                    .padding(vertical = 12.dp)
            ) {
                summaryLines(c).forEach { Text(it) } // name, location, max occupancy, rating
            }
            HorizontalDivider()
        }
    }
}