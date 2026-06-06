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
    val attractions: List<Attraction>,
)

data class Attraction(
    val sequence: Int,
    val name: String,
    val description: String,
    val imageUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
