# orbit

**orbit** is an Android travel-record and AI travel-planner app. Each user has their own travel "Earth" — trips, photos, and routes orbit around their personal world, visualized as a rotating 3D globe.

Built as a 2-person, ~2-week university term project (PNU SW 2026 / Software Design & Experiment).

---

## Demo Video

[![Demo Video](https://img.youtube.com/vi/7J_eY6hjeIQ/maxresdefault.jpg)](https://www.youtube.com/watch?v=7J_eY6hjeIQ)

*(placeholder link — https://www.youtube.com/watch?v=7J_eY6hjeIQ)*

---

## Download

Don't want to clone and build it yourself? Grab the prebuilt debug APK from the [**GitHub Releases**](https://github.com/orbit-travel/orbit-android/releases/latest) page and install it directly on your device.

---

## Features

### Travel Record
- Create a trip with title, start place, destination, and date range
- Build a trip out of ordered transport segments (flight / train / car / accommodation)
- Import photos from the system photo picker
- Automatic EXIF metadata extraction (GPS coordinates, taken date), falling back to the trip destination when GPS is missing
- Trip list with cover image, destination, date range, and photo count (RecyclerView)

### Map & Route Visualization
- Real Google Maps rendering of a trip's route and photo locations
- Mode-colored, curved transport polylines with animated moving icons
- Stacked photo markers that fan out on tap, with photo + comment overlay
- Offline coordinate fallback (bundled airport dataset + known-place lookup) when geocoding is unavailable

### AI Travel Planner
- Generates a day-by-day itinerary (morning / lunch / afternoon / evening) from destination, duration, and travel style
- Backed by the Gemini API via Retrofit + Kotlin Coroutines, with structured JSON output
- Automatic retry on rate-limiting, and a stable fallback demo plan if the API call fails or the key is missing
- Results saved to Room and browsable through a ViewPager2 day-by-day view

### Weather
- Open-Meteo integration surfaces a weather summary alongside the generated plan
- Weather failures never block the AI plan from displaying

### On-Device Photo Classification
- Each imported photo is automatically tagged with a scene category by a local TensorFlow Lite model
- See [Machine Learning](#machine-learning) below

### 3D Earth Hub
- Rotating, swipeable 3D Earth model (GLB) on the record screen; tapping it opens a Google satellite map
- Three Earths side by side:
  - **My Earth** — your real local trips, stored in Room
  - **Friends' Earth** 🚧 *(coming soon)* — currently sample/dummy data; real friend trips are a planned future feature
  - **World Earth** 🚧 *(coming soon)* — currently sample/dummy data; a real global feed is a planned future feature

---

## Tech Stack

| Category | Details |
|---|---|
| Language | Kotlin 2.0, Java 11 target |
| Build | Android Gradle Plugin 9.1.1, Gradle 9.3.1, KSP 2.2.10-2.0.2 |
| SDK | `minSdk` 28, `targetSdk` / `compileSdk` 36 |
| UI Toolkit | Android Views (Activities + Fragments), no Jetpack Compose |
| Architecture | MVVM + Repository, manual dependency injection via `RepositoryProvider` |
| Jetpack | RecyclerView, Fragment, ViewPager2, ViewModel + LiveData, Room |
| Async | Kotlin Coroutines |
| Networking | Retrofit 3.0.0 + Gson converter |
| Image Loading | Glide 5.0.7 |
| Database | Room 2.8.4 (KSP) |
| Maps | Google Maps SDK 20.0.0, Places SDK 5.1.1 |
| Machine Learning | TensorFlow Lite (LiteRT) 1.4.2 |
| 3D Rendering | SceneView 2.3.1 (Filament), GLB model |
| Metadata | AndroidX ExifInterface 1.4.2 |
| Testing | JUnit 4.13.2, Espresso 3.7.0 |

---

## Machine Learning

Imported photos are classified on-device by a TensorFlow Lite model (`TfLitePhotoClassifier`) into one of six scene categories: `buildings`, `forest`, `glacier`, `mountain`, `sea`, `street`. Classification never blocks or delays photo import, and any failure (missing model, low confidence, decode error) safely resolves to an `unknown` tag instead of crashing.

---

## Project Structure

```text
com.pnu.orbit/
├── data/
│   ├── local/
│   │   ├── asset/      # offline airport coordinate dataset
│   │   ├── dao/        # Room DAOs
│   │   ├── db/         # OrbitDatabase (Room, v6) + migrations
│   │   └── entity/     # Trip, Photo, TransportSegment, Plan, SavedTravelPlan
│   ├── remote/
│   │   ├── api/        # AiPlannerApi, GeminiPlannerApi, OpenMeteoApi
│   │   ├── client/      # RetrofitClient
│   │   └── dto/         # network request/response DTOs
│   ├── repository/      # Trip/Planner/Earth/Weather repositories (Local*/Dummy* impls)
│   └── mapper/           # entity <-> domain model mappers
├── domain/
│   └── model/             # Trip, TravelPhoto, TransportSegment, TravelPlan, EarthPreview, ...
├── map/                    # PlaceCoordinateResolver, RoutePreviewBuilder, TripMapRenderer
├── ml/                     # PhotoClassifier, TfLitePhotoClassifier, FallbackPhotoClassifier
├── ui/
│   ├── splash/             # SplashActivity
│   ├── main/                # MainActivity (BottomNavigationView host)
│   ├── record/               # TravelRecordFragment, Earth hub, trip list
│   ├── addtrip/               # AddTripActivity, PlaceSearchActivity, GalleryPickerActivity
│   ├── planner/                # TravelPlannerFragment + nested planner flow
│   └── common/                  # UiState, shared adapters/dialogs
└── util/                          # IntentKeys, DemoFallbacks, WeatherCode
```

---

## Architecture

`orbit` follows **MVVM + Repository**, with manual dependency injection (no Hilt/Dagger) — `RepositoryProvider` is the single composition root that wires DAOs and APIs into repositories. UI flows through ViewModels backed by the sealed `UiState` (`Loading` / `Empty` / `Success` / `Error`) so every async screen has explicit loading, empty, and error handling.

```text
SplashActivity
    ↓ Intent
MainActivity (BottomNavigationView)
    ├── TravelRecordFragment
    │       ↓ Intent
    │   AddTripActivity ──→ PlaceSearchActivity / GalleryPickerActivity
    │       ↓ setResult
    │   (back to TravelRecordFragment)
    └── TravelPlannerFragment
```

Repository interfaces (`TripRepository`, `PlannerRepository`, `EarthRepository`, `WeatherRepository`) each have a `Local*`/`Dummy*` implementation, keeping local/dummy data sources swappable for a future backend without touching UI code.

---

## Getting Started

### Prerequisites
- Android Studio (with the bundled JBR / JDK 11+)
- Android SDK 36

### 1. Clone

```powershell
git clone https://github.com/orbit-travel/orbit-android.git
cd orbit-android
```

### 2. Configure API keys

Create a `local.properties` file in the project root (this file is git-ignored and must never be committed):

```properties
MAPS_API_KEY=your_google_maps_api_key
PLACES_API_KEY=your_google_places_api_key
GEMINI_API_KEY=your_gemini_api_key
```

`PLACES_API_KEY` is optional and falls back to `MAPS_API_KEY` if omitted. A missing or invalid key degrades gracefully to fallback/demo data — the app will not crash.

### 3. Build & run

```powershell
.\gradlew.bat assembleDebug          # build debug APK
.\gradlew.bat installDebug           # build + install on a connected device/emulator
.\gradlew.bat test                   # JVM unit tests
.\gradlew.bat connectedAndroidTest   # instrumented tests (needs a device/emulator)
.\gradlew.bat lint                   # Android Lint
```

Prefer not to build at all? See [Download](#download) for a ready-to-install APK.

---

## Permissions

| Permission | Purpose |
|---|---|
| `INTERNET` | AI planner, weather, and Maps/Places API calls |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Map display and location-aware features |
| `READ_MEDIA_IMAGES` / `READ_MEDIA_VISUAL_USER_SELECTED` | Photo import via the system picker (Android 13+) |
| `ACCESS_MEDIA_LOCATION` | Reading GPS EXIF data from imported photos |
| `READ_EXTERNAL_STORAGE` (maxSdk 32) | Photo import on older Android versions |

All permission failures and denials disable only the dependent feature — the rest of the app keeps working.

---

## Team & Course Context

`orbit` is a term project for **PNU SW 2026 / Software Design & Experiment**, built by a 2-person team over roughly two weeks. By design, there is **no backend** in the current version — all real data is stored locally with Room, and "Friends'/World Earth" social content is intentionally dummy/sample data for this submission.

---

## Roadmap / Future Work

- Real **Friends' Earth** and **World Earth** backed by an actual backend service
- User accounts and authentication
- Cloud photo storage and cross-device sync
- Comments/likes and a shared global feed
- Friend graph and social sharing
