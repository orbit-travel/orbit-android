package com.pnu.orbit.data.repository

import android.util.Log
import com.pnu.orbit.data.local.dao.PlanDao
import com.pnu.orbit.data.mapper.toDomain
import com.pnu.orbit.data.mapper.toEntity
import com.pnu.orbit.data.remote.api.AiPlannerApi
import com.pnu.orbit.data.remote.dto.AiPlanRequestDto
import com.pnu.orbit.data.remote.dto.PlannerAccommodationDto
import com.pnu.orbit.data.remote.dto.PlannerPlaceDto
import com.pnu.orbit.domain.model.DailyWeather
import com.pnu.orbit.domain.model.DayPlan
import com.pnu.orbit.domain.model.PlannerPlace
import com.pnu.orbit.domain.model.PlannerRequest
import com.pnu.orbit.domain.model.TravelPlan
import com.pnu.orbit.util.DemoFallbacks
import com.pnu.orbit.util.WeatherCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class LocalPlannerRepository(
    private val planDao: PlanDao,
    private val aiPlannerApi: AiPlannerApi,
    private val weatherRepository: WeatherRepository,
) : PlannerRepository {
    override fun observeSavedPlans(): Flow<List<TravelPlan>> =
        planDao.observePlans().map { plans -> plans.map { it.toDomain() } }

    override suspend fun createPlan(request: PlannerRequest): TravelPlan {
        val weatherByRegion = fetchWeatherByRegion(request)
        val weatherSummary = buildWeatherSummary(weatherByRegion)

        val plan = aiPlannerApi.createPlan(
            AiPlanRequestDto(
                destination = request.destination,
                days = request.days,
                style = request.style,
                companionType = request.companionType,
                budget = request.budget,
                pace = request.pace,
                latitude = request.latitude,
                longitude = request.longitude,
                startDate = request.startDate,
                endDate = request.endDate,
                regions = request.regions.map {
                    PlannerPlaceDto(
                        name = it.name,
                        latitude = it.latitude,
                        longitude = it.longitude,
                    )
                },
                accommodations = request.accommodations.map {
                    PlannerAccommodationDto(
                        day = it.day,
                        name = it.name,
                        latitude = it.latitude,
                        longitude = it.longitude,
                    )
                },
                arrivalTime = request.arrivalTime,
                departureTime = request.departureTime,
                transportMode = request.transportMode,
                weatherSummary = weatherSummary,
            ),
        ).toDomain(style = request.style)
            .withoutRouteAnchorCards(request)
            .withResolvedWeather(request, weatherByRegion)

        val savedId = planDao.insertPlan(plan.toEntity())
        return plan.copy(id = savedId)
    }

    /** Pre-fetches each region's daily forecast for the full trip range. Never throws. */
    private suspend fun fetchWeatherByRegion(request: PlannerRequest): Map<String, List<DailyWeather>> {
        if (request.regions.isEmpty() || request.startDate == null) return emptyMap()
        return try {
            request.regions.associate { region ->
                region.name to weatherRepository.getDailyForecast(
                    latitude = region.latitude,
                    longitude = region.longitude,
                    startDate = request.startDate,
                    endDate = request.endDate ?: request.startDate,
                )
            }.filterValues { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w("LocalPlannerRepository", "Failed to pre-fetch weather for regions", e)
            emptyMap()
        }
    }

    private fun buildWeatherSummary(weatherByRegion: Map<String, List<DailyWeather>>): String? {
        if (weatherByRegion.isEmpty()) return null
        return weatherByRegion.entries.joinToString(separator = "\n\n") { (region, days) ->
            val lines = days.joinToString(separator = "\n") { day ->
                val temps = listOfNotNull(day.tempMaxC, day.tempMinC)
                val tempText = if (temps.size == 2) " ${day.tempMinC}-${day.tempMaxC}°C" else ""
                "- ${day.date}: ${WeatherCode.descriptionFor(day.weatherCode)}$tempText"
            }
            "$region daily forecast:\n$lines"
        }
    }

    /** Matches each day's region to the pre-fetched forecast, falling back to an on-demand
     * single-day lookup (using that day's first attraction coordinates) for unlisted day trips. */
    private suspend fun TravelPlan.withResolvedWeather(
        request: PlannerRequest,
        weatherByRegion: Map<String, List<DailyWeather>>,
    ): TravelPlan {
        val startDate = request.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return this
        if (weatherByRegion.isEmpty()) return this

        val normalizedBuffer = weatherByRegion.mapKeys { it.key.trim().lowercase() }
        val resolvedDayPlans = dayPlans.map { dayPlan ->
            val dateForDay = startDate.plusDays((dayPlan.day - 1).toLong()).toString()
            val matchedDaily = dayPlan.region
                ?.let { normalizedBuffer[it.trim().lowercase()] }
                ?.firstOrNull { it.date == dateForDay }
                ?: findDayTripWeather(dayPlan, dateForDay, request.regions)
            dayPlan.copy(weatherEmoji = matchedDaily?.let { WeatherCode.emojiFor(it.weatherCode) })
        }
        return copy(dayPlans = resolvedDayPlans)
    }

    private suspend fun findDayTripWeather(
        dayPlan: DayPlan,
        dateForDay: String,
        regions: List<PlannerPlace>,
    ): DailyWeather? {
        val regionNames = regions.map { it.name.trim().lowercase() }.toSet()
        if (dayPlan.region?.trim()?.lowercase() in regionNames) return null
        val attraction = dayPlan.attractions.firstOrNull { it.latitude != null && it.longitude != null }
            ?: return null
        return try {
            weatherRepository.getDailyForecast(
                latitude = attraction.latitude,
                longitude = attraction.longitude,
                startDate = dateForDay,
                endDate = dateForDay,
            ).firstOrNull()
        } catch (e: Exception) {
            Log.w("LocalPlannerRepository", "Failed to fetch day-trip weather", e)
            null
        }
    }

    override suspend fun getRecommendations(
        destination: String,
        style: String,
        latitude: Double,
        longitude: Double,
        radiusKm: Double
    ): List<com.pnu.orbit.domain.model.Attraction> {
        return aiPlannerApi.getRecommendations(
            destination = destination,
            style = style,
            latitude = latitude,
            longitude = longitude,
            radiusKm = radiusKm
        ).map { it.toDomain() }
    }

    private fun TravelPlan.withoutRouteAnchorCards(request: PlannerRequest): TravelPlan {
        val lodgingNames = request.accommodations.map { it.name.lowercase() }
        val blockedWords = listOf(
            "airport",
            "station",
            "terminal",
            "hotel",
            "hostel",
            "resort",
            "lodging",
            "accommodation",
        )
        val cleanedDays = dayPlans.map { dayPlan ->
            val cleaned = dayPlan.attractions
                .filterNot { attraction ->
                    val name = attraction.name.lowercase()
                    lodgingNames.any { lodgingName -> lodgingName.isNotBlank() && name.contains(lodgingName) } ||
                        blockedWords.any { word -> name.contains(word) }
                }
                .mapIndexed { index, attraction -> attraction.copy(sequence = index + 1) }
            dayPlan.copy(attractions = cleaned)
        }
        return copy(dayPlans = cleanedDays)
    }
}
