package com.pnu.orbit.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val destination: String,
    val days: Int,
    val style: String,
    val planJson: String,
    val createdAt: Long,
)
