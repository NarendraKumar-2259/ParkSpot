package io.github.narendrakumar2259.parkspotassignment.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.narendrakumar2259.parkspotassignment.data.CancelResult
import io.github.narendrakumar2259.parkspotassignment.data.Repository
import io.github.narendrakumar2259.parkspotassignment.data.model.Reservation
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyReservationsUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val reservations: List<Reservation> = emptyList(),
    val cancellingIds: Set<String> = emptySet(),
    val message: String? = null,
)

@HiltViewModel
class MyReservationsViewModel @Inject constructor(
    private val repository: Repository,
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)
    private val cancellingIds = MutableStateFlow<Set<String>>(emptySet())
    private val message = MutableStateFlow<String?>(null)
    private val loadError = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val reservations: Flow<List<Reservation>?> = retryTrigger.flatMapLatest {
        flow<List<Reservation>?> {
            loadError.value = null
            emitAll(repository.myReservations)
        }.catch { e ->
            loadError.value = e.message ?: "Could not load your reservations"
            emit(null)
        }
    }

    val uiState: StateFlow<MyReservationsUiState> = combine(
        reservations, cancellingIds, message, loadError,
    ) { reservations, cancelling, message, loadError ->
        MyReservationsUiState(
            isLoading = reservations == null && loadError == null,
            loadError = loadError,
            reservations = reservations.orEmpty(),
            cancellingIds = cancelling,
            message = message,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MyReservationsUiState(),
    )

    fun cancel(reservation: Reservation) {
        if (reservation.id in cancellingIds.value) return
        viewModelScope.launch {
            cancellingIds.update { it + reservation.id }
            message.value = when (val result = repository.cancel(reservation.id)) {
                is CancelResult.Success -> "Cancelled ${reservation.slotName}"
                is CancelResult.NotOwner -> "You can only cancel your own reservations"
                is CancelResult.Failure -> result.message
            }
            cancellingIds.update { it - reservation.id }
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    fun retry() {
        retryTrigger.value++
    }
}