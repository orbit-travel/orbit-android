# Demo Scenario

## 목표

채점자가 과제 요구사항을 빠르게 확인할 수 있는 안정적인 데모 흐름을 우선한다.

## 권장 데모 순서

1. SplashActivity에서 orbit 시작 화면 확인
2. MainActivity에서 Travel Log 탭과 AI Planner 탭 확인
3. Travel Log에서 3개 GLB 지구와 샘플 여행 목록 RecyclerView 확인
4. 여행 추가 버튼으로 AddTripActivity 이동
5. 제목/도착지 입력, 사진 선택 UI 확인, 저장 후 ActivityResult 수신 확인
6. 샘플 상세 또는 trip card 클릭으로 TravelDetailActivity 이동
7. Intent로 받은 tripId/destination 표시 확인
8. 지도 placeholder, 사진/마커 구현 위치 설명
9. AI Planner 탭에서 목적지/일수/스타일 입력 후 fallback 계획 확인
10. API 실패 시에도 앱이 멈추지 않는 fallback 정책 설명

## 발표 때 강조할 구현 포인트

- 4개 Activity로 필수 3개 Activity 조건 충족
- ActivityResultLauncher 기반 양방향 Activity 결과 수신 흐름 준비
- Fragment, RecyclerView, ViewPager2 사용 지점 명확화
- Room/Repository/Mapper 구조로 실제 데이터와 더미 데이터를 분리
- API/ML/지도는 Repository 또는 인터페이스 뒤에 두어 교체 가능하게 설계
