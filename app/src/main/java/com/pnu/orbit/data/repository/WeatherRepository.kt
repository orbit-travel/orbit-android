package com.pnu.orbit.data.repository

import com.pnu.orbit.domain.model.DailyWeather

interface WeatherRepository {
    /**
     * Returns the daily forecast between [startDate] and [endDate] (inclusive, yyyy-MM-dd).
     * Never throws: returns an empty list when coordinates are missing, the range is outside
     * Open-Meteo's forecast window, or the request fails for any reason.
     */
    suspend fun getDailyForecast(
        latitude: Double?,
        longitude: Double?,
        startDate: String?,
        endDate: String?,
    ): List<DailyWeather>
}
