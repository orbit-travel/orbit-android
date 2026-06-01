# orbit Architecture

## 현재 설계 범위

- 패키지 기준은 `com.pnu.orbit`로 정리했다.
- UI는 `SplashActivity -> MainActivity -> TravelRecordFragment / TravelPlannerFragment` 흐름으로 구성했다.
- 추가 Activity는 `AddTripActivity`, `TravelDetailActivity`를 만들고 Intent extra 키를 `IntentKeys`로 모았다.
- 데이터 계층은 `data/local`, `data/remote`, `data/repository`, `data/mapper`로 분리했다.
- Room 대상 엔티티는 `TripEntity`, `PhotoEntity`, `PlanEntity`로 정의했다.
- Retrofit 대상 API 인터페이스는 `AiPlannerApi`, `WeatherApi`로 분리했다.
- `Friends' Earth`, `World Earth`는 `DummyEarthRepository`와 `DemoFallbacks`를 통해 실제 사용자 Room 데이터와 분리했다.
- UI 상태는 `UiState.Loading / Success / Error / Empty`로 공통화했다.

## 패키지 구조

```text
com.pnu.orbit/
├─ data/
│  ├─ local/
│  │  ├─ db/
│  │  ├─ dao/
│  │  └─ entity/
│  ├─ remote/
│  │  ├─ api/
│  │  ├─ dto/
│  │  └─ client/
│  ├─ repository/
│  └─ mapper/
├─ domain/model/
├─ ui/
│  ├─ splash/
│  ├─ main/
│  ├─ record/
│  ├─ addtrip/
│  ├─ detail/
│  ├─ planner/
│  └─ common/
├─ ml/
├─ map/
└─ util/
```

## 아직 미구현인 부분

- Room 저장/조회가 실제 화면에 연결되지는 않았다.
- AI API 호출은 Repository 경계만 있고 화면은 fallback 데이터를 사용한다.
- 지도 SDK 연결, 실제 Polyline/Marker 표시는 아직 placeholder다.
- 사진 EXIF 추출과 ML 분류는 인터페이스와 fallback만 있다.
- 실제 RecyclerView 어댑터는 샘플 데이터를 표시하는 수준이다.
