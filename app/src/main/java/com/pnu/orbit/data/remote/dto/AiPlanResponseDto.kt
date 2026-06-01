package com.pnu.orbit.data.remote.dto

data class AiPlanResponseDto(
    val destination: String,
    val days: List<DayPlanDto>,
)

data class DayPlanDto(
    val day: Int,
    val morning: String,
    val lunch: String,
    val afternoon: String,
    val evening: String,
)
