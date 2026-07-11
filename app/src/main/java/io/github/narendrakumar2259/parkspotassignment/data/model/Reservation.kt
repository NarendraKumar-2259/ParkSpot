package io.github.narendrakumar2259.parkspotassignment.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Reservation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val slotId: String = "",
    val slotName: String = "",
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    @ServerTimestamp val createdAt: Date? = null,
)
