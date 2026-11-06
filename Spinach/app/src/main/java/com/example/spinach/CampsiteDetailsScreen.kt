package com.example.spinach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.spinach.Campsite
import com.example.spinach.detailLines

// US 02.01.04: the owner views one of their campsites. Uses the same detail lines as the
// entrant details popup (Arnav's detailLines), plus an Edit button for US 02.01.03.
@Composable
fun CampsiteDetailsScreen(
    campsite: Campsite?,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (campsite == null) {
        Text("Campsite not found.", modifier = modifier.padding(16.dp))
        return
    }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(campsite.name.ifEmpty { "Unnamed campsite" }, style = MaterialTheme.typography.titleLarge)
        detailLines(campsite).forEach { Text(it) }
        if (campsite.images.isNotEmpty()) Text("Images: ${campsite.images.joinToString(", ")}")
        if (campsite.qrCode.isNotEmpty()) Text("QR code: ${campsite.qrCode}")
        Button(onClick = onEdit) { Text("Edit details") }
    }
}