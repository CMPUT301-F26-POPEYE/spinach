package com.example.spinach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spinach.Campsite
import com.example.spinach.CampsiteRepository
import com.example.spinach.ui.theme.SpinachTheme
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * CampsiteFormScreen
 * The form an owner fills in to register a new campsite, or to edit one they own.
 * Saving goes through [CampsiteRepository.registerCampsite] or [CampsiteRepository.updateCampsite],
 * which stamp / check the owner and run [Campsite.validationError].
 *
 * To cover:
 * - US 02.01.01 As a campsite owner, I want to register my campsite with the app.
 * - US 02.01.02 As a campsite owner, I want to provide campsite details including name, location,
 *   description (amenities), maximum occupancy, map location, optional image, and tags.
 * - US 02.01.03 As a campsite owner, I want to update details about my campsite.
 *
 * @param existing The campsite being edited, or null to register a new one.
 * @param onSaved Called after the campsite is saved.
 * @param modifier Modifier for the form's outer column.
 */
@Composable
fun CampsiteFormScreen(
    existing: Campsite?,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = remember { CampsiteRepository() }
    val scope = rememberCoroutineScope()
    val editing = existing != null

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var location by remember { mutableStateOf(existing?.location ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var occupancy by remember { mutableStateOf(existing?.maxOccupancy?.takeIf { it > 0 }?.toString() ?: "") }
    // (0, 0) means "not set" on a Campsite, so show empty fields instead.
    val coords = existing?.mapCoordinates?.takeIf { it.latitude != 0.0 || it.longitude != 0.0 }
    var latitude by remember { mutableStateOf(coords?.latitude?.toString() ?: "") }
    var longitude by remember { mutableStateOf(coords?.longitude?.toString() ?: "") }
    var tags by remember { mutableStateOf(existing?.tags?.joinToString(", ") ?: "") }
    var imageUrl by remember { mutableStateOf(existing?.images?.firstOrNull() ?: "") }
    var message by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (editing) "Edit campsite" else "Register a campsite",
            style = MaterialTheme.typography.titleLarge,
        )

        FormField("Name", name) { name = it }
        FormField("Location", location) { location = it }
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description (amenities)") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        FormField("Maximum occupancy", occupancy, KeyboardType.Number) { occupancy = it }

        Text("Map location", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormField("Latitude", latitude, KeyboardType.Decimal, Modifier.weight(1f)) { latitude = it }
            FormField("Longitude", longitude, KeyboardType.Decimal, Modifier.weight(1f)) { longitude = it }
        }

        FormField("Tags (comma separated)", tags) { tags = it }
        FormField("Image URL (optional)", imageUrl, KeyboardType.Uri) { imageUrl = it }

        Button(
            enabled = !saving,
            onClick = {
                val error = campsiteFormError(name, location, description, occupancy, latitude, longitude, tags)
                if (error != null) {
                    message = error
                    return@Button
                }
                // Work on a copy so the list on screen only changes once Firestore has the edit.
                val campsite = existing?.let(::copyOf) ?: Campsite()
                campsite.updateDetails(
                    name = name.trim(),
                    location = location.trim(),
                    description = description.trim(),
                    maxOccupancy = occupancy.trim().toInt(),
                    mapCoordinates = GeoPoint(latitude.trim().toDouble(), longitude.trim().toDouble()),
                    tags = parseTags(tags),
                    images = listOfNotNull(imageUrl.trim().ifBlank { null }) + (existing?.images?.drop(1) ?: emptyList()),
                )
                saving = true
                message = ""
                scope.launch {
                    try {
                        if (editing) repository.updateCampsite(campsite) else repository.registerCampsite(campsite)
                        onSaved()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        message = e.message ?: "Could not save the campsite"
                    } finally {
                        saving = false
                    }
                }
            },
        ) {
            Text(if (editing) "Save changes" else "Register campsite")
        }
        if (saving) Text("Saving...")
        Text(message)
    }
}

/**
 * Checks the raw text typed into the campsite form (US 02.01.02).
 * Runs before a [Campsite] is built, so bad numbers never reach [GeoPoint] or Firestore.
 *
 * @param name The campsite name.
 * @param location The location text.
 * @param description The description (amenities).
 * @param occupancy The maximum occupancy, which must be a positive whole number.
 * @param latitude The latitude, between -90 and 90.
 * @param longitude The longitude, between -180 and 180.
 * @param tags The tags, comma separated; at least one is needed.
 * @return A message saying what to fix, or null when the form can be saved.
 */
fun campsiteFormError(
    name: String,
    location: String,
    description: String,
    occupancy: String,
    latitude: String,
    longitude: String,
    tags: String,
): String? {
    if (name.isBlank()) return "Name is required"
    if (location.isBlank()) return "Location is required"
    if (description.isBlank()) return "Description is required"
    if (occupancy.isBlank()) return "Maximum occupancy is required"
    val max = occupancy.trim().toIntOrNull()
    if (max == null || max <= 0) return "Maximum occupancy must be a positive whole number"
    if (latitude.isBlank() || longitude.isBlank()) return "Map location is required"
    val lat = latitude.trim().toDoubleOrNull()
    val lng = longitude.trim().toDoubleOrNull()
    if (lat == null || lat !in -90.0..90.0) return "Latitude must be a number between -90 and 90"
    if (lng == null || lng !in -180.0..180.0) return "Longitude must be a number between -180 and 180"
    if (lat == 0.0 && lng == 0.0) return "Map location is required"
    if (parseTags(tags).isEmpty()) return "Add at least one tag"
    return null
}

/**
 * Turns the comma separated tags field into a list of tags.
 * For example "lake, hiking ,  dogs" becomes ["lake", "hiking", "dogs"].
 *
 * @param text The text typed into the tags field.
 * @return The trimmed tags, with blank entries dropped.
 */
fun parseTags(text: String): List<String> =
    text.split(",").map { it.trim() }.filter { it.isNotEmpty() }

/**
 * Copies a campsite, so edits made in the form do not touch the campsite shown in the list
 * until Firestore has saved them (US 02.01.03).
 *
 * @param c The campsite to copy.
 * @return A new [Campsite] with the same values and its own lists.
 */
fun copyOf(c: Campsite): Campsite = Campsite(
    id = c.id,
    name = c.name,
    location = c.location,
    description = c.description,
    maxOccupancy = c.maxOccupancy,
    mapCoordinates = c.mapCoordinates,
    tags = c.tags.toMutableList(),
    images = c.images.toMutableList(),
    qrCode = c.qrCode,
    ownerId = c.ownerId,
    ratings = c.ratings.toMutableList(),
    reviews = c.reviews.toMutableList(),
)

/**
 * A single line text field that fills the form's width, like the other forms in the app.
 *
 * @param label The label shown in the field.
 * @param value The current text.
 * @param keyboardType Which keyboard to show, such as numbers for maximum occupancy.
 * @param modifier Modifier for the field; fills the width by default.
 * @param onChange Called with the new text when the user types.
 */
@Composable
private fun FormField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun CampsiteFormPreview() {
    SpinachTheme {
        CampsiteFormScreen(existing = null, onSaved = {})
    }
}
