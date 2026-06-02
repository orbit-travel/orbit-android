package com.pnu.orbit.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transport_segments",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId")],
)
data class TransportSegmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val tripId: Long,
    val type: String,
    val departureName: String,
    val departureLat: Double?,
    val departureLng: Double?,
    val arrivalName: String,
    val arrivalLat: Double?,
    val arrivalLng: Double?,
    val sortOrder: Int,
)
