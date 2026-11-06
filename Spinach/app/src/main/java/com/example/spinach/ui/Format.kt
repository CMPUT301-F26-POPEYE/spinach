package com.example.spinach.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// For example "6 Nov 2026, 14:05", in the phone's time zone.
fun formatDateTime(millis: Long): String =
    if (millis <= 0L) "Unknown time"
    else SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))
