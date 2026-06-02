package com.pnu.orbit.domain.model

data class TransportSegment(
    val id: Long = 0L,
    val tripId: Long = 0L,
    val type: TransportType,
    val departureName: String,
    val departureLat: Double?,
    val departureLng: Double?,
    val arrivalName: String,
    val arrivalLat: Double?,
    val arrivalLng: Double?,
    val sortOrder: Int,
)

enum class TransportType {
    FLIGHT,
    TRAIN,
    CAR,
    ACCOMMODATION,
}
