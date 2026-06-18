package com.pnu.orbit.data.remote.dto

data class AiPlanRequestDto(
    val destination: String,
    val days: Int,
    val style: String,
    val companionType: String?,
    val budget: String?,
    val pace: String?,
    val latitude: Double?,
    val longitude: Double?,
    val startDate: String?,
    val endDate: String?,
    val regions: List<PlannerPlaceDto>,
    val accommodations: List<PlannerAccommodationDto>,
    val arrivalTime: String?,
    val departureTime: String?,
    val transportMode: String?,
    val weatherSummary: String? = null,
)

data class PlannerPlaceDto(
    val name: String,
    val latitude: Double?,
    val longitude: Double?,
)

data class PlannerAccommodationDto(
    val day: Int,
    val name: String,
    val latitude: Double?,
    val longitude: Double?,
)
