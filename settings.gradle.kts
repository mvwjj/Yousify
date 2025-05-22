// settings.gradle.kts

pluginManagement {
    repositories {
        // порядок важен: Gradle Plugin Portal → Google → Maven Central → JitPack
        gradlePluginPortal()
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            // Удаляем ограничения по группам, чтобы все зависимости могли загружаться
        }
    }
    plugins {
        // фиксируем версии плагинов, чтобы модули могли писать их без версии
        id("com.android.application")            version "8.10.0"
        id("org.jetbrains.kotlin.android")       version "2.1.21"
        id("com.google.devtools.ksp")            version "2.1.21-2.0.1"
        id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            // Удаляем ограничения по группам, чтобы все зависимости могли загружаться
        }
    }
}

rootProject.name = "yousify"
include(":app")
