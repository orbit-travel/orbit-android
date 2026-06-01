package com.pnu.orbit.data.remote.api

import com.pnu.orbit.data.remote.dto.WeatherDto
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApi {
    @GET("weather")
    suspend fun getWeather(@Query("destination") destination: String): WeatherDto
}
