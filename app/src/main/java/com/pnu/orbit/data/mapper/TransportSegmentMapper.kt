package com.pnu.orbit.data.mapper

import com.pnu.orbit.data.local.entity.TransportSegmentEntity
import com.pnu.orbit.domain.model.TransportSegment
import com.pnu.orbit.domain.model.TransportType

fun TransportSegmentEntity.toDomain(): TransportSegment = TransportSegment(
    id = id,
    tripId = tripId,
    type = type.toTransportType(),
    departureName = departureName,
    departureLat = departureLat,
    departureLng = departureLng,
    arrivalName = arrivalName,
    arrivalLat = arrivalLat,
    arrivalLng = arrivalLng,
    sortOrder = sortOrder,
)

fun TransportSegment.toEntity(tripIdOverride: Long? = null): TransportSegmentEntity =
    TransportSegmentEntity(
        id = id,
        tripId = tripIdOverride ?: tripId,
        type = type.name.lowercase(),
        departureName = departureName,
        departureLat = departureLat,
        departureLng = departureLng,
        arrivalName = arrivalName,
        arrivalLat = arrivalLat,
        arrivalLng = arrivalLng,
        sortOrder = sortOrder,
    )

private fun String?.toTransportType(): TransportType =
    runCatching { TransportType.valueOf(orEmpty().uppercase()) }
        .getOrDefault(TransportType.FLIGHT)
