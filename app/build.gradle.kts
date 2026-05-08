// build.gradle.kts (App module)
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)          // Kotlin Symbol Processing для Room
}

android {
    namespace   = "com.example.carjournal"
    compileSdk  = 34

    defaultConfig {
        applicationId = "com.example.carjournal"
        minSdk        = 24
        targetSdk     = 34
        versionCode   = 1
        versionName   = "1.0"

        // Читаем ключ из local.properties
        val localProps = java.util.Properties()
        val localFile = rootProject.file("local.properties")
        if (localFile.exists()) localProps.load(localFile.inputStream())
        manifestPlaceholders["MAPS_API_KEY"] = localProps.getProperty("MAPS_API_KEY", "")
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    // ── Jetpack Compose ──
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // ── Activity & Lifecycle ──
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // ── Room (база данных) ──
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)      // поддержка Coroutines/Flow
    ksp(libs.androidx.room.compiler)            // генератор кода Room

    // ── Material Components (нужен для XML-темы NoActionBar) ──
    implementation(libs.googleMaterial)

    // ── Coil — асинхронная загрузка изображений из сети ──
    implementation(libs.coilCompose)

    // ── OSMDroid — карта OpenStreetMap ──
    implementation(libs.osmdroid)

    // ── Google Maps ──
    implementation(libs.playServicesMaps)
    implementation(libs.mapsCompose)

    // ── Play Services Location — GPS/геолокация ──
    implementation(libs.playServicesLocation)

    // ── OkHttp — HTTP-клиент для Wikipedia и Overpass API ──
    implementation(libs.okhttp)

    // ── Coroutines ──
    implementation(libs.kotlinx.coroutines.android)
}
