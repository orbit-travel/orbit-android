package com.pnu.orbit.data.remote.client

import com.pnu.orbit.data.remote.api.AiPlannerApi
import com.pnu.orbit.data.remote.api.GeminiApi
import com.pnu.orbit.data.remote.api.WeatherApi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val DEMO_BASE_URL = "https://example.com/"

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(DEMO_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private class RateLimitRetryInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            var response = chain.proceed(request)
            var tryCount = 0
            val maxLimit = 5
            var delayTime = 3000L // initial delay of 3 seconds

            while (!response.isSuccessful && response.code == 429 && tryCount < maxLimit) {
                tryCount++
                val retryAfter = response.header("Retry-After")?.toLongOrNull()?.times(1000L)
                val sleepTime = retryAfter ?: delayTime
                response.close() // Close the response body to avoid resource leak
                
                try {
                    Thread.sleep(sleepTime)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
                
                if (retryAfter == null) {
                    delayTime *= 2 // Exponential backoff (3s -> 6s -> 12s -> 24s -> 48s)
                }
                response = chain.proceed(request)
            }
            return response
        }
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(RateLimitRetryInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val geminiRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val aiPlannerApi: AiPlannerApi by lazy {
        retrofit.create(AiPlannerApi::class.java)
    }

    val weatherApi: WeatherApi by lazy {
        retrofit.create(WeatherApi::class.java)
    }

    val geminiApi: GeminiApi by lazy {
        geminiRetrofit.create(GeminiApi::class.java)
    }
}
