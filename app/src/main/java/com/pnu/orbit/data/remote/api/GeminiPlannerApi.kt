package com.pnu.orbit.data.remote.api

import com.google.gson.Gson
import com.pnu.orbit.data.remote.dto.AiPlanRequestDto
import com.pnu.orbit.data.remote.dto.AiPlanResponseDto
import com.pnu.orbit.data.remote.dto.GeminiContent
import com.pnu.orbit.data.remote.dto.GeminiGenerationConfig
import com.pnu.orbit.data.remote.dto.GeminiPart
import com.pnu.orbit.data.remote.dto.GeminiRequest

class GeminiPlannerApi(
    private val geminiApi: GeminiApi,
    private val apiKey: String
) : AiPlannerApi {

    private val gson = Gson()

    override suspend fun createPlan(request: AiPlanRequestDto): AiPlanResponseDto {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("Gemini API key is missing. Fallback to demo plan.")
        }

        val prompt = """
            Create a travel itinerary for ${request.destination} for ${request.days} days.
            Travel style: ${request.style}.
            ${if (!request.companionType.isNullOrBlank()) "Companion type: ${request.companionType}." else ""}
            ${if (!request.budget.isNullOrBlank()) "Budget: ${request.budget}." else ""}
            ${if (!request.pace.isNullOrBlank()) "Travel pace: ${request.pace}." else ""}
            
            Return JSON only with this structure:
            {
              "destination": "${request.destination}",
              "days": [
                {
                  "day": 1,
                  "morning": "Morning itinerary detail...",
                  "lunch": "Lunch itinerary detail...",
                  "afternoon": "Afternoon itinerary detail...",
                  "evening": "Evening itinerary detail..."
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
        val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("Empty response from Gemini API")

        return gson.fromJson(jsonText, AiPlanResponseDto::class.java)
    }
}
