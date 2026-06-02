package com.pnu.orbit.domain.model

data class TravelPhoto(
    val id: Long,
    val tripId: Long,
    val segmentId: Long?,
    val uri: String,
    val takenAt: Long?,
    val lat: Double?,
    val lng: Double?,
    val locationName: String?,
    val comment: String?,
    val tag: PhotoTag,
) {
    val hasLocation: Boolean = lat != null && lng != null
}

data class NewTravelPhoto(
    val segmentSortOrder: Int?,
    val uri: String,
    val takenAt: Long?,
    val lat: Double?,
    val lng: Double?,
    val locationName: String?,
    val comment: String?,
    val tag: PhotoTag,
)

enum class PhotoTag {
    CITY,
    SEA,
    MOUNTAIN,
    FOOD,
    NIGHT,
    LANDMARK,
    UNKNOWN,
}
