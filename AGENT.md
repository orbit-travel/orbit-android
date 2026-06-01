# AGENT.md

## 0. Project Identity

**App name:** orbit  
**Repository name:** orbit  
**Project type:** Android term project for SW 2026 / Software Design & Experiment  
**Development period:** Approximately 2 weeks  
**Team size:** 2 developers  
**Primary platform:** Android  
**Primary IDE:** Android Studio  
**Version control:** GitHub single repository

This document is the working guide for agentic AI tools and human developers.  
Every agent must read and follow this file before generating code, modifying architecture, designing UI, or changing project scope.

---

## 1. Project Goal

`orbit` is an Android travel record and AI travel planner app.

The core concept is:

> Each user has their own travel “Earth.” Their travel records, photos, routes, and memories orbit around that personal world.

The app uses a multiverse-like visual metaphor:

- **My Earth**: the user’s real local travel records.
- **Friends’ Earth**: dummy/mock content for the current term project.
- **World Earth**: dummy/mock content for the current term project.

The current project is **not a full social network**.  
The priority is to build a stable Android MVP that satisfies the assignment requirements.

---

## 2. Absolute Priority Order

When making any implementation decision, follow this order:

1. **Satisfy course requirements**
2. **Make the demo stable**
3. **Complete core travel record + planner features**
4. **Keep the architecture clean and expandable**
5. **Improve UI/UX and visual concept**
6. **Add optional animations or advanced effects**

Do not sacrifice assignment requirements for fancy UI.

If there is a conflict between:
- cool visual design
- complex 3D globe
- backend-like social features
- stable assignment scoring features

then always choose **stable assignment scoring features** first.

---

## 3. Course Requirements

The app must satisfy the term project conditions.

### 3.1 Mandatory App Structure

The app must include:

- **At least 3 Activities**
- **Intent-based data transfer between Activities**
- At least one Activity-to-Activity data transfer
- One-way or two-way Intent is allowed
- Prefer one two-way flow using `ActivityResultLauncher`

Recommended Activity structure:

```text
SplashActivity
    ↓ Intent
MainActivity
    ├── TravelRecordFragment
    ├── TravelPlannerFragment
    │
    ├── Intent(tripId) → TravelDetailActivity
    │                     └── setResult(updatedTripData)
    │
    └── Intent() → AddTripActivity
                      └── setResult(newTripId)
```

Minimum Activities:

| Activity | Purpose |
|---|---|
| SplashActivity | App launch / visual intro / transition to MainActivity |
| MainActivity | Main container with travel record and planner tabs |
| AddTripActivity | Create a new trip and import photos |
| TravelDetailActivity | Show map, route, photo markers, and comments |

Optional:

| Activity | Purpose |
|---|---|
| PlannerResultActivity | Show detailed AI-generated plan |
| PhotoDetailActivity | Show photo detail and edit comment |

### 3.2 Final PPT Structure

The final PPT must be **6 pages or fewer** and follow this exact structure:

1. **p.1:** Team members and role distribution
2. **p.2:** Application overview
3. **p.3~p.4:** Wireframes, basic scoring features, additional scoring features
4. **p.5:** Stability implementation details
5. **p.6:** Other notes, lessons learned, future expansion

The role distribution must be clear because differential evaluation may be applied.

---

## 4. Scoring Strategy

The UI and implementation should make the following scoring items clearly visible.

| Scoring Item | Implementation Strategy |
|---|---|
| Coroutine | Use Kotlin Coroutines for API calls, Room access, and metadata processing |
| Download manager | Use Retrofit for API calls and Glide for image loading |
| Jetpack | Use RecyclerView, Fragment, ViewPager2, ViewModel, Room |
| External app integration | Use Gallery / Photo Picker / MediaStore |
| API | Use map API, AI planning API, and optional weather API |
| DB | Use Room for local persistence; note that DB score may not stack with API score |
| Machine Learning | Classify selected photos as city / sea / mountain |
| Stability | Permission handling, loading/error UI, null safety, offline cache, fallback data |
| Completeness | A stable demo flow that does not crash |

Important:

- The app must clearly show at least 3 Activities.
- The app must clearly show Intent data transfer.
- RecyclerView / Fragment / ViewPager2 should be easy to identify in the implementation and presentation.
- API usage should be visible in the demo or explainable through stable fallback data.
- ML should be simple and demonstrable.

