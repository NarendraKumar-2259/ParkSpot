package io.github.narendrakumar2259.parkspotassignment.data.model

data class Slot(
    val id: String,
    val name: String,
    val intervals: List<BookedInterval> = emptyList(),
)

/**
 * One booked time window on a slot, mirrored inside the slot document's
 * `intervals` map so that a single transactional read of the slot doc is
 * enough to decide availability.
 */
data class BookedInterval(
    val reservationId: String,
    val userId: String,
    val startTime: Long,
    val endTime: Long,
)

/**
 * Half-open interval overlap: [aStart, aEnd) vs [bStart, bEnd).
 * Back-to-back bookings (one ends exactly when the next starts) do not overlap.
 */
fun overlaps(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Boolean =
    aStart < bEnd && bStart < aEnd

fun Slot.conflictsFor(startTime: Long, endTime: Long): List<BookedInterval> =
    intervals.filter { overlaps(startTime, endTime, it.startTime, it.endTime) }

fun Slot.isAvailableFor(startTime: Long, endTime: Long): Boolean =
    conflictsFor(startTime, endTime).isEmpty()