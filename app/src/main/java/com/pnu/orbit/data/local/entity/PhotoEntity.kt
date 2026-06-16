package com.pnu.orbit.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId"), Index("segmentId")],
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tripId: Long,
    val segmentId: Long?,
    val uri: String,
    val takenAt: Long?,
    val lat: Double?,
    val lng: Double?,
    val locationName: String?,
    val comment: String?,
    val tag: String?,
    /** true = fill the polaroid window (centre-crop); false = fit whole photo with black bars. */
    @ColumnInfo(defaultValue = "1")
    val cropToFill: Boolean = true,
)