---

## 5. Backend Policy

### 5.1 Current Version: No Backend

Do **not** implement a backend server in the current term project unless explicitly requested.

No backend should be added for:

- Login
- User accounts
- Friend relationships
- Global feed
- Cloud photo upload
- Shared travel records
- Comments/likes
- Real-time synchronization

Current architecture:

```text
Android App
├── Room DB
├── Gallery / MediaStore
├── Map API
├── AI planner API
├── Optional Weather API
├── Optional local ML model
└── Dummy friend/world Earth data
```

### 5.2 Social Earth Policy

The app concept includes:

```text
My Earth
Friends' Earth
World Earth
```

But only **My Earth** is real in this term project.

| Earth Type | Current Implementation |
|---|---|
| My Earth | Real local user trips stored in Room |
| Friends' Earth | Dummy/mock sample trips |
| World Earth | Dummy/mock global sample trips |

Rules:

- Do not connect Friends’ Earth or World Earth to a real server.
- Do not create login or user account features.
- Do not implement cloud sync.
- Keep dummy data clearly separated from real local user data.
- Design the architecture so backend can be added later.

### 5.3 Future Backend Expansion

The code should be organized so that future backend expansion is possible.

Potential future backend features:

- User authentication
- Friend graph
- Public/private trip visibility
- Shared world feed
- Cloud photo storage
- Comments and likes
- Cross-device synchronization

For now, define clean Repository boundaries so local/dummy data can later be replaced by remote data sources.

---

## 6. Core Features

## 6.1 Travel Record

The travel record feature is the main implemented feature.

### Required Functions

- Create a trip
- Select/import photos from gallery
- Save trip information to Room
- Save selected photo URIs to Room
- Extract photo metadata when possible:
  - taken date
  - latitude
  - longitude
- Display trip list using RecyclerView
- Open trip detail through Intent
- Show trip route and photo markers on a map
- Allow comments/memo per photo

### Photo Metadata Policy

When photos are imported:

1. Try to read GPS metadata.
2. Try to read taken date.
3. If GPS metadata exists, place the photo marker on the map.
4. If GPS metadata is missing, use the trip destination as fallback.
5. If no usable location exists, show the photo in an “Unlocated Photos” section.
6. Never crash because of missing EXIF or MediaStore metadata.

---

## 6.2 Travel Detail

TravelDetailActivity should show:

- Map view
- Route line
- Photo markers
- Photo list or BottomSheet
- Photo detail
- Photo comment editing
- ML tag display if available

Route policy:

- MVP route can be a simple Polyline between start and destination.
- Real navigation routing is optional.
- Route animation is optional.
- Do not block core implementation for advanced route animation.

Photo marker policy:

- Markers should be visible and clickable.
- Marker click should show related photo(s).
- If several photos share similar location, show them as a list or simple stacked layout.
- Advanced clustering is optional.

---

## 6.3 AI Travel Planner

The planner feature generates travel plans using an AI API.

### Required Input

- Destination
- Duration or date range
- Travel style
- Optional companion type
- Optional budget
- Optional travel pace

### Required Output

- Day-by-day itinerary
- Morning / lunch / afternoon / evening structure
- Save generated plan to Room
- Display result using RecyclerView or ViewPager2

### API Policy

- Use Retrofit.
- Use Kotlin Coroutine.
- Use loading / success / error states.
- Prefer structured JSON output.
- Do not rely on fragile free-form parsing if avoidable.
- If API fails, show fallback demo plan instead of crashing.

Recommended prompt style:

```text
Create a travel itinerary for {destination} for {days} days.
Travel style: {style}.
Return JSON only with this structure:
{
  "destination": "...",
  "days": [
    {
      "day": 1,
      "morning": "...",
      "lunch": "...",
      "afternoon": "...",
      "evening": "..."
    }
  ]
}
```

---

## 6.4 Weather

Weather is optional but useful for API scoring.

If implemented:

- Use Retrofit + Coroutine.
- Display weather summary in planner result.
- Weather API failure must not block AI plan display.
- Use fallback weather UI if needed.

---

## 6.5 Machine Learning

Machine learning should be intentionally simple.

