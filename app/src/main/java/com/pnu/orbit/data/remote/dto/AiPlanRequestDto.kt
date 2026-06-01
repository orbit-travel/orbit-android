package com.pnu.orbit.data.remote.dto

data class AiPlanRequestDto(
    val destination: String,
    val days: Int,
    val style: String,
    val companionType: String?,
    val budget: String?,
    val pace: String?,
)
