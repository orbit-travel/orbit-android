# Final PPT Outline

## p.1 Team members and roles

- Developer A: Travel Record / Photo / Map / Room
- Developer B: Planner / API / UI state / PPT and demo stability

## p.2 Application overview

- orbit: 개인 여행 기록이 My Earth 주위를 도는 여행 메모리 앱
- My Earth는 실제 로컬 데이터, Friends/World Earth는 MVP 더미 데이터

## p.3 Wireframes and basic scoring features

- SplashActivity
- MainActivity with TravelRecordFragment and TravelPlannerFragment
- AddTripActivity
- TravelDetailActivity
- RecyclerView, Fragment, Intent flow

## p.4 Additional scoring features

- Room DB
- Retrofit + Coroutine
- Glide image loading
- ViewPager2 planner result
- Gallery/Photo Picker
- Simple ML photo tag

## p.5 Stability implementation details

- Loading/Success/Error/Empty state
- API fallback data
- Missing GPS fallback
- Permission denial handling
- Null safety and user cancellation handling

## p.6 Lessons and future expansion

- Backend is intentionally out of MVP scope
- Future: login, friend graph, cloud sync, shared World Earth
- Repository boundaries allow local/dummy data to be replaced later
