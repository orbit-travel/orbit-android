# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`orbit` is an Android travel-record + AI travel-planner app, built as a 2-person, ~2-week university term project (PNU SW 2026 / Software Design & Experiment). It is a single-module Android app with **no backend** — all real data is local (Room), and "Friends'/World Earth" social content is intentionally dummy data.

**`AGENT.md` in the repo root is the authoritative design constitution.** It dictates scope, the absolute priority order (course requirements > demo stability > core features > clean architecture > polish), backend prohibition, and the MVP checklist. Read it before any non-trivial change. Note that `AGENT.md` describes the *intended* design; some of it is aspirational (e.g. it names a `TravelDetailActivity` that does not exist yet). Always verify against the actual source. The `docs/` folder holds supporting design docs (`architecture.md`, `mvp-roadmap.md`, `demo-scenario.md`, `maps-and-3d-assets.md`, `wireframe.md`, `ppt-outline.md`).

## Build & test

Environment is Windows + PowerShell. Use the Gradle wrapper:

```powershell
.\gradlew.bat assembleDebug          # build debug APK
.\gradlew.bat installDebug           # build + install on connected device/emulator
.\gradlew.bat test                   # JVM unit tests (app/src/test)
.\gradlew.bat testDebugUnitTest --tests "com.pnu.orbit.ExampleUnitTest"   # single unit test
.\gradlew.bat connectedAndroidTest   # instrumented tests (needs device/emulator)
.\gradlew.bat lint                   # Android Lint
```

There is no separate ktlint/detekt; `lint` is the only static check wired up.

## API keys

Keys live in root `local.properties` (git-ignored, never commit). `app/build.gradle.kts` reads `MAPS_API_KEY`, optional `PLACES_API_KEY` (defaults to the maps key), and `GEMINI_API_KEY`, and exposes them as:
- manifest placeholder `${MAPS_API_KEY}` (Google Maps `meta-data`)
- `BuildConfig.MAPS_API_KEY` / `BuildConfig.PLACES_API_KEY` / `BuildConfig.GEMINI_API_KEY`

A missing key must degrade gracefully (fallback UI / demo data), never crash — e.g. `GeminiPlannerApi` throws on a blank key so the planner can surface an error/fallback instead of calling the API. See `docs/maps-and-3d-assets.md` for the Google Cloud key-restriction checklist.

## Architecture

MVVM + Repository, with **manual dependency injection** — there is no Hilt/Dagger.

- **`RepositoryProvider`** (object) is the single composition root. Activities/Fragments/ViewModels obtain repositories through it; it wires DAOs from `OrbitDatabase.getInstance()` and APIs from `RetrofitClient`. Add new repository factories here rather than constructing DAOs/APIs directly in UI code.
- **Layering rule (from AGENT.md):** UI → ViewModel → Repository (interface) → DAO/API. Repositories expose `domain.model` types; `data.mapper` converts between Room entities / Retrofit DTOs and domain models. This is the target, but note it isn't fully enforced today — `TravelPlannerViewModel` reaches into `OrbitDatabase.getInstance(...).planDao()` directly. Prefer routing new data access through a repository rather than copying that shortcut.
- **Repository interfaces vs. impls:** each repository is an interface (`TripRepository`, `PlannerRepository`, `EarthRepository`) with a `Local*`/`Dummy*` implementation. This boundary exists so local/dummy sources can later be swapped for a backend — preserve it.
- **UI state** is the sealed `UiState<T>` (`Loading` / `Empty` / `Success` / `Error`). User-facing async flows should drive this through a ViewModel + LiveData; do not skip the Empty/Error cases (stability is a graded item).
- **Coroutines** handle all async (Room, Retrofit, photo/metadata work).

### Navigation / Activity flow (actual)

Manifest registers four activities: `SplashActivity` (launcher) → `MainActivity` → `AddTripActivity` → `PlaceSearchActivity`. `MainActivity` hosts two fragments via `BottomNavigationView` + manual `FragmentTransaction.replace` (not Navigation Component): `TravelRecordFragment` and `TravelPlannerFragment`. Cross-screen data uses Intents with keys centralized in `util/IntentKeys`; prefer two-way flows via `ActivityResultLauncher`.

