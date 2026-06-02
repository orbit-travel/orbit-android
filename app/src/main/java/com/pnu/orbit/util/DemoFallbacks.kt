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
            subtitle = "Real local trips saved in Room",
            coverImageRes = null,
            isRealData = true,
        ),
        EarthPreview(
            id = "friends-earth",
            type = EarthType.FRIENDS,
            title = "Friends' Earth",
            subtitle = "Demo trips for this MVP",
            coverImageRes = null,
            isRealData = false,
        ),
        EarthPreview(
            id = "world-earth",
            type = EarthType.WORLD,
            title = "World Earth",
            subtitle = "Dummy feed for future backend expansion",
            coverImageRes = null,
            isRealData = false,
        ),
    )

    fun sampleTrips(): List<Trip> = listOf(
        Trip(
            id = 1L,
            title = "Busan Sea Log",
            startPlace = "Pusan National University",
            destination = "Haeundae",
            startDate = 1_777_574_400_000L,
            endDate = 1_777_660_800_000L,
            coverPhotoUri = null,
            memo = "Sample for map markers and photo notes",
            photoCount = 4,
        ),
        Trip(
            id = 2L,
            title = "Seoul Culture Walk",
            startPlace = "Seoul Station",
            destination = "Gyeongbokgung Palace",
            startDate = 1_778_179_200_000L,
            endDate = 1_778_265_600_000L,
            coverPhotoUri = null,
            memo = "Sample showing real trips separated from Friends and World",
            photoCount = 2,
        ),
    )

    fun samplePlan(destination: String, days: Int, style: String): TravelPlan {
        val safeDestination = destination.ifBlank { "Busan" }
        val safeDays = days.coerceAtLeast(1)
        val safeStyle = style.ifBlank { "culture" }
        val dayPlans = (1..safeDays).map { day ->
            DayPlan(
                day = day,
                morning = "Visit a signature spot in $safeDestination",
                lunch = "Try a local restaurant",
                afternoon = "Follow a $safeStyle-focused experience",
                evening = "Enjoy the night view and write a short travel note",
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
