package com.pnu.orbit.util

import com.pnu.orbit.domain.model.DayPlan
import com.pnu.orbit.domain.model.EarthPreview
import com.pnu.orbit.domain.model.EarthType
import com.pnu.orbit.domain.model.TravelPlan
import com.pnu.orbit.domain.model.Trip

object DemoFallbacks {
    fun earthPreviews(): List<EarthPreview> = listOf(
        EarthPreview(
            id = "my-earth",
            type = EarthType.MY,
            title = "My Earth",
            subtitle = "Room에 저장되는 실제 내 여행 기록",
            coverImageRes = null,
            isRealData = true,
        ),
        EarthPreview(
            id = "friends-earth",
            type = EarthType.FRIENDS,
            title = "Friends' Earth",
            subtitle = "이번 MVP에서는 더미 여행 샘플",
            coverImageRes = null,
            isRealData = false,
        ),
        EarthPreview(
            id = "world-earth",
            type = EarthType.WORLD,
            title = "World Earth",
            subtitle = "향후 백엔드 확장 대상 더미 피드",
            coverImageRes = null,
            isRealData = false,
        ),
    )

    fun sampleTrips(): List<Trip> = listOf(
        Trip(
            id = 1L,
            title = "부산 바다 기록",
            startPlace = "부산대",
            destination = "해운대",
            startDate = 1_777_574_400_000L,
            endDate = 1_777_660_800_000L,
            coverPhotoUri = null,
            memo = "지도 마커와 사진 메모 데모용 샘플",
            photoCount = 4,
        ),
        Trip(
            id = 2L,
            title = "서울 문화 산책",
            startPlace = "서울역",
            destination = "경복궁",
            startDate = 1_778_179_200_000L,
            endDate = 1_778_265_600_000L,
            coverPhotoUri = null,
            memo = "Friends/World와 분리된 실제 기록 구조 확인용",
            photoCount = 2,
        ),
    )

    fun samplePlan(destination: String, days: Int, style: String): TravelPlan {
        val safeDestination = destination.ifBlank { "부산" }
        val safeDays = days.coerceAtLeast(1)
        val safeStyle = style.ifBlank { "culture" }
        val dayPlans = (1..safeDays).map { day ->
            DayPlan(
                day = day,
                morning = "$safeDestination 대표 명소 산책",
                lunch = "지역 음식점 방문",
                afternoon = "$safeStyle 스타일에 맞는 체험 코스",
                evening = "야경 또는 카페에서 기록 정리",
            )
        }

        return TravelPlan(
            id = 0L,
            destination = safeDestination,
            days = safeDays,
            style = safeStyle,
            dayPlans = dayPlans,
            createdAt = System.currentTimeMillis(),
            isFallback = true,
        )
    }
}
