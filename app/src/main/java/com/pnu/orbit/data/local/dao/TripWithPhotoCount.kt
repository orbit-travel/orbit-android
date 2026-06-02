package com.pnu.orbit.data.local.dao

import androidx.room.Embedded
import com.pnu.orbit.data.local.entity.TripEntity

data class TripWithPhotoCount(
    @Embedded val trip: TripEntity,
    val photoCount: Int,
)
