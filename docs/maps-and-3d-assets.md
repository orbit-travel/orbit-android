# Maps and 3D Earth Assets

## Current implementation

The Travel Record tab currently uses `LowPolyEarthView`, a custom Android `View`.

This means:

- No `.glb`, `.gltf`, `.obj`, or texture file is required right now.
- The three Earths rotate automatically.
- Users can swipe each Earth to rotate it manually.
- Tapping an Earth runs a zoom-like animation and unfolds the Google satellite map panel.

This is the lower-risk MVP choice for the course demo because it does not depend on external 3D renderers, asset loading, or model licensing.

## If we later use a real 3D model

Recommended asset path:

```text
app/src/main/assets/models/low_poly_earth.glb
```

Recommended Android renderer option:

```text
SceneView Android / Google Filament
```

Candidate asset sources checked:

- Pixabay: "Earth Low Poly Planet" provides a downloadable GLB asset.
- SceneView Android: open-source Android 3D/AR rendering library powered by Google Filament.

Before adding a model file, verify the license and attribution requirements. Do not commit a model unless its license is acceptable for the project submission.

## Google Maps API key

Put the key in the project root `local.properties` file:

```properties
MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY
```

Do not commit `local.properties`.

The Gradle script reads `MAPS_API_KEY` and exposes it to:

- `AndroidManifest.xml` as `${MAPS_API_KEY}`
- `BuildConfig.MAPS_API_KEY`

The manifest metadata name is:

```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="${MAPS_API_KEY}" />
```

Google Cloud setup checklist:

- Enable Maps SDK for Android.
- Restrict the key to Android apps.
- Add package name `com.pnu.orbit`.
- Add the debug/release SHA-1 fingerprints used for the build.
