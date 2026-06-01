package com.pnu.orbit.domain.model

data class TravelPlan(
    val id: Long,
    val destination: String,
    val days: Int,
    val style: String,
    val dayPlans: List<DayPlan>,
    val createdAt: Long,
    val isFallback: Boolean,
)

data class DayPlan(
    val day: Int,
    val morning: String,
    val lunch: String,
    val afternoon: String,
    val evening: String,
)