Target labels:

```text
city
sea
mountain
```

Optional labels:

```text
food
night
landmark
```

Expected behavior:

1. User imports a photo.
2. The app runs a local classifier.
3. The classifier predicts a simple category.
4. The tag is saved in Room.
5. The tag is shown on the photo card, detail screen, or marker info.

Implementation options:

- TensorFlow Lite local model
- ML Kit image labeling
- Lightweight custom classifier

Preferred for scoring:

- Use a simple local TFLite model if feasible.
- If ML Kit is used, keep the feature small and clearly show that image classification is applied in the app.

Important:

- ML must not block photo import.
- ML failure must not crash the app.
- If classification fails, set tag to `"unknown"`.

---

## 7. UI / UX Direction

## 7.1 UI Priority

The first UI goal is **assignment requirement visibility**, not artistic perfection.

UI must make the following visible:

- 3+ Activities
- Intent transitions
- Travel record list
- Gallery/photo integration
- Map route/photo marker
- AI planner result
- Jetpack usage
- Loading/error/empty states
- Dummy social Earths as future expansion

## 7.2 Main Screen Layout

MainActivity should use a simple and clear layout.

Recommended structure:

```text
MainActivity
├── Top app title: orbit
├── Earth hub area
│   ├── My Earth
│   ├── Friends' Earth
│   └── World Earth
├── BottomNavigationView
│   ├── Travel Record tab
│   └── AI Planner tab
└── Fragment container
```

Alternative:

```text
MainActivity
├── BottomNavigationView
├── TravelRecordFragment
│   ├── Earth hub
│   └── Trip RecyclerView
└── TravelPlannerFragment
    ├── Planner form
    └── Plan result
```

Prefer the alternative if it is easier to implement.

---

## 7.3 Earth Hub UI

Earth hub should communicate the concept without becoming the main technical risk.

Minimum version:

- Dark space-like background
- My Earth as the largest planet
- Friends’ Earth and World Earth as smaller planets
- My Earth opens real travel records
- Friends’ Earth and World Earth show dummy data or future-expansion message

Allowed implementations:

1. Static ImageView planet with rotation animation
2. Lottie Earth animation
3. Simple Canvas planet
4. Mapbox Globe if stable quickly
5. True OpenGL 3D only if already working and not blocking core features

Preferred approach:

- Earth hub = visual concept
- TravelDetailActivity map = actual functional travel visualization

Do not spend excessive time implementing real 3D if core scoring features are unfinished.

---

## 7.4 Travel Record UI

TravelRecordFragment should include:

- Earth hub or button to My Earth
- Trip list using RecyclerView
- FloatingActionButton or button for AddTripActivity

Trip card should show:

- Cover image
- Trip title
- Destination
- Date range
- Photo count
- Optional ML tag chips
- Optional short memo

---

## 7.5 Add Trip UI

AddTripActivity should include:

- Trip title input
- Start place input
- Destination input
- Start date / end date
- Photo selection button
- Selected photo preview list
- Save button

Save button behavior:

- Validate required fields.
- Save TripEntity.
- Save PhotoEntity list.
- Return result using setResult.
- Finish Activity.

---

## 7.6 Travel Detail UI

TravelDetailActivity should include:

- Map section
- Route line
- Photo markers
- Photo list or BottomSheet
- Photo comment editor

Recommended layout:

```text
TravelDetailActivity
├── Header: trip title + date
├── MapView / SupportMapFragment
├── Photo marker interaction
├── BottomSheet photo list
└── Comment editor or photo detail dialog
```

---

## 7.7 AI Planner UI

TravelPlannerFragment should include:

- Destination input
- Days/date range input
- Travel style chips
- Generate button
- Loading state
- Error state
- Result area

Result display options:

- ViewPager2 with DayPlanFragment
- RecyclerView with day cards

Recommended:

- Use ViewPager2 if you still need clear Jetpack scoring.
- Use RecyclerView if ViewPager2 slows implementation.

---

## 8. Architecture

Use MVVM + Repository pattern.

Recommended package structure:

