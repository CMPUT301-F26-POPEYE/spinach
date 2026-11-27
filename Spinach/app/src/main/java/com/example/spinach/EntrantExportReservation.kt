package com.example.spinach

import android.content.ContentResolver
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * ExportService
 * Turns reservations into a CSV file the entrant can download.
 * The CSV building has no Firebase and no file access, so JUnit can test it.
 *
 * To cover:
 * - US 01.05.02 As an entrant, I want to receive reservation by notification or downloadable CSV.
 *
 * The notification half of the story is already done by
 * EntrantReservationRules.confirmationMessage (US 01.05.01).
 */
object ExportService {

    /** The first line of every exported file */
    const val HEADER = "Campsite Name,Location,Start Date,End Date,Status"

    /** MIME type to give to the file picker */
    /** MIME type tells receiver what type of file this is */
    const val MIME_TYPE = "text/csv"

    /** The name suggested to the entrant in the file picker. */
    const val DEFAULT_FILE_NAME = "my_reservations.csv"

    /**
     * Builds the CSV text for a list of reservations (US 01.05.02).
     * Each reservation is one row: campsite name, location, start date, end date, status.
     * Dates are stored as UTC midnight millis, so start is the earliest date and end is the
     * latest date, written as yyyy-MM-dd in UTC.
     *
     * @param reservations The reservations to export (for example the entrant's own).
     * @param locationFor Gives the location text of a campsite id. Return "" if unknown.
     * @return The whole CSV, header included.
     */
    fun toCsv(
        reservations: List<Reservation>,
        locationFor: (campsiteId: String) -> String,
    ): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val rows = reservations.map { reservation ->
            val start = reservation.dates.minOrNull()?.let { format.format(Date(it)) }.orEmpty()
            val end = reservation.dates.maxOrNull()?.let { format.format(Date(it)) }.orEmpty()
            listOf(
                reservation.campsiteName,
                locationFor(reservation.campsiteId),
                start,
                end,
                reservation.status.name,
            ).joinToString(",") { escape(it) }
        }

        return (listOf(HEADER) + rows).joinToString("\r\n") + "\r\n"
    }

    /**
     * Same as the other toCsv, but looks the location up in a list of campsites by
     * Campsite.id. A reservation whose campsite is not in the list gets an empty location.
     */
    fun toCsv(reservations: List<Reservation>, campsites: List<Campsite>): String {
        val locations = campsites.associate { it.id to it.location }
        return toCsv(reservations) { id -> locations[id].orEmpty() }
    }

    /**
     * Writes CSV text to a file the entrant picked (for example with
     * ActivityResultContracts.CreateDocument). No storage permission is needed for this.
     *
     * @param resolver The ContentResolver of the context, to open the uri.
     * @param uri The uri returned by the file picker.
     * @param csv The text from toCsv.
     * @return success, or the reason it failed.
     */
    fun writeTo(resolver: ContentResolver, uri: Uri, csv: String): Result<Unit> =
        runCatching {
            // "wt" so an existing file is replaced, not appended to
            val stream = resolver.openOutputStream(uri, "wt")
                ?: throw IllegalStateException("Could not open the file")
            stream.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
        }

    /**
     * Makes one value safe for a CSV cell.
     * Values with a comma, quote or line break are wrapped in quotes (quotes doubled).
     * A value that starts with = + - @ is prefixed with ' so a spreadsheet does not run it
     * as a formula (campsite names are typed by owners).
     */
    internal fun escape(value: String): String {
        val safe = if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value
        val needsQuotes = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}