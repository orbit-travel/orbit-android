package com.pnu.orbit.util

/**
 * Maps Open-Meteo's WMO weather codes (https://open-meteo.com/en/docs) to a display
 * emoji and a short English description used in the Gemini prompt.
 */
object WeatherCode {
    fun emojiFor(code: Int): String = when (code) {
        0 -> "☀️" // clear sky
        1, 2, 3 -> "⛅" // mainly clear / partly cloudy / overcast
        45, 48 -> "🌫️" // fog
        51, 53, 55, 56, 57 -> "🌦️" // drizzle
        61, 63, 65, 66, 67 -> "🌧️" // rain
        71, 73, 75, 77 -> "🌨️" // snow
        80, 81, 82 -> "🌦️" // rain showers
        85, 86 -> "🌨️" // snow showers
        95, 96, 99 -> "⛈️" // thunderstorm
        else -> "⛅"
    }

    fun descriptionFor(code: Int): String = when (code) {
        0 -> "clear sky"
        1, 2 -> "partly cloudy"
        3 -> "overcast"
        45, 48 -> "fog"
        51, 53, 55, 56, 57 -> "drizzle"
        61, 63, 65, 66, 67 -> "rain"
        71, 73, 75, 77 -> "snow"
        80, 81, 82 -> "rain showers"
        85, 86 -> "snow showers"
        95, 96, 99 -> "thunderstorm"
        else -> "unknown"
    }
}
