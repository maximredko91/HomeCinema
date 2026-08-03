pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    // Lets Gradle auto-download a matching JDK for the daemon toolchain (see
    // gradle/gradle-daemon-jvm.properties) instead of depending on whatever JAVA_HOME
    // or Android Studio's configured JDK happens to be on a given machine.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "HomeCinema"
include(":app")
