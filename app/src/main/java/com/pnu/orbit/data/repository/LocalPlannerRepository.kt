package com.pnu.orbit.data.repository

import android.util.Log
import com.pnu.orbit.data.local.dao.PlanDao
import com.pnu.orbit.data.mapper.toDomain
import com.pnu.orbit.data.mapper.toEntity
import com.pnu.orbit.data.remote.api.AiPlannerApi
import com.pnu.orbit.data.remote.dto.AiPlanRequestDto
import com.pnu.orbit.domain.model.PlannerRequest
import com.pnu.orbit.domain.model.TravelPlan
import com.pnu.orbit.util.DemoFallbacks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalPlannerRepository(
    private val planDao: PlanDao,
    private val aiPlannerApi: AiPlannerApi,
) : PlannerRepository {
    override fun observeSavedPlans(): Flow<List<TravelPlan>> =
        planDao.observePlans().map { plans -> plans.map { it.toDomain() } }

    override suspend fun createPlan(request: PlannerRequest): TravelPlan {
        val plan = aiPlannerApi.createPlan(
            AiPlanRequestDto(
                destination = request.destination,
                days = request.days,
                style = request.style,
                companionType = request.companionType,
                budget = request.budget,
                pace = request.pace,
                latitude = request.latitude,
                longitude = request.longitude,
            ),
        ).toDomain(style = request.style)

        val savedId = planDao.insertPlan(plan.toEntity())
        return plan.copy(id = savedId)
    }

    override suspend fun getRecommendations(
        destination: String,
        style: String,
        latitude: Double,
        longitude: Double,
        radiusKm: Double
    ): List<com.pnu.orbit.domain.model.Attraction> {
        return aiPlannerApi.getRecommendations(
            destination = destination,
            style = style,
            latitude = latitude,
            longitude = longitude,
            radiusKm = radiusKm
        ).map { it.toDomain() }
    }
}
