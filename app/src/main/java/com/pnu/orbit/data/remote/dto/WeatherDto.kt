package com.pnu.orbit.data.remote.dto

data class WeatherDto(
    val destination: String,
    val summary: String,
    val temperatureCelsius: Double?,
)
