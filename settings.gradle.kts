pluginManagement {
    repositories {
        // порядок важен: Gradle-Plugin-Portal → Google → MavenCentral → JitPack
        gradlePluginPortal()
        google()
        mavenCentral()
        maven { 
            url = uri("https://jitpack.io") 
            // Удаляем ограничения по группам, чтобы все зависимости могли загружаться
        }
    }
    plugins {
        // фиксируем версии, чтобы модули могли писать плагин без version
        id("com.android.application") version "8.4.0"
        id("org.jetbrains.kotlin.android") version "1.9.23"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
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
