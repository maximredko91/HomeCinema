import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Release signing - keystore.properties (repo root) + app/release.keystore are both
// gitignored and generated locally; only present on machines that need to produce a
// signed release build. Their absence just means the release build type stays unsigned.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.homecinema.library"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.homecinema.library"
        minSdk = 31
        targetSdk = 34
        versionCode = 11
        versionName = "1.7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Real backdrop blur for the "Стекло" theme (minSdk 31 specifically to support this
    // unconditionally - Haze itself falls back to a flat scrim below API 31, same as what
    // this app used to fake by hand, so there's no point in supporting that split)
    implementation("dev.chrisbanes.haze:haze:1.7.2")
    implementation("dev.chrisbanes.haze:haze-materials:1.7.2")

    // Media3 / ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.4.0")
    implementation("androidx.media3:media3-datasource:1.4.0")
    implementation("androidx.media3:media3-ui:1.4.0")
    implementation("androidx.media3:media3-common:1.4.0")

    // SMB client (SMB2/SMB3 support)
    implementation("eu.agno3.jcifs:jcifs-ng:2.1.10")

    // Tiny local HTTP server - bridges SMB streams to external players that can't
    // read smb:// URIs directly (only VLC understands that scheme natively)
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // Room (local library cache)
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // DataStore for settings (SMB host/user/pass etc.)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Android-Keystore-backed encryption for SMB source passwords
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Image loading (posters), with support for custom fetchers
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // WorkManager (persistent downloads + scheduled library rescans)
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    // XML parsing for .nfo (kotlinx or plain XmlPullParser is fine - using built-in XmlPullParser, no extra dep needed)

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Unit tests (JVM, no device/emulator needed - ./gradlew test)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    // JVM XmlPullParser implementation - the Android platform provides one on-device,
    // but plain unit tests run on the host JVM and need this for NfoParser's tests.
    testImplementation("net.sf.kxml:kxml2:2.3.0")

    // Instrumented UI tests (need a device/emulator - ./gradlew connectedAndroidTest)
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
