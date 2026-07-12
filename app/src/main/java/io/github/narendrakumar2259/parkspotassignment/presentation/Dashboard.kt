package io.github.narendrakumar2259.parkspotassignment.presentation

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val AvailableContainer = Color(0xFFC8E6C9)
private val AvailableContent = Color(0xFF1B5E20)
private val BookedContainer = Color(0xFFFFCDD2)
private val BookedContent = Color(0xFFB71C1C)
private val MineContainer = Color(0xFFBBDEFB)
private val MineContent = Color(0xFF0D47A1)

@Composable
fun DashboardScreen(
    contentPadding: PaddingValues,
    snackbarHostState: SnackbarHostState,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    var slotPendingConfirmation by remember { mutableStateOf<SlotUi?>(null) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = "Find a spot",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        TimeWindowSelector(
            windowStart = state.windowStart,
            windowEnd = state.windowEnd,
            onEditStart = { showStartPicker = true },
            onEditEnd = { showEndPicker = true },
        )
        state.windowError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (state.isReserving) {
            LinearProgressIndicator(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        when {
            state.loadError != null ->
                ErrorState(state.loadError!!, onRetry = viewModel::retry)

            state.isLoading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            else -> SlotGrid(
                slots = state.slots,
                interactionEnabled = !state.isReserving && state.windowError == null,
                onSlotClick = { slotPendingConfirmation = it },
            )
        }
    }

    slotPendingConfirmation?.let { slot ->
        AlertDialog(
            onDismissRequest = { slotPendingConfirmation = null },
            title = { Text("Reserve ${slot.name}?") },
            text = { Text(formatRange(state.windowStart, state.windowEnd)) },
            confirmButton = {
                TextButton(onClick = {
                    slotPendingConfirmation = null
                    viewModel.reserve(slot)
                }) { Text("Reserve") }
            },
            dismissButton = {
                TextButton(onClick = { slotPendingConfirmation = null }) { Text("Cancel") }
            },
        )
    }

    if (showStartPicker) {
        DateTimePickerDialog(
            title = "Start time",
            initialMillis = state.windowStart,
            onDismiss = { showStartPicker = false },
            onConfirm = {
                showStartPicker = false
                viewModel.setStartTime(it)
            },
        )
    }
    if (showEndPicker) {
        DateTimePickerDialog(
            title = "End time",
            initialMillis = state.windowEnd,
            onDismiss = { showEndPicker = false },
            onConfirm = {
                showEndPicker = false
                viewModel.setEndTime(it)
            },
        )
    }
}

@Composable
private fun TimeWindowSelector(
    windowStart: Long,
    windowEnd: Long,
    onEditStart: () -> Unit,
    onEditEnd: () -> Unit,
) {
    Card {
        Column {
            TimeRow(label = "From", millis = windowStart, onClick = onEditStart)
            HorizontalDivider()
            TimeRow(label = "Until", millis = windowEnd, onClick = onEditEnd)
        }
    }
}

@Composable
private fun TimeRow(label: String, millis: Long, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(formatDateTime(millis), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun SlotGrid(
    slots: List<SlotUi>,
    interactionEnabled: Boolean,
    onSlotClick: (SlotUi) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        items(slots, key = { it.id }) { slot ->
            SlotCard(
                slot = slot,
                enabled = interactionEnabled,
                onClick = { onSlotClick(slot) },
            )
        }
    }
}

@Composable
private fun SlotCard(slot: SlotUi, enabled: Boolean, onClick: () -> Unit) {
    val (container, content) = when {
        slot.isAvailable -> AvailableContainer to AvailableContent
        slot.isMine -> MineContainer to MineContent
        else -> BookedContainer to BookedContent
    }
    Card(
        onClick = onClick,
        enabled = enabled && slot.isAvailable,
        colors = CardDefaults.cardColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = slot.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = when {
                    slot.isAvailable -> "Available"
                    slot.isMine -> "Yours"
                    else -> "Booked"
                },
                style = MaterialTheme.typography.labelMedium,
            )
            if (!slot.isAvailable && slot.conflictStart != null && slot.conflictEnd != null) {
                Text(
                    text = "${formatTime(slot.conflictStart)} – ${formatTime(slot.conflictEnd)}",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}