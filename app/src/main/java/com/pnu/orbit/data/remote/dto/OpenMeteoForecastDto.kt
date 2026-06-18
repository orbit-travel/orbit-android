package com.pnu.orbit.data.remote.dto

data class OpenMeteoForecastDto(
    val daily: OpenMeteoDailyDto?,
)

data class OpenMeteoDailyDto(
    val time: List<String> = emptyList(),
    val weathercode: List<Int> = emptyList(),
    val temperature_2m_max: List<Double> = emptyList(),
    val temperature_2m_min: List<Double> = emptyList(),
)
