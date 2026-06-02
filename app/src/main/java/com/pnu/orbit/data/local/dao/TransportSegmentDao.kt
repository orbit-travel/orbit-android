package com.pnu.orbit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pnu.orbit.data.local.entity.TransportSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransportSegmentDao {
    @Query("SELECT * FROM transport_segments WHERE tripId = :tripId ORDER BY sortOrder ASC")
    fun observeSegments(tripId: Long): Flow<List<TransportSegmentEntity>>

    @Query("SELECT * FROM transport_segments WHERE tripId = :tripId ORDER BY sortOrder ASC")
    suspend fun getSegments(tripId: Long): List<TransportSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<TransportSegmentEntity>): List<Long>

    @Query("DELETE FROM transport_segments WHERE tripId = :tripId")
    suspend fun deleteSegments(tripId: Long)
}
