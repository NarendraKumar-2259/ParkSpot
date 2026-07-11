package io.github.narendrakumar2259.parkspotassignment.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import io.github.narendrakumar2259.parkspotassignment.data.model.BookedInterval
import io.github.narendrakumar2259.parkspotassignment.data.model.Reservation
import io.github.narendrakumar2259.parkspotassignment.data.model.Slot
import io.github.narendrakumar2259.parkspotassignment.data.model.overlaps
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

class SlotUnavailableException :
    Exception("This slot is already reserved for an overlapping time window")

class NotReservationOwnerException :
    Exception("You can only cancel your own reservations")

sealed interface ReserveResult {
    data object Success : ReserveResult
    data object Conflict : ReserveResult
    data class Failure(val message: String) : ReserveResult
}

sealed interface CancelResult {
    data object Success : CancelResult
    data object NotOwner : CancelResult
    data class Failure(val message: String) : CancelResult
}

@Singleton
class Repository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) {

    companion object {
        const val SLOT_COUNT = 20
        private const val SLOTS = "slots"
        private const val RESERVATIONS = "reservations"
        private const val FIELD_INTERVALS = "intervals"
        private const val FIELD_USER_ID = "userId"
        private const val FIELD_START = "startTime"
        private const val FIELD_END = "endTime"
    }

    private val signInMutex = Mutex()

    val currentUserId: String?
        get() = auth.currentUser?.uid

    suspend fun ensureSignedIn(): String {
        auth.currentUser?.let { return it.uid }
        return signInMutex.withLock {
            auth.currentUser?.uid
                ?: auth.signInAnonymously().await().user?.uid
                ?: throw IllegalStateException("Anonymous sign-in failed")
        }
    }

    /** All 20 slots with their booked intervals, live-updating. */
    val slots: Flow<List<Slot>> = flow {
        ensureSignedIn()
        ensureSlotsSeeded()
        emitAll(slotSnapshots())
    }

    /** The current user's reservations (upcoming and past), live-updating. */
    val myReservations: Flow<List<Reservation>> = flow {
        val uid = ensureSignedIn()
        emitAll(reservationSnapshots(uid))
    }

