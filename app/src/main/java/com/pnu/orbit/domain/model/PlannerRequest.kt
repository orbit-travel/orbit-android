package com.pnu.orbit.domain.model

data class PlannerRequest(
    val destination: String,
    val days: Int,
    val style: String,
    val companionType: String? = null,
    val budget: String? = null,
    val pace: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val regions: List<PlannerPlace> = emptyList(),
    val accommodations: List<PlannerAccommodation> = emptyList(),
    val arrivalTime: String? = null,
    val departureTime: String? = null,
    val transportMode: String? = null,
)

data class PlannerPlace(
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class PlannerAccommodation(
    val day: Int,
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
