package io.github.narendrakumar2259.parkspotassignment.presentation

data class DashboardUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val slots: List<SlotUi> = emptyList(),
    val windowStart: Long = 0L,
    val windowEnd: Long = 0L,
    val windowError: String? = null,
    val isReserving: Boolean = false,
    val message: String? = null,
)

/** A slot's availability evaluated against the currently selected time window. */
data class SlotUi(
    val id: String,
    val name: String,
    val isAvailable: Boolean,
    val isMine: Boolean,
    val conflictStart: Long? = null,
    val conflictEnd: Long? = null,
)