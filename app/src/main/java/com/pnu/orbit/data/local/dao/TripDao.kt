package com.pnu.orbit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pnu.orbit.data.local.entity.TripEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY startDate DESC")
    fun observeTrips(): Flow<List<TripEntity>>

    @Query(
        """
        SELECT trips.*, COUNT(photos.id) AS photoCount
        FROM trips
        LEFT JOIN photos ON photos.tripId = trips.id
        GROUP BY trips.id
        ORDER BY trips.startDate DESC
        """,
    )
    fun observeTripsWithPhotoCount(): Flow<List<TripWithPhotoCount>>

    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun getTrip(tripId: Long): TripEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripEntity): Long

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun deleteTrip(tripId: Long)
}
