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
    val cropToFill: Boolean = true,
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
    val cropToFill: Boolean = true,
)

// Classes match the Intel Image Classification dataset the on-device model is
// trained on (see ml-training/). Names mirror the dataset folder names so the
// model's photo_labels.txt maps 1:1 via PhotoTag.valueOf (see PhotoMapper).
enum class PhotoTag {
    BUILDINGS,
    FOREST,
    GLACIER,
    MOUNTAIN,
    SEA,
    STREET,
    UNKNOWN,
}
