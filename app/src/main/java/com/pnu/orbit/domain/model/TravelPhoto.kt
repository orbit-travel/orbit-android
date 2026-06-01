package com.pnu.orbit.domain.model

data class TravelPhoto(
    val id: Long,
    val tripId: Long,
    val uri: String,
    val takenAt: Long?,
    val lat: Double?,
    val lng: Double?,
    val comment: String?,
    val tag: PhotoTag,
) {
    val hasLocation: Boolean = lat != null && lng != null
}

enum class PhotoTag {
    CITY,
    SEA,
    MOUNTAIN,
    FOOD,
    NIGHT,
    LANDMARK,
    UNKNOWN,
}
