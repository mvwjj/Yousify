// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("com.google.devtools.ksp") version "1.9.23-1.0.20" apply false
}

allprojects {
    configurations.all {
        resolutionStrategy {
            force("org.xerial:sqlite-jdbc:3.34.0")
        }
    }
}

// Принудительно фиксируем Kotlin 1.9.23 для всех зависимостей
subprojects {
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-stdlib:1.9.23")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.23")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.23")
            force("org.jetbrains.kotlin:kotlin-reflect:1.9.23")
            force("org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23")
            force("org.jetbrains.kotlin:kotlin-annotation-processing-gradle:1.9.23")
        }
    }
}