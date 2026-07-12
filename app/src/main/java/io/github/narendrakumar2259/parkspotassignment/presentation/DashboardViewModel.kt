package io.github.narendrakumar2259.parkspotassignment.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.narendrakumar2259.parkspotassignment.data.Repository
import io.github.narendrakumar2259.parkspotassignment.data.ReserveResult
import io.github.narendrakumar2259.parkspotassignment.data.model.Slot
import io.github.narendrakumar2259.parkspotassignment.data.model.conflictsFor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: Repository,
) : ViewModel() {

    companion object {
        private val QUARTER_HOUR = TimeUnit.MINUTES.toMillis(15)
        private val DEFAULT_DURATION = TimeUnit.HOURS.toMillis(1)
        private val START_GRACE = TimeUnit.MINUTES.toMillis(5)
    }

    private val retryTrigger = MutableStateFlow(0)
    private val window = MutableStateFlow(defaultWindow())
    private val isReserving = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val loadError = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val slots: Flow<List<Slot>?> = retryTrigger.flatMapLatest {
        flow<List<Slot>?> {
            loadError.value = null
            emitAll(repository.slots)
        }.catch { e ->
            loadError.value = e.message ?: "Could not load slots"
            emit(null)
        }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        slots, window, isReserving, message, loadError,
    ) { slots, window, reserving, message, loadError ->
        buildState(slots, window.first, window.second, reserving, message, loadError)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DashboardUiState(windowStart = window.value.first, windowEnd = window.value.second),
    )

    fun setStartTime(millis: Long) {
        val end = window.value.second
        window.value = millis to if (end <= millis) millis + DEFAULT_DURATION else end
    }

    fun setEndTime(millis: Long) {
        window.value = window.value.first to millis
    }

    fun reserve(slot: SlotUi) {
        val (start, end) = window.value
        validateWindow(start, end)?.let {
            message.value = it
            return
        }
        if (isReserving.value) return
        viewModelScope.launch {
            isReserving.value = true
            message.value = when (val result = repository.reserve(slot.id, start, end)) {
                is ReserveResult.Success ->
                    "Reserved ${slot.name} • ${formatRange(start, end)}"
                is ReserveResult.Conflict ->
                    "${slot.name} was just booked for an overlapping time. Pick another slot or time."
                is ReserveResult.Failure -> result.message
            }
            isReserving.value = false
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    fun retry() {
        retryTrigger.value++
    }

    private fun buildState(
        slots: List<Slot>?,
        windowStart: Long,
        windowEnd: Long,
        isReserving: Boolean,
        message: String?,
        loadError: String?,
    ): DashboardUiState {
        val uid = repository.currentUserId
        return DashboardUiState(
            isLoading = slots == null && loadError == null,
            loadError = loadError,
            windowStart = windowStart,
            windowEnd = windowEnd,
            windowError = validateWindow(windowStart, windowEnd),
            isReserving = isReserving,
            message = message,
            slots = slots.orEmpty().map { slot ->
                val conflicts = slot.conflictsFor(windowStart, windowEnd)
                val firstConflict = conflicts.minByOrNull { it.startTime }
                SlotUi(
                    id = slot.id,
                    name = slot.name,
                    isAvailable = conflicts.isEmpty(),
                    isMine = conflicts.any { it.userId == uid },
                    conflictStart = firstConflict?.startTime,
                    conflictEnd = firstConflict?.endTime,
                )
            },
        )
    }

    private fun validateWindow(start: Long, end: Long): String? {
        val now = System.currentTimeMillis()
        return when {
            end <= start -> "End time must be after start time"
            start < now - START_GRACE -> "Start time is in the past"
            else -> null
        }
    }

    private fun defaultWindow(): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val start = (now / QUARTER_HOUR + 1) * QUARTER_HOUR
        return start to start + DEFAULT_DURATION
    }
}