# MVP Roadmap

## 1단계: 앱 구조와 과제 필수 흐름 고정

- SplashActivity, MainActivity, AddTripActivity, TravelDetailActivity 유지
- AddTripActivity에서 입력값과 선택 사진 URI를 받아 Room에 저장
- TravelRecordFragment에서 Room trip 목록을 RecyclerView로 표시
- trip 클릭 시 Intent로 TravelDetailActivity 열기
- ActivityResultLauncher로 AddTripActivity 결과를 수신

## 2단계: 여행 기록 기능 완성

- Android Photo Picker 결과 URI 저장
- MediaStore/ExifInterface로 촬영일, GPS 메타데이터 추출
- GPS가 없으면 목적지 fallback 위치 사용
- PhotoEntity에 comment, tag 저장
- TravelDetailActivity에서 사진 목록, 댓글 수정, 위치 없는 사진 섹션 구현

## 3단계: 지도 표시

- 지도 API 키를 `local.properties`에 두고 BuildConfig로 노출
- TravelDetailActivity에 MapView 또는 SupportMapFragment 연결
- 출발지와 목적지를 단순 Polyline으로 연결
- PhotoEntity 위치를 Marker로 표시
- Marker 클릭 시 관련 사진/메모 표시

## 4단계: AI 여행 플래너

- Planner form을 ViewModel과 Repository에 연결
- Retrofit + Coroutine으로 AI plan API 호출
- API key 누락, 네트워크 실패, JSON 파싱 실패 시 fallback plan 표시
- PlanEntity에 JSON 저장
- ViewPager2 또는 RecyclerView로 일자별 계획 표시

## 5단계: ML 및 안정성

- 간단한 photo classifier를 `PhotoClassifier` 뒤에 구현
- 실패 시 `UNKNOWN` 태그 저장
- Loading/Error/Empty 상태를 모든 주요 화면에 반영
- 회전, 권한 거부, 갤러리 취소, 네트워크 실패 시나리오 점검

## 6단계: 발표 준비

- 6페이지 이하 PPT 구조 확정
- 3개 이상 Activity, Intent 전달, RecyclerView, Fragment, ViewPager2, Room, Retrofit, Coroutine, Glide 사용 지점을 캡처
- 데모 실패 대비 fallback 데이터 경로를 설명 가능하게 정리
