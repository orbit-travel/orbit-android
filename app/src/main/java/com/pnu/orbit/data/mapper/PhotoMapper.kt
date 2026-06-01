package com.pnu.orbit.data.mapper

import com.pnu.orbit.data.local.entity.PhotoEntity
import com.pnu.orbit.domain.model.PhotoTag
import com.pnu.orbit.domain.model.TravelPhoto

fun PhotoEntity.toDomain(): TravelPhoto = TravelPhoto(
    id = id,
    tripId = tripId,
    uri = uri,
    takenAt = takenAt,
    lat = lat,
    lng = lng,
    comment = comment,
    tag = tag.toPhotoTag(),
)

fun TravelPhoto.toEntity(tripIdOverride: Long? = null): PhotoEntity = PhotoEntity(
    id = id,
    tripId = tripIdOverride ?: tripId,
    uri = uri,
    takenAt = takenAt,
    lat = lat,
    lng = lng,
    comment = comment,
    tag = tag.name.lowercase(),
)

private fun String?.toPhotoTag(): PhotoTag =
    runCatching { PhotoTag.valueOf(orEmpty().uppercase()) }.getOrDefault(PhotoTag.UNKNOWN)
