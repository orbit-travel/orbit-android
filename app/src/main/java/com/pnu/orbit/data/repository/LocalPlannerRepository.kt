package com.pnu.orbit.data.repository

import android.util.Log
import com.pnu.orbit.data.local.dao.PlanDao
import com.pnu.orbit.data.mapper.toDomain
import com.pnu.orbit.data.mapper.toEntity
import com.pnu.orbit.data.remote.api.AiPlannerApi
import com.pnu.orbit.data.remote.dto.AiPlanRequestDto
import com.pnu.orbit.data.remote.dto.PlannerAccommodationDto
import com.pnu.orbit.data.remote.dto.PlannerPlaceDto
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
                startDate = request.startDate,
                endDate = request.endDate,
                regions = request.regions.map {
                    PlannerPlaceDto(
                        name = it.name,
                        latitude = it.latitude,
                        longitude = it.longitude,
                    )
                },
                accommodations = request.accommodations.map {
                    PlannerAccommodationDto(
                        day = it.day,
                        name = it.name,
                        latitude = it.latitude,
                        longitude = it.longitude,
                    )
                },
                arrivalTime = request.arrivalTime,
                departureTime = request.departureTime,
                transportMode = request.transportMode,
            ),
        ).toDomain(style = request.style)
            .withoutRouteAnchorCards(request)

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

    private fun TravelPlan.withoutRouteAnchorCards(request: PlannerRequest): TravelPlan {
        val lodgingNames = request.accommodations.map { it.name.lowercase() }
        val blockedWords = listOf(
            "airport",
            "station",
            "terminal",
            "hotel",
            "hostel",
            "resort",
            "lodging",
            "accommodation",
        )
        val cleanedDays = dayPlans.map { dayPlan ->
            val cleaned = dayPlan.attractions
                .filterNot { attraction ->
                    val name = attraction.name.lowercase()
                    lodgingNames.any { lodgingName -> lodgingName.isNotBlank() && name.contains(lodgingName) } ||
                        blockedWords.any { word -> name.contains(word) }
                }
                .mapIndexed { index, attraction -> attraction.copy(sequence = index + 1) }
            dayPlan.copy(attractions = cleaned)
        }
        return copy(dayPlans = cleanedDays)
    }
}
