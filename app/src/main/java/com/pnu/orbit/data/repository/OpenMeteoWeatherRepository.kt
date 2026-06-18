package com.pnu.orbit.data.repository

import android.util.Log
import com.pnu.orbit.data.remote.api.OpenMeteoApi
import com.pnu.orbit.domain.model.DailyWeather
import java.time.LocalDate
import java.time.format.DateTimeParseException

class OpenMeteoWeatherRepository(
    private val openMeteoApi: OpenMeteoApi,
) : WeatherRepository {

    override suspend fun getDailyForecast(
        latitude: Double?,
        longitude: Double?,
        startDate: String?,
        endDate: String?,
    ): List<DailyWeather> {
        if (latitude == null || longitude == null || (latitude == 0.0 && longitude == 0.0)) {
            return emptyList()
        }
        val start = startDate?.let(::parseDateOrNull) ?: return emptyList()
        val requestedEnd = endDate?.let(::parseDateOrNull) ?: start
        val today = LocalDate.now()
        val forecastLimit = today.plusDays(FORECAST_RANGE_DAYS - 1)
        if (start.isAfter(forecastLimit)) {
            return emptyList()
        }
        val clampedEnd = if (requestedEnd.isAfter(forecastLimit)) forecastLimit else requestedEnd

        return try {
            val response = openMeteoApi.getDailyForecast(
                latitude = latitude,
                longitude = longitude,
                startDate = start.toString(),
                endDate = clampedEnd.toString(),
            )
            val daily = response.daily ?: return emptyList()
            daily.time.indices.map { index ->
                DailyWeather(
                    date = daily.time[index],
                    weatherCode = daily.weathercode.getOrElse(index) { -1 },
                    tempMaxC = daily.temperature_2m_max.getOrNull(index),
                    tempMinC = daily.temperature_2m_min.getOrNull(index),
                )
            }
        } catch (e: Exception) {
            Log.w("OpenMeteoWeatherRepository", "Failed to fetch forecast for ($latitude, $longitude)", e)
            emptyList()
        }
    }

    private fun parseDateOrNull(date: String): LocalDate? =
        try {
            LocalDate.parse(date)
        } catch (e: DateTimeParseException) {
            null
        }

    companion object {
        private const val FORECAST_RANGE_DAYS = 16L
    }
}
