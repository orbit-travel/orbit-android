# Maps and 3D Earth Assets

## Current implementation

The Travel Log tab currently uses `EarthModelView`, a custom wrapper around SceneView.

This means:

- The Earth model is loaded from `app/src/main/assets/models/planet_earth.glb`.
- The asset location used by code is `models/planet_earth.glb`.
- The three Earths rotate automatically.
- Users can swipe each Earth to rotate it manually.
- Tapping an Earth runs a zoom-like animation and unfolds the Google satellite map panel.
- My Earth uses the original model color. Friends and World use a light teal overlay filter.

This uses SceneView/Filament because Android does not provide native GLB rendering out of the box.

## Asset path

Use this standard Android asset path:

```text
app/src/main/assets/models/<model-file>.glb
```

For the current file:

```text
app/src/main/assets/models/planet_earth.glb
```

If the model file is replaced, keep the same filename or update `EarthModelView.MODEL_ASSET`.

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
