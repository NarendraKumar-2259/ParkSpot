package io.github.narendrakumar2259.parkspotassignment.presentation

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val DATE_TIME = DateTimeFormatter.ofPattern("EEE, d MMM • h:mm a")
private val DATE = DateTimeFormatter.ofPattern("EEE, d MMM")
private val TIME = DateTimeFormatter.ofPattern("h:mm a")

private fun Long.toZoned(): ZonedDateTime =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault())

fun formatDateTime(millis: Long): String = millis.toZoned().format(DATE_TIME)

fun formatTime(millis: Long): String = millis.toZoned().format(TIME)

fun formatRange(startMillis: Long, endMillis: Long): String {
    val start = startMillis.toZoned()
    val end = endMillis.toZoned()
    return if (start.toLocalDate() == end.toLocalDate()) {
        "${start.format(DATE)}, ${start.format(TIME)} – ${end.format(TIME)}"
    } else {
        "${start.format(DATE_TIME)} – ${end.format(DATE_TIME)}"
    }
}