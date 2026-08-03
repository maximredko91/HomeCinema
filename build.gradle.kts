plugins {
    // AGP 9.0+ has built-in Kotlin support - no separate org.jetbrains.kotlin.android
    // plugin/version needed or allowed; AGP bundles its own Kotlin compiler.
    id("com.android.application") version "9.3.1" apply false
    // Kotlin 2.x moved the Compose compiler out of the Kotlin plugin into its own,
    // versioned in lockstep with Kotlin - replaces composeOptions.kotlinCompilerExtensionVersion.
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10" apply false
    id("com.google.devtools.ksp") version "2.3.10" apply false
}
