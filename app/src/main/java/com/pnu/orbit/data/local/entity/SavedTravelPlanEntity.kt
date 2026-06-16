package com.pnu.orbit.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_travel_plans")
data class SavedTravelPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val destination: String,
    val days: Int,
    val style: String,
    val planJson: String,
    val startDate: String, // format yyyy-MM-dd
    val createdAt: Long,
    val color: Int = 0,
)
