package com.pnu.orbit.domain.model

data class PlannerRequest(
    val destination: String,
    val days: Int,
    val style: String,
    val companionType: String? = null,
    val budget: String? = null,
    val pace: String? = null,
)