    /**
     * Reserves [slotId] for [startTime, endTime) atomically.
     *
     * The transaction reads the slot document, rejects the booking if any live
     * interval overlaps the requested window, and otherwise writes the updated
     * interval map plus the reservation document in one atomic commit. If two
     * clients race on the same slot, Firestore's optimistic concurrency makes
     * the loser's transaction re-run against the winner's committed data, so
     * it then sees the new interval and fails with [ReserveResult.Conflict] —
     * overlapping reservations can never be created.
     */
    suspend fun reserve(slotId: String, startTime: Long, endTime: Long): ReserveResult {
        if (endTime <= startTime) return ReserveResult.Failure("End time must be after start time")
        return try {
            val uid = ensureSignedIn()
            val slotRef = firestore.collection(SLOTS).document(slotId)
            val reservationRef = firestore.collection(RESERVATIONS).document()
            firestore.runTransaction { tx ->
                val slotSnap = tx.get(slotRef)
                if (!slotSnap.exists()) throw IllegalStateException("Slot $slotId does not exist")

                // Drop intervals that already ended so the document never grows unbounded.
                val now = System.currentTimeMillis()
                val liveIntervals = parseIntervals(slotSnap.get(FIELD_INTERVALS))
                    .filter { it.endTime > now }

                if (liveIntervals.any { overlaps(startTime, endTime, it.startTime, it.endTime) }) {
                    throw SlotUnavailableException()
                }

                val updatedIntervals =
                    liveIntervals.associate { it.reservationId to it.toIntervalMap() } +
                            (reservationRef.id to mapOf(
                                FIELD_USER_ID to uid,
                                FIELD_START to startTime,
                                FIELD_END to endTime,
                            ))
                tx.update(slotRef, FIELD_INTERVALS, updatedIntervals)
                tx.set(
                    reservationRef,
                    mapOf(
                        FIELD_USER_ID to uid,
                        "slotId" to slotId,
                        "slotName" to (slotSnap.getString("name") ?: slotId),
                        FIELD_START to startTime,
                        FIELD_END to endTime,
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                )
                null
            }.await()
            ReserveResult.Success
        } catch (e: SlotUnavailableException) {
            ReserveResult.Conflict
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ReserveResult.Failure(e.message ?: "Could not complete the reservation")
        }
    }

    /**
     * Cancels a reservation. Ownership is verified inside the transaction (and
     * again by Firestore security rules), and the reservation document and its
     * interval entry on the slot are removed atomically.
     */
    suspend fun cancel(reservationId: String): CancelResult {
        return try {
            val uid = ensureSignedIn()
            val reservationRef = firestore.collection(RESERVATIONS).document(reservationId)
            firestore.runTransaction { tx ->
                val snap = tx.get(reservationRef)
                if (!snap.exists()) return@runTransaction null // already cancelled elsewhere
                if (snap.getString(FIELD_USER_ID) != uid) throw NotReservationOwnerException()

                snap.getString("slotId")?.let { slotId ->
                    tx.update(
                        firestore.collection(SLOTS).document(slotId),
                        FieldPath.of(FIELD_INTERVALS, reservationId),
                        FieldValue.delete(),
                    )
                }
                tx.delete(reservationRef)
                null
            }.await()
            CancelResult.Success
        } catch (e: NotReservationOwnerException) {
            CancelResult.NotOwner
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CancelResult.Failure(e.message ?: "Could not cancel the reservation")
        }
    }

    private fun slotSnapshots(): Flow<List<Slot>> = callbackFlow {
        val registration = firestore.collection(SLOTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val slots = snapshot.documents.map { doc ->
                    Slot(
                        id = doc.id,
                        name = doc.getString("name") ?: doc.id,
                        intervals = parseIntervals(doc.get(FIELD_INTERVALS)),
                    )
                }.sortedBy { it.id }
                trySend(slots)
            }
        awaitClose { registration.remove() }
    }

    private fun reservationSnapshots(uid: String): Flow<List<Reservation>> = callbackFlow {
        // Filter by userId only and sort client-side, so no composite index is needed.
        val registration = firestore.collection(RESERVATIONS)
            .whereEqualTo(FIELD_USER_ID, uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val reservations = snapshot.documents
                    .mapNotNull { it.toObject(Reservation::class.java) }
                    .sortedBy { it.startTime }
                trySend(reservations)
            }
        awaitClose { registration.remove() }
    }

    /** Creates the fixed pool of slots on first launch; no-op afterwards. */
    private suspend fun ensureSlotsSeeded() {
        val existing = firestore.collection(SLOTS).get().await().documents.map { it.id }.toSet()
        val batch = firestore.batch()
        var missing = false
        for (i in 1..SLOT_COUNT) {
            val id = "slot_%02d".format(i)
            if (id in existing) continue
            missing = true
            batch.set(
                firestore.collection(SLOTS).document(id),
                mapOf(
                    "name" to "Slot $i",
                    FIELD_INTERVALS to emptyMap<String, Any>(),
                ),
            )
        }
        if (missing) batch.commit().await()
    }

    private fun parseIntervals(raw: Any?): List<BookedInterval> {
        val map = raw as? Map<*, *> ?: return emptyList()
        return map.mapNotNull { (key, value) ->
            val reservationId = key as? String ?: return@mapNotNull null
            val fields = value as? Map<*, *> ?: return@mapNotNull null
            BookedInterval(
                reservationId = reservationId,
                userId = fields[FIELD_USER_ID] as? String ?: return@mapNotNull null,
                startTime = (fields[FIELD_START] as? Number)?.toLong() ?: return@mapNotNull null,
                endTime = (fields[FIELD_END] as? Number)?.toLong() ?: return@mapNotNull null,
            )
        }
    }

    private fun BookedInterval.toIntervalMap(): Map<String, Any> = mapOf(
        FIELD_USER_ID to userId,
        FIELD_START to startTime,
        FIELD_END to endTime,
    )
}