package com.pnu.orbit.data.mapper

import com.pnu.orbit.data.local.entity.TripEntity
import com.pnu.orbit.domain.model.Trip

fun TripEntity.toDomain(photoCount: Int = 0): Trip = Trip(
    id = id,
    title = title,
    startPlace = startPlace,
    destination = destination,
    startDate = startDate,
    endDate = endDate,
    coverPhotoUri = coverPhotoUri,
    memo = memo,
    photoCount = photoCount,
)

fun Trip.toEntity(): TripEntity = TripEntity(
    id = id,
    title = title,
    startPlace = startPlace,
    destination = destination,
    startDate = startDate,
    endDate = endDate,
    coverPhotoUri = coverPhotoUri,
    memo = memo,
)
