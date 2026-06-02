package com.pnu.orbit.data.mapper

import com.pnu.orbit.data.local.entity.PhotoEntity
import com.pnu.orbit.domain.model.NewTravelPhoto
import com.pnu.orbit.domain.model.PhotoTag
import com.pnu.orbit.domain.model.TravelPhoto

fun PhotoEntity.toDomain(): TravelPhoto = TravelPhoto(
    id = id,
    tripId = tripId,
    segmentId = segmentId,
    uri = uri,
    takenAt = takenAt,
    lat = lat,
    lng = lng,
    locationName = locationName,
    comment = comment,
    tag = tag.toPhotoTag(),
)

fun TravelPhoto.toEntity(tripIdOverride: Long? = null): PhotoEntity = PhotoEntity(
    id = id,
    tripId = tripIdOverride ?: tripId,
    segmentId = segmentId,
    uri = uri,
    takenAt = takenAt,
    lat = lat,
    lng = lng,
    locationName = locationName,
    comment = comment,
    tag = tag.name.lowercase(),
)

fun NewTravelPhoto.toEntity(tripId: Long, segmentId: Long?): PhotoEntity = PhotoEntity(
    id = 0L,
    tripId = tripId,
    segmentId = segmentId,
    uri = uri,
    takenAt = takenAt,
    lat = lat,
    lng = lng,
    locationName = locationName,
    comment = comment,
    tag = tag.name.lowercase(),
)

private fun String?.toPhotoTag(): PhotoTag =
    runCatching { PhotoTag.valueOf(orEmpty().uppercase()) }.getOrDefault(PhotoTag.UNKNOWN)
