import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}
val mapsApiKey = localProperties.getProperty("MAPS_API_KEY").orEmpty()
val placesApiKey = localProperties.getProperty("PLACES_API_KEY", mapsApiKey).orEmpty()
val geminiApiKey = localProperties.getProperty("GEMINI_API_KEY").orEmpty()

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.pnu.orbit"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.pnu.orbit"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // TFLite and SceneView/Filament ship native .so libs for every ABI, which bloats
        // the APK. Keep only the ABIs we actually run on: arm64-v8a (real phones) and
        // x86_64 (emulator). Drops armeabi-v7a + x86, roughly halving the APK.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", mapsApiKey.asBuildConfigString())
        buildConfigField("String", "PLACES_API_KEY", placesApiKey.asBuildConfigString())
        buildConfigField("String", "GEMINI_API_KEY", geminiApiKey.asBuildConfigString())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
    }
    androidResources {
        // Keep the on-device ML model uncompressed so TFLite can mmap it from assets.
        noCompress += "tflite"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.viewpager2)
    implementation(libs.glide)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.maps)
    implementation(libs.places)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.sceneview)
    implementation(libs.litert)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
