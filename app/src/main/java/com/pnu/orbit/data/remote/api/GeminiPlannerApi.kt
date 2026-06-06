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
            Create a highly optimized, realistic travel itinerary for ${request.destination} for ${request.days} days.
            Travel style: ${request.style}.
            ${if (!request.companionType.isNullOrBlank()) "Companion type: ${request.companionType}." else ""}
            ${if (!request.budget.isNullOrBlank()) "Budget: ${request.budget}." else ""}
            ${if (!request.pace.isNullOrBlank()) "Travel pace: ${request.pace}." else ""}
            
            IMPORTANT RULES:
            1. Recommend specific, real, and well-known tourist attractions (관광지) that physically exist. For example, if destination is "Busan" and style is "extreme", recommend actual places like "롯데월드 어드벤처 부산", "스카이라인 루지 기장", or "송도해상케이블카" instead of vague terms.
            2. Optimize the visiting sequence (동선) to minimize travel time between spots each day.
            3. Write all attraction names ("name") and descriptions ("description") in Korean.
            4. Provide highly accurate coordinates ("latitude", "longitude") for each attraction (vital for plotting on Google Maps).
            5. Provide a valid, working travel image URL ("imageUrl") representing the attraction or place. (You can use high-quality Unsplash image URLs: e.g. "https://images.unsplash.com/photo-..." or similar public images, ensuring it is a direct image URL).
            
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

        return gson.fromJson(cleanJson, AiPlanResponseDto::class.java)
    }
}
