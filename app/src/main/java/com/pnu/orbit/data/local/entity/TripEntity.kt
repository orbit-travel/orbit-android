package com.pnu.orbit.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val startPlace: String,
    val destination: String,
    val startDate: Long,
    val endDate: Long,
    val coverPhotoUri: String?,
    val memo: String?,
)
