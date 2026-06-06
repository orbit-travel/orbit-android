package com.pnu.orbit.data.remote.dto

data class AiPlanResponseDto(
    val destination: String,
    val days: List<DayPlanDto>,
)

data class DayPlanDto(
    val day: Int,
    val attractions: List<AttractionDto>,
)

data class AttractionDto(
    val sequence: Int,
    val name: String,
    val description: String,
    val imageUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
