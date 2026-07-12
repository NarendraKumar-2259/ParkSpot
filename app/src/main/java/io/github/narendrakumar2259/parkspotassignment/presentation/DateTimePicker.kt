package io.github.narendrakumar2259.parkspotassignment.presentation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Two-step picker: date first, then time of day. Calls [onConfirm] with the
 * chosen moment as epoch millis in the device's time zone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    title: String,
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val initial = remember(initialMillis) {
        Instant.ofEpochMilli(initialMillis).atZone(ZoneId.systemDefault())
    }
    // DatePickerState works in UTC-midnight millis, so convert both ways.
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = initial.toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    val timeState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = false,
    )
    var step by remember { mutableIntStateOf(0) }

    if (step == 0) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    enabled = dateState.selectedDateMillis != null,
                    onClick = { step = 1 },
                ) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        ) {
            DatePicker(state = dateState)
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val dateMillis = dateState.selectedDateMillis ?: return@TextButton
                    val localDate = Instant.ofEpochMilli(dateMillis)
                        .atZone(ZoneOffset.UTC).toLocalDate()
                    val result = localDate
                        .atTime(timeState.hour, timeState.minute)
                        .atZone(ZoneId.systemDefault())
                        .toInstant().toEpochMilli()
                    onConfirm(result)
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}