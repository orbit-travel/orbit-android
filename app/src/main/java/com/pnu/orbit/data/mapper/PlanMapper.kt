package com.pnu.orbit.data.mapper

import com.google.gson.Gson
import com.pnu.orbit.data.local.entity.PlanEntity
import com.pnu.orbit.data.remote.dto.AiPlanResponseDto
import com.pnu.orbit.data.remote.dto.AttractionDto
import com.pnu.orbit.data.remote.dto.DayPlanDto
import com.pnu.orbit.domain.model.Attraction
import com.pnu.orbit.domain.model.DayPlan
import com.pnu.orbit.domain.model.TravelPlan

private val gson = Gson()

fun AiPlanResponseDto.toDomain(
    id: Long = 0L,
    style: String,
    createdAt: Long = System.currentTimeMillis(),
    isFallback: Boolean = false,
): TravelPlan = TravelPlan(
    id = id,
    destination = destination,
    days = days.size,
    style = style,
    dayPlans = days.map { it.toDomain() },
    createdAt = createdAt,
    isFallback = isFallback,
)

fun DayPlanDto.toDomain(): DayPlan = DayPlan(
    day = day,
    attractions = attractions.map { it.toDomain() },
)

fun AttractionDto.toDomain(): Attraction = Attraction(
    sequence = sequence,
    name = name,
    description = description,
    imageUrl = imageUrl,
    latitude = latitude,
    longitude = longitude,
)

fun TravelPlan.toEntity(): PlanEntity = PlanEntity(
    id = id,
    destination = destination,
    days = days,
    style = style,
    planJson = gson.toJson(this),
    createdAt = createdAt,
)

fun PlanEntity.toDomain(): TravelPlan =
    runCatching {
        val jsonObject = gson.fromJson(planJson, com.google.gson.JsonObject::class.java)
        val dayPlansArray = jsonObject.getAsJsonArray("dayPlans") ?: jsonObject.getAsJsonArray("days")
        val dayPlans = dayPlansArray.map { dayElement ->
            val dayObj = dayElement.asJsonObject
            val dayNum = dayObj.get("day").asInt
            val attractions = mutableListOf<Attraction>()
            if (dayObj.has("attractions")) {
                val attrArray = dayObj.getAsJsonArray("attractions")
                attrArray.forEach { attrElement ->
                    val attrObj = attrElement.asJsonObject
                    attractions.add(
                        Attraction(
                            sequence = attrObj.get("sequence").asInt,
                            name = attrObj.get("name").asString,
                            description = attrObj.get("description").asString,
                            imageUrl = attrObj.get("imageUrl")?.let { if (it.isJsonNull) null else it.asString },
                            latitude = attrObj.get("latitude")?.let { if (it.isJsonNull) null else it.asDouble },
                            longitude = attrObj.get("longitude")?.let { if (it.isJsonNull) null else it.asDouble }
                        )
                    )
                }
            } else {
                var seq = 1
                listOf("morning", "lunch", "afternoon", "evening").forEach { timeOfDay ->
                    if (dayObj.has(timeOfDay) && !dayObj.get(timeOfDay).isJsonNull) {
                        val text = dayObj.get(timeOfDay).asString
                        if (text.isNotBlank()) {
                            val name = when (timeOfDay) {
                                "morning" -> "오전"
                                "lunch" -> "점심"
                                "afternoon" -> "오후"
                                "evening" -> "저녁"
                                else -> timeOfDay.replaceFirstChar { it.uppercase() }
                            }
                            attractions.add(
                                Attraction(
                                    sequence = seq++,
                                    name = name,
                                    description = text
                                )
                            )
                        }
                    }
                }
            }
            DayPlan(day = dayNum, attractions = attractions)
        }
        TravelPlan(
            id = id,
            destination = jsonObject.get("destination")?.asString ?: destination,
            days = jsonObject.get("days")?.let { 
                if (it.isJsonArray) it.asJsonArray.size() else it.asInt 
            } ?: days,
            style = jsonObject.get("style")?.asString ?: style,
            dayPlans = dayPlans,
            createdAt = jsonObject.get("createdAt")?.asLong ?: createdAt,
            isFallback = jsonObject.get("isFallback")?.asBoolean ?: false
        )
    }.getOrElse {
        TravelPlan(
            id = id,
            destination = destination,
            days = days,
            style = style,
            dayPlans = emptyList(),
            createdAt = createdAt,
            isFallback = true,
        )
    }