### Persistence specifics

- **Room DB** (`OrbitDatabase`, name `orbit.db`) is a hand-built singleton at **version 4** with `exportSchema = true` (schemas in `app/schemas/`). Entities: `TripEntity`, `TransportSegmentEntity`, `PhotoEntity`, `PlanEntity`, `SavedTravelPlanEntity`. Two manual migrations exist (`MIGRATION_1_2`, `MIGRATION_2_3`), but the builder also calls **`.fallbackToDestructiveMigration()`** — so there is currently **no `MIGRATION_3_4`** and a v3→v4 upgrade wipes local data. When changing the schema, bump the version and add a real `Migration` registered in `addMigrations(...)`; don't rely on the destructive fallback for data you want to keep.
- **`PhotoFileStore`** copies photos chosen via the system photo picker into app-private `filesDir/trip_photos` and stores `file://` URIs, because picker URI grants are temporary. Persisting must run off the main thread.
- **A trip is modeled as a sequence of `TransportSegment`s** (departure/arrival names + optional lat/lng, ordered by `sortOrder`), and each photo can attach to a segment (`segmentId`). `LocalTripRepository.addTrip/updateTrip` insert segments first, then map photos onto the generated segment ids. Update is delete-then-reinsert of a trip's segments and photos.

### Maps & geocoding (`map/` package)

`map/` is independent of the 3D Earth: `PlaceCoordinateResolver` resolves place names to coordinates (Places SDK, with `data/local/asset/AirportDataSource` providing offline airport coordinates as a fallback), `RoutePreviewBuilder` builds route previews, and `TripMapRenderer` draws markers/polylines on the Google map. Keep geocoding/network here off the main thread and degrading gracefully when a key or result is missing.

### Network & demo fallbacks

`RetrofitClient` builds **two** Retrofit instances: a placeholder one at `https://example.com/` (backs `AiPlannerApi`/`WeatherApi`, expected to fail in the term-project build) and a real one at `https://generativelanguage.googleapis.com/` for `geminiApi`. The live planner path is **Gemini**: `RepositoryProvider` wraps `geminiApi` + `BuildConfig.GEMINI_API_KEY` in `GeminiPlannerApi` (which implements `AiPlannerApi`) and injects it into `LocalPlannerRepository`. `GeminiPlannerApi` builds the prompt, calls `generateContent`, strips ```` ```json ```` fences, and parses the JSON; a shared `Mutex` serializes calls and `RetrofitClient`'s `RateLimitRetryInterceptor` retries HTTP 429 with backoff.

Failures (blank key, network error, malformed JSON) propagate as exceptions; the **ViewModel** (`TravelPlannerViewModel`) catches them and drives `UiState.Error`/fallback rather than `LocalPlannerRepository.createPlan` swallowing them. Sample/fallback content lives in `util/DemoFallbacks` (and the VM cleans up persisted fallback plans). Keep this no-crash-on-failure behavior when adding network features, and route sample/fallback content through `DemoFallbacks`.

### ML

`PhotoClassifier` is an interface returning a `PhotoTag`; `FallbackPhotoClassifier` is the no-real-model implementation. ML must never block photo import or crash — failures resolve to an `unknown`/fallback tag.

### 3D Earth

`ui/record/EarthModelView` wraps **SceneView/Filament** to render `assets/models/planet_earth.glb` (rotating, swipeable, tap-to-open Google satellite map). The model asset path is referenced via `EarthModelView.MODEL_ASSET`. This is visual concept only; the functional map lives on the maps surface, not here.

## Conventions

- Package root `com.pnu.orbit`; structure follows `data/{local{db,dao,entity,asset},remote{api,dto,client},repository,mapper}`, `domain/model`, `ui/<feature>` (`splash`, `main`, `record`, `addtrip`, `planner`, `common`), `map`, `ml`, `util`.
- Commit prefixes (from AGENT.md): `feat:`, `fix:`, `refactor:`, `chore:`, `docs:`, `ui:`, `test:`.
- Branches: `main` (submission), `develop` (integration), `feature/*`.
- Keep real local user data strictly separated from dummy social-Earth data; do not add backend/login/cloud/social-server code unless explicitly told to (see AGENT.md §16 scope guardrails).
