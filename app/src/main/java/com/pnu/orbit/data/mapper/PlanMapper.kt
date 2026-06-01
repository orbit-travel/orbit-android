package com.pnu.orbit.data.mapper

import com.google.gson.Gson
import com.pnu.orbit.data.local.entity.PlanEntity
import com.pnu.orbit.data.remote.dto.AiPlanResponseDto
import com.pnu.orbit.data.remote.dto.DayPlanDto
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
    morning = morning,
    lunch = lunch,
    afternoon = afternoon,
    evening = evening,
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
    runCatching { gson.fromJson(planJson, TravelPlan::class.java).copy(id = id) }
        .getOrElse {
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
