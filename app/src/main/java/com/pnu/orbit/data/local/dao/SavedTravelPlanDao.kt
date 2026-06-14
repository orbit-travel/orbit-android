package com.pnu.orbit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pnu.orbit.data.local.entity.SavedTravelPlanEntity

@Dao
interface SavedTravelPlanDao {
    @Query("SELECT * FROM saved_travel_plans ORDER BY createdAt DESC")
    suspend fun getAllSavedPlans(): List<SavedTravelPlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedPlan(plan: SavedTravelPlanEntity): Long

    @Query("DELETE FROM saved_travel_plans WHERE id = :id")
    suspend fun deleteSavedPlanById(id: Long)
}
