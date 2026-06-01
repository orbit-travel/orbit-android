package com.pnu.orbit.data.remote.client

import com.pnu.orbit.data.remote.api.AiPlannerApi
import com.pnu.orbit.data.remote.api.WeatherApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val DEMO_BASE_URL = "https://example.com/"

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(DEMO_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val aiPlannerApi: AiPlannerApi by lazy {
        retrofit.create(AiPlannerApi::class.java)
    }

    val weatherApi: WeatherApi by lazy {
        retrofit.create(WeatherApi::class.java)
    }
}
