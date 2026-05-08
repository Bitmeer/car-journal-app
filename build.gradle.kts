// build.gradle.kts (Project level)
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android)      apply false
    alias(libs.plugins.kotlin.compose)      apply false
    // KSP нужен для Room annotation processor
    alias(libs.plugins.ksp)                 apply false
}
