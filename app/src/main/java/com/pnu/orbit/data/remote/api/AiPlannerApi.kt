package com.pnu.orbit.data.remote.api

import com.pnu.orbit.data.remote.dto.AiPlanRequestDto
import com.pnu.orbit.data.remote.dto.AiPlanResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

interface AiPlannerApi {
    @POST("travel/plan")
    suspend fun createPlan(@Body request: AiPlanRequestDto): AiPlanResponseDto

    suspend fun getRecommendations(
        destination: String,
        style: String,
        latitude: Double,
        longitude: Double,
        radiusKm: Double
    ): List<com.pnu.orbit.data.remote.dto.AttractionDto>
}