```text
com.pnu.orbit/
├── data/
│   ├── local/
│   │   ├── db/
│   │   ├── dao/
│   │   └── entity/
│   ├── remote/
│   │   ├── api/
│   │   ├── dto/
│   │   └── client/
│   ├── repository/
│   └── mapper/
├── domain/
│   └── model/
├── ui/
│   ├── splash/
│   ├── main/
│   ├── record/
│   ├── addtrip/
│   ├── detail/
│   ├── planner/
│   └── common/
├── ml/
├── map/
└── util/
```

Rules:

- Activity/Fragment should not directly access Retrofit or Room DAO.
- Use ViewModel for UI state.
- Use Repository for data operations.
- Use Coroutine for async work.
- Keep dummy social data behind a repository or provider so it can later be replaced by backend data.

---

## 9. Data Model

Recommended Room entities:

```kotlin
@Entity
data class TripEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val startPlace: String,
    val destination: String,
    val startDate: Long,
    val endDate: Long,
    val coverPhotoUri: String?,
    val memo: String?
)
```

```kotlin
@Entity
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tripId: Long,
    val uri: String,
    val takenAt: Long?,
    val lat: Double?,
    val lng: Double?,
    val comment: String?,
    val tag: String?
)
```

```kotlin
@Entity
data class PlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val destination: String,
    val days: Int,
    val style: String,
    val planJson: String,
    val createdAt: Long
)
```

Optional dummy social model:

```kotlin
data class EarthPreview(
    val id: String,
    val type: EarthType,
    val title: String,
    val subtitle: String,
    val coverImageRes: Int?,
    val isRealData: Boolean
)

enum class EarthType {
    MY,
    FRIENDS,
    WORLD
}
```

---

## 10. API Key Policy

Never hardcode API keys in Kotlin source files.

Use `local.properties`:

```properties
MAPS_API_KEY=...
OPENAI_API_KEY=...
WEATHER_API_KEY=...
```

Expose through Gradle `BuildConfig` if needed.

Rules:

- `local.properties` must not be committed.
- If an API key is missing, show error UI or use demo fallback.
- The app must not crash because an API key is missing.
- For the term project, direct API calls from Android are acceptable.
- For real service expansion, API calls should move behind a backend.

---

## 11. Permission Policy

Expected permissions:

- Internet
- Location if needed
- Photo/media access if needed

Photo access must be Android-version aware.

Guideline:

- Prefer Android Photo Picker if possible.
- For Android 13+, use `READ_MEDIA_IMAGES` if direct media permission is needed.
- For older versions, use `READ_EXTERNAL_STORAGE` if needed.
- User cancellation must be handled safely.

Permission failure behavior:

- Show message.
- Disable only the dependent feature.
- Do not crash.
- Allow the user to continue using other parts of the app.

---

## 12. Stability Requirements

Every user-facing async flow should use:

```text
Loading
Success
Error
Empty
```

Stability checklist:

- No crash when app launches.
- No crash when trip list is empty.
- No crash when user cancels gallery selection.
- No crash when selected photo has no GPS.
- No crash when selected photo has no date.
- No crash when network fails.
- No crash when API response is malformed.
- No crash when API key is missing.
- No crash after screen rotation.
- Room data persists after app restart.
- Demo fallback data exists for API failure.

---

## 13. Demo Fallback Policy

Because live APIs may fail during presentation, prepare fallback data.

Fallback data may include:

- Sample trips
- Sample photo markers
- Sample AI plan JSON
- Sample weather data
- Dummy Friends’ Earth
- Dummy World Earth

Fallback should activate when:

- API key is missing
- Network call fails
- No user data exists
- Demo setup needs predictable output

Do not let fallback data hide real implemented features.  
Use fallback only to keep the demo stable.

---

## 14. GitHub / Repository Rules

Repository name:

```text
orbit
```

Recommended repository structure:

```text
orbit/
├── app/
├── docs/
│   ├── proposal.md
│   ├── wireframe.md
│   ├── demo-scenario.md
│   └── ppt-outline.md
├── AGENT.md
├── README.md
├── .gitignore
└── local.properties   // must NOT be committed
```

Recommended branches:

```text
main                stable submission branch
develop             integration branch
feature/record      travel record, photos, map
feature/planner     AI planner, API, result UI
feature/ui          Earth hub and visual polish
```

Commit message prefixes:

