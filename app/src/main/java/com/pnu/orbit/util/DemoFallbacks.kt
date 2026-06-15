package com.pnu.orbit.util

import com.pnu.orbit.domain.model.Attraction
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
        val isBusan = safeDestination.lowercase().contains("busan") || safeDestination.contains("부산")
        val isSeoul = safeDestination.lowercase().contains("seoul") || safeDestination.contains("서울")
        
        val dayPlans = (1..safeDays).map { day ->
            val attractions = if (isBusan) {
                if (safeStyle.lowercase().contains("extreme") || safeStyle.contains("액티비티") || safeStyle.contains("레저")) {
                    listOf(
                        Attraction(
                            sequence = 1,
                            name = "스카이라인 루지 기장",
                            description = "기장에 위치한 스릴 만점 루지 체험입니다. 카트를 타고 곡선 트랙을 질주하며 스트레스를 날릴 수 있습니다.",
                            imageUrl = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=500",
                            latitude = 35.1952,
                            longitude = 129.2135
                        ),
                        Attraction(
                            sequence = 2,
                            name = "롯데월드 어드벤처 부산",
                            description = "동부산의 대형 테마파크로, 롤러코스터인 자이언트 디거와 워터코스터인 자이언트 스플래쉬 등 익스트림 기구들을 탑승합니다.",
                            imageUrl = "https://images.unsplash.com/photo-1513885045260-6b3086b24c07?w=500",
                            latitude = 35.1958,
                            longitude = 129.2155
                        ),
                        Attraction(
                            sequence = 3,
                            name = "송도 해상 케이블카",
                            description = "송도해수욕장 동쪽 송림공원에서 서쪽 암남공원까지 바다 위를 가로지르는 케이블카입니다. 바닥이 투명한 크리스탈 크루를 타며 짜릿함을 느낍니다.",
                            imageUrl = "https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?w=500",
                            latitude = 35.0762,
                            longitude = 129.0175
                        )
                    )
                } else {
                    listOf(
                        Attraction(
                            sequence = 1,
                            name = "해운대 해수욕장",
                            description = "부산을 대표하는 해변으로, 아침 해안선 산책과 시원한 바다 뷰를 감상하기 가장 좋은 명소입니다.",
                            imageUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=500",
                            latitude = 35.1587,
                            longitude = 129.1604
                        ),
                        Attraction(
                            sequence = 2,
                            name = "감천문화마을",
                            description = "산자락을 따라 계단식으로 형성된 알록달록한 파스텔톤 집들이 가득한 예술 마을로, 골목 구석구석 포토존과 문화 명소를 관람합니다.",
                            imageUrl = "https://images.unsplash.com/photo-1476514525535-07fb3b4ae5f1?w=500",
                            latitude = 35.0975,
                            longitude = 129.0093
                        ),
                        Attraction(
                            sequence = 3,
                            name = "광안리해수욕장 & 광안대교",
                            description = "저녁에는 광안리 해변에서 파도 소리와 함께 광안대교의 환상적인 LED 라이트 쇼 및 바다 야경을 즐깁니다.",
                            imageUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=500",
                            latitude = 35.1532,
                            longitude = 129.1189
                        )
                    )
                }
            } else if (isSeoul) {
                listOf(
                    Attraction(
                        sequence = 1,
                        name = "경복궁",
                        description = "조선 왕조의 대표 법궁인 경복궁에서 광화문, 근정전 등 역사적 건축을 관람하며 수문장 교대식을 관람합니다.",
                        imageUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=500",
                        latitude = 37.5796,
                        longitude = 126.9770
                    ),
                    Attraction(
                        sequence = 2,
                        name = "명동 거리",
                        description = "쇼핑과 먹거리 가득한 서울 중심지에서 유명 길거리 음식을 맛보고 화려한 도심 분위기를 체감합니다.",
                        imageUrl = "https://images.unsplash.com/photo-1501854140801-50d01698950b?w=500",
                        latitude = 37.5636,
                        longitude = 126.9856
                    ),
                    Attraction(
                        sequence = 3,
                        name = "N서울타워",
                        description = "남산 정상에 우뚝 솟은 N서울타워에 올라 탁 트인 360도 서울 도심 전경 및 멋진 남산 야경을 눈에 담습니다.",
                        imageUrl = "https://images.unsplash.com/photo-1469854523086-cc02fe5d8800?w=500",
                        latitude = 37.5511,
                        longitude = 126.9882
                    )
                )
            } else {
                listOf(
                    Attraction(
                        sequence = 1,
                        name = "$safeDestination 시그니처 랜드마크",
                        description = "오전에는 $safeDestination 최고의 명소를 방문합니다. $safeStyle 스타일에 맞춰 여유롭게 감상하세요.",
                        imageUrl = "https://images.unsplash.com/photo-1476514525535-07fb3b4ae5f1?w=500",
                        latitude = 35.1796,
                        longitude = 129.0756
                    ),
                    Attraction(
                        sequence = 2,
                        name = "지역 추천 맛집",
                        description = "점심 식사를 즐기며 동선을 최적화하기 위해 근처 인기 로컬 식당을 이용합니다.",
                        imageUrl = "https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=500",
                        latitude = 35.1810,
                        longitude = 129.0790
                    ),
                    Attraction(
                        sequence = 3,
                        name = "$safeStyle 액티비티 체험장",
                        description = "선택하신 $safeStyle 성향에 맞춘 추천 액티비티 또는 명소 탐방을 실시합니다.",
                        imageUrl = "https://images.unsplash.com/photo-1501785888041-af3ef285b470?w=500",
                        latitude = 35.1830,
                        longitude = 129.0720
                    ),
                    Attraction(
                        sequence = 4,
                        name = "$safeDestination 야경 명소",
                        description = "하루를 마무리하며 멋진 전망대나 포인트를 찾아 멋진 도시의 야경을 감상합니다.",
                        imageUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=500",
                        latitude = 35.1750,
                        longitude = 129.0810
                    )
                )
            }
            DayPlan(day = day, attractions = attractions)
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
