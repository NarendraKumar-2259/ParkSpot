package io.github.narendrakumar2259.parkspotassignment.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.narendrakumar2259.parkspotassignment.data.model.Reservation

private enum class ReservationStatus(val label: String, val color: Color) {
    ACTIVE("Active now", Color(0xFF1B5E20)),
    UPCOMING("Upcoming", Color(0xFF0D47A1)),
    COMPLETED("Completed", Color(0xFF616161)),
}

private fun statusOf(reservation: Reservation, now: Long): ReservationStatus = when {
    now >= reservation.endTime -> ReservationStatus.COMPLETED
    now >= reservation.startTime -> ReservationStatus.ACTIVE
    else -> ReservationStatus.UPCOMING
}

@Composable
fun MyReservationsScreen(
    contentPadding: PaddingValues,
    snackbarHostState: SnackbarHostState,
    viewModel: MyReservationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    var reservationPendingCancel by remember { mutableStateOf<Reservation?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = "My bookings",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        when {
            state.loadError != null ->
                ErrorState(state.loadError!!, onRetry = viewModel::retry)

            state.isLoading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            state.reservations.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No bookings yet.\nReserve a slot from the Slots tab.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(state.reservations, key = { it.id }) { reservation ->
                    ReservationCard(
                        reservation = reservation,
                        isCancelling = reservation.id in state.cancellingIds,
                        onCancel = { reservationPendingCancel = reservation },
                    )
                }
            }
        }
    }

    reservationPendingCancel?.let { reservation ->
        val isPast = statusOf(reservation, System.currentTimeMillis()) == ReservationStatus.COMPLETED
        AlertDialog(
            onDismissRequest = { reservationPendingCancel = null },
            title = { Text(if (isPast) "Remove ${reservation.slotName}?" else "Cancel ${reservation.slotName}?") },
            text = { Text(formatRange(reservation.startTime, reservation.endTime)) },
            confirmButton = {
                TextButton(onClick = {
                    reservationPendingCancel = null
                    viewModel.cancel(reservation)
                }) { Text(if (isPast) "Remove" else "Cancel booking") }
            },
            dismissButton = {
                TextButton(onClick = { reservationPendingCancel = null }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun ReservationCard(
    reservation: Reservation,
    isCancelling: Boolean,
    onCancel: () -> Unit,
) {
    val status = statusOf(reservation, System.currentTimeMillis())
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = reservation.slotName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = status.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = status.color,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatRange(reservation.startTime, reservation.endTime),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onCancel, enabled = !isCancelling) {
                Text(
                    text = when {
                        isCancelling -> "Cancelling…"
                        status == ReservationStatus.COMPLETED -> "Remove"
                        else -> "Cancel booking"
                    },
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}