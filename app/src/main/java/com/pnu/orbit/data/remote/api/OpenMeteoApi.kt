package com.pnu.orbit.data.remote.api

import com.pnu.orbit.data.remote.dto.OpenMeteoForecastDto
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getDailyForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("daily") daily: String = "weathercode,temperature_2m_max,temperature_2m_min",
        @Query("timezone") timezone: String = "auto",
    ): OpenMeteoForecastDto
}
