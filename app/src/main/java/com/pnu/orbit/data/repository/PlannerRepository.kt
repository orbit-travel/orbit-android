package com.pnu.orbit.data.repository

import com.pnu.orbit.domain.model.PlannerRequest
import com.pnu.orbit.domain.model.TravelPlan
import kotlinx.coroutines.flow.Flow

interface PlannerRepository {
    fun observeSavedPlans(): Flow<List<TravelPlan>>
    suspend fun createPlan(request: PlannerRequest): TravelPlan
    suspend fun getRecommendations(
        destination: String,
        style: String,
        latitude: Double,
        longitude: Double,
        radiusKm: Double
    ): List<com.pnu.orbit.domain.model.Attraction>
}
