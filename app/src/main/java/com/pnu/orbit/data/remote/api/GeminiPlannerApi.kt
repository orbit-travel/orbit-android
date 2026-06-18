package com.pnu.orbit.data.remote.api

import com.google.gson.Gson
import com.pnu.orbit.data.remote.dto.AiPlanRequestDto
import com.pnu.orbit.data.remote.dto.AiPlanResponseDto
import com.pnu.orbit.data.remote.dto.AttractionDto
import com.pnu.orbit.data.remote.dto.GeminiContent
import com.pnu.orbit.data.remote.dto.GeminiGenerationConfig
import com.pnu.orbit.data.remote.dto.GeminiPart
import com.pnu.orbit.data.remote.dto.GeminiRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GeminiPlannerApi(
    private val geminiApi: GeminiApi,
    private val apiKey: String,
) : AiPlannerApi {

    private val gson = Gson()
    private val mutex = Mutex()

    override suspend fun createPlan(request: AiPlanRequestDto): AiPlanResponseDto = mutex.withLock {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("Gemini API key is missing.")
        }

        val prompt = buildPlanPrompt(request)
        val rawJson = requestJson(prompt)
        gson.fromJson(cleanJson(rawJson), AiPlanResponseDto::class.java)
    }

    override suspend fun getRecommendations(
        destination: String,
        style: String,
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
    ): List<AttractionDto> = mutex.withLock {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("Gemini API key is missing.")
        }

        val prompt = """
            Recommend 5-8 real Google Maps places near $destination.
            Center: $latitude, $longitude. Radius: ${radiusKm}km.
            Style: $style.
            Return compact JSON only:
            {
              "recommendations": [
                {
                  "name": "Place name",
                  "description": "Short keyword reason",
                  "latitude": 35.1234,
                  "longitude": 129.5678
                }
              ]
            }
        """.trimIndent()

        data class RecommendationListDto(val recommendations: List<AttractionDto>)

        val parsed = gson.fromJson(cleanJson(requestJson(prompt)), RecommendationListDto::class.java)
        parsed.recommendations.mapIndexed { index, attr ->
            attr.copy(sequence = index + 1, imageUrl = null)
        }
    }

    private fun buildPlanPrompt(request: AiPlanRequestDto): String {
        val regionsInfo = if (request.regions.isNotEmpty()) {
            request.regions.joinToString(separator = "\n") { place ->
                "- ${place.name} (${place.latitude ?: "unknown"}, ${place.longitude ?: "unknown"})"
            }
        } else if (request.latitude != null && request.longitude != null) {
            "- ${request.destination} (${request.latitude}, ${request.longitude})"
        } else {
            "- ${request.destination} (coordinates unknown)"
        }

        val accommodationInfo = if (request.accommodations.isEmpty()) {
            "No lodging was provided. Use each region center as the base."
        } else {
            request.accommodations.joinToString(separator = "\n") { lodging ->
                "- Night ${lodging.day} after Day ${lodging.day}: ${lodging.name} (${lodging.latitude ?: "unknown"}, ${lodging.longitude ?: "unknown"})"
            }
        }

        val arrivalTime = request.arrivalTime?.takeIf { it.isNotBlank() } ?: "12:00 on day 1"
        val departureTime = request.departureTime?.takeIf { it.isNotBlank() } ?: "12:00 on the last day"
        val transportMode = request.transportMode?.takeIf { it.isNotBlank() } ?: "not specified"
        val dateRange = listOfNotNull(request.startDate, request.endDate)
            .joinToString(" to ")
            .ifBlank { "${request.days} days" }
        val weatherInfo = request.weatherSummary?.takeIf { it.isNotBlank() }

        return """
            Build a realistic ${request.days}-day itinerary for ${request.destination}.
            Dates: $dateRange.
            Required regions in order:
            $regionsInfo
            Style categories: ${request.style}.
            Arrival time at local airport/station: $arrivalTime. If defaulted, start day 1 after lunch.
            Departure time from local airport/station: $departureTime. If defaulted, keep the last day light before lunch.
            Main transport: $transportMode.
            Lodging for route planning only, never as itinerary cards:
            $accommodationInfo
            ${if (weatherInfo != null) "\n            Actual daily forecast (use this, it is real data):\n            $weatherInfo\n            Prefer indoor/covered attractions on rain/snow/thunderstorm days and prioritize outdoor attractions on clear/partly cloudy days.\n" else ""}
            Use current public knowledge when helpful for seasonal events, festivals, closures${if (weatherInfo == null) ", and likely weather for the dates" else ""}.
            Prefer famous first-time visitor routes, then weight the selected style categories.
            Day 1 starts at the arrival airport/station after immigration/baggage and a realistic 1-2 hour transfer into the city.
            Days 2..${request.days} start from the previous night's lodging when lodging exists; otherwise start from the region center.
            The last day must leave enough transfer time back to the departure airport/station.
            Airports, stations, and lodging are route anchors only. Do NOT include airports, stations, hotels, lodging, or accommodations as attraction cards.
            Optimize route order around lodging/region anchors and the main transport.
            Keep each day realistic: 4 to 6 stops, fewer on arrival/departure days.
            Do not repeat the same place across days.
            Use real Google Maps places only.
            Return compact JSON only. No markdown. No prose.
            Use concise English keywords/descriptions, max 12 words per description.
            Do not include image URLs.
            Each day object must include a "region" field naming which region that day belongs to.
            Use the exact same string as listed in "Required regions in order" above. If a day is a
            day trip to a place not in that list (for example a side trip from a base city), use the
            actual visited city's name instead.
            Return exactly this structure:
            {
              "destination": "${request.destination}",
              "days": [
                {
                  "day": 1,
                  "region": "${request.regions.firstOrNull()?.name ?: request.destination}",
                  "attractions": [
                    {
                      "sequence": 1,
                      "name": "Place name",
                      "description": "Short keyword reason",
                      "latitude": 35.1952,
                      "longitude": 129.2135
                    }
                  ]
                }
              ]
            }
            The "days" array must contain exactly ${request.days} day objects numbered 1 through ${request.days}.
        """.trimIndent()
    }

    private suspend fun requestJson(prompt: String): String {
        val geminiRequest = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt)),
                ),
            ),
            generationConfig = GeminiGenerationConfig(responseMimeType = "application/json"),
        )

        val response = geminiApi.generateContent(apiKey, geminiRequest)
        return response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("Empty response from Gemini API")
    }

    private fun cleanJson(rawJson: String): String =
        if (rawJson.trim().startsWith("```")) {
            rawJson.trim()
                .substringAfter("```json")
                .substringAfter("```")
                .substringBeforeLast("```")
                .trim()
        } else {
            rawJson.trim()
        }
}
