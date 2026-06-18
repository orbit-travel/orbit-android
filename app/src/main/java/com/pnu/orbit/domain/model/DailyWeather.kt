package com.pnu.orbit.domain.model

data class DailyWeather(
    val date: String, // yyyy-MM-dd
    val weatherCode: Int,
    val tempMaxC: Double?,
    val tempMinC: Double?,
)