```text
feat: new feature
fix: bug fix
refactor: code restructuring
chore: setup/dependency/config
docs: documentation
ui: layout or visual update
test: test/demo data
```

Never commit:

- API keys
- `local.properties`
- `.gradle/`
- `app/build/`
- build outputs
- personal SDK paths

---

## 15. Team Work Division

Recommended two-person split:

### Developer A: Travel Record / Map / Photo

Responsibilities:

- Room entities and DAO for Trip/Photo
- AddTripActivity
- Gallery/photo picker
- Photo metadata extraction
- TravelDetailActivity
- Map markers and route Polyline
- Photo comment saving

### Developer B: Planner / API / UI / Stability

Responsibilities:

- MainActivity and Fragment structure
- TravelPlannerFragment
- AI planner API
- Weather API if implemented
- ViewPager2 or plan result RecyclerView
- Loading/error/empty state handling
- PPT/wireframe documentation

Shared:

- GitHub merge
- UI consistency
- Demo scenario
- Final bug fixing
- Presentation preparation

---

## 16. Scope Guardrails

Do not implement these unless all MVP items are already complete:

- Real backend
- Login
- Firebase authentication
- Cloud database
- Cloud photo upload
- Real friend system
- Real global feed
- Real comments/likes
- Advanced 3D engine
- Full route optimization
- Full automatic gallery scan
- Complex photo clustering
- Production security architecture

Allowed as future expansion only:

- Backend server
- Social sharing
- Cloud sync
- Real Friends’ Earth
- Real World Earth
- AR travel memory
- Cross-device account sync

---

## 17. MVP Completion Checklist

The MVP is complete when:

- [ ] App launches without crash.
- [ ] MainActivity exists.
- [ ] At least 3 Activities exist.
- [ ] Intent data transfer works.
- [ ] TravelRecordFragment exists.
- [ ] TravelPlannerFragment exists.
- [ ] User can create a trip.
- [ ] User can select photos from gallery.
- [ ] Trip data is saved in Room.
- [ ] Photo data is saved in Room.
- [ ] Trip list is shown with RecyclerView.
- [ ] TravelDetailActivity opens from a trip.
- [ ] Map is displayed.
- [ ] Route or marker is displayed.
- [ ] Photo comment can be saved or edited.
- [ ] AI planner form exists.
- [ ] AI planner uses Retrofit + Coroutine or stable fallback.
- [ ] AI plan result is displayed.
- [ ] Fragment is used.
- [ ] RecyclerView is used.
- [ ] ViewPager2 or another Jetpack component is used.
- [ ] Glide loads images.
- [ ] Permissions are handled.
- [ ] API failure does not crash the app.
- [ ] Empty states are handled.
- [ ] Friends’ Earth exists as dummy/future-expansion UI.
- [ ] World Earth exists as dummy/future-expansion UI.
- [ ] Simple ML classification works or has a clear fallback.
- [ ] PPT can clearly explain scoring features.

---

## 18. Agent Work Rules

Any AI agent working on this repository must follow these rules:

1. Read this AGENT.md before making changes.
2. Preserve course requirement satisfaction.
3. Do not add backend/server code unless explicitly instructed.
4. Do not remove Activity/Intent flows needed for grading.
5. Prefer simple, stable Android implementation over complex visuals.
6. Keep Friends’ Earth and World Earth as dummy or future expansion.
7. Keep dummy data separate from real user Room data.
8. Keep Repository boundaries so backend can be added later.
9. Use Room for local persistence.
10. Use Coroutine for asynchronous operations.
11. Use Retrofit/Glide where network/image loading is needed.
12. Use RecyclerView/Fragment/ViewPager2 to satisfy Jetpack scoring.
13. Add loading/error/empty states for user-facing screens.
14. Never commit API keys.
15. Keep UI code and data/network code separated.
16. Update docs when major architecture decisions change.
17. Do not expand scope without checking MVP checklist first.

---

## 19. Final Product Statement

`orbit` should be presented as:

> An Android travel memory planet app that visualizes personal trips through map-based photo records and generates future trip plans using AI.

For the current term project:

- Backend is out of scope.
- My Earth uses real local data.
- Friends’ Earth and World Earth use dummy data.
- The app must prioritize assignment requirements.
- The app must be stable during demo.
- The architecture should allow future backend/social expansion.
