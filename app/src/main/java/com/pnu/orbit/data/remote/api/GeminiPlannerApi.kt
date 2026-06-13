package com.pnu.orbit.data.remote.api

import com.google.gson.Gson
import com.pnu.orbit.data.remote.dto.AiPlanRequestDto
import com.pnu.orbit.data.remote.dto.AiPlanResponseDto
import com.pnu.orbit.data.remote.dto.GeminiContent
import com.pnu.orbit.data.remote.dto.GeminiGenerationConfig
import com.pnu.orbit.data.remote.dto.GeminiPart
import com.pnu.orbit.data.remote.dto.GeminiRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GeminiPlannerApi(
    private val geminiApi: GeminiApi,
    private val apiKey: String
) : AiPlannerApi {

    private val gson = Gson()
    private val mutex = Mutex()

    override suspend fun createPlan(request: AiPlanRequestDto): AiPlanResponseDto = mutex.withLock {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("Gemini API key is missing. Fallback to demo plan.")
        }

        val coordinatesInfo = if (request.latitude != null && request.longitude != null) {
            """
            - The exact center coordinates of the destination are: latitude ${request.latitude}, longitude ${request.longitude} (representing '${request.destination}').
            - GEOGRAPHICAL VICINITY CONSTRAINT: Every recommended place (attractions, landmarks, restaurants, cafes) MUST be located strictly within a 2-3km radius around these center coordinates. Do not suggest anything outside this immediate vicinity.
            """.trimIndent()
        } else {
            "The target destination is '${request.destination}'. Recommends spots strictly within this area."
        }

        val prompt = """
            Create a highly optimized, realistic travel itinerary for '${request.destination}' for ${request.days} days.
            Travel style preferences: ${request.style}.
            ${if (!request.companionType.isNullOrBlank()) "Companion type: ${request.companionType}." else ""}
            ${if (!request.budget.isNullOrBlank()) "Budget: ${request.budget}." else ""}
            ${if (!request.pace.isNullOrBlank()) "Travel pace: ${request.pace}." else ""}
            
            ${coordinatesInfo}
            
            CRITICAL RULES:
            1. STRICT DAY-BY-DAY UNIQUENESS (DO NOT REPEAT): Every day in the itinerary (from Day 1 to Day ${request.days}) MUST feature a completely unique, non-overlapping set of places (attractions and restaurants). Absolutely DO NOT repeat any place, attraction, cafe, or restaurant across different days.
            2. REAL & GOOGLE MAPS REGISTERED PLACES ONLY: Recommend specific, real, and well-known tourist attractions, landmarks, restaurants, or cafes that physically exist and are registered on Google Maps. Do NOT hallucinate, fabricate, or suggest fake/non-existent places or estimated place names.
            3. LANDMARK & RESTAURANT/CAFE MIX: For each day, include a balanced mix of 2-3 tourist landmarks/attractions and at least 1-2 famous local restaurants or cafes matching the selected travel style preferences '${request.style}'.
            4. ACCURATE COORDINATES: Provide highly precise, authentic latitude and longitude coordinates for each recommended place (so they map correctly without errors on Google Maps).
            5. Korean Language: Write all attraction/restaurant names ("name") and descriptions ("description") in natural Korean.
            6. Image URL: Provide a valid, working travel image URL ("imageUrl") representing the attraction or place. (Use high-quality Unsplash image URLs: e.g., "https://images.unsplash.com/photo-..." or similar public image URLs).
            Return JSON only with this structure:
            {
              "destination": "${request.destination}",
              "days": [
                {
                  "day": 1,
                  "attractions": [
                    {
                      "sequence": 1,
                      "name": "구체적인 관광지 이름",
                      "description": "관광지에 대한 상세 설명 및 추천 이유, 이동 동선 팁 (한국어)",
                      "imageUrl": "https://images.unsplash.com/photo-...",
                      "latitude": 35.1952,
                      "longitude": 129.2135
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val geminiRequest = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = prompt)
                    )
                )
            ),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json"
            )
        )

        val response = geminiApi.generateContent(apiKey, geminiRequest)
        val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("Empty response from Gemini API")

        val cleanJson = if (rawJson.trim().startsWith("```")) {
            rawJson.trim()
                .substringAfter("```json")
                .substringAfter("```")
                .substringBeforeLast("```")
                .trim()
        } else {
            rawJson.trim()
        }

        gson.fromJson(cleanJson, AiPlanResponseDto::class.java)
    }

    override suspend fun getRecommendations(
        destination: String,
        style: String,
        latitude: Double,
        longitude: Double,
        radiusKm: Double
    ): List<com.pnu.orbit.data.remote.dto.AttractionDto> = mutex.withLock {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("Gemini API key is missing.")
        }

        val prompt = """
            Recommend 5-8 tourist attractions, famous local landmarks, restaurants, or cafes near coordinates (latitude ${latitude}, longitude ${longitude}) representing '${destination}' strictly within a ${radiusKm}km radius.
            Filter recommendations to match the travel style preferences: '${style}'.
            
            Return JSON only with this structure:
            {
              "recommendations": [
                {
                  "name": "Specific place name in Korean",
                  "description": "Short description and recommendation reason (Korean, 2-3 sentences)",
                  "imageUrl": "https://images.unsplash.com/photo-...",
                  "latitude": 35.1234,
                  "longitude": 129.5678
                }
              ]
            }
            Ensure all names and descriptions are written in natural, fluent Korean. 
            Do NOT include markdown block markers (like ```json), just return raw JSON text.
        """.trimIndent()

        val geminiRequest = com.pnu.orbit.data.remote.dto.GeminiRequest(
            contents = listOf(
                com.pnu.orbit.data.remote.dto.GeminiContent(
                    parts = listOf(
                        com.pnu.orbit.data.remote.dto.GeminiPart(text = prompt)
                    )
                )
            ),
            generationConfig = com.pnu.orbit.data.remote.dto.GeminiGenerationConfig(
                responseMimeType = "application/json"
            )
        )

        val response = geminiApi.generateContent(apiKey, geminiRequest)
        val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("Empty response from Gemini API")

        val cleanJson = if (rawJson.trim().startsWith("```")) {
            rawJson.trim()
                .substringAfter("```json")
                .substringAfter("```")
                .substringBeforeLast("```")
                .trim()
        } else {
            rawJson.trim()
        }

        data class RecommendationListDto(val recommendations: List<com.pnu.orbit.data.remote.dto.AttractionDto>)
        val parsed = gson.fromJson(cleanJson, RecommendationListDto::class.java)
        
        parsed.recommendations.mapIndexed { index, attr ->
            attr.copy(sequence = index + 1)
        }
    }
}
