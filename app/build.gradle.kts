@Suppress("DSL_SCOPE_VIOLATION") // if you use version catalogs
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.devtools.ksp") version "1.9.23-1.0.20"
}

android {
    namespace  = "com.veshikov.yousify"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.veshikov.yousify"
        minSdk        = 24
        targetSdk     = 34
        versionCode   = 1
        versionName   = "1.0"

        manifestPlaceholders += mapOf(
            "redirectHostName"   to "127.0.0.1",
            "redirectSchemeName" to "http"
        )

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf(
                    "room.incremental"     to "true",
                    "room.expandProjection" to "true"
                )
            }
        }
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
        compose     = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.13"
    }

    compileOptions {
        sourceCompatibility        = JavaVersion.VERSION_17
        targetCompatibility        = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    // — Compose BOM & UI
    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.foundation:foundation-layout")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")

    // — Core / UI
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // — Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // — Ktor (локальный OAuth)
    implementation("io.ktor:ktor-server-core:2.3.5")
    implementation("io.ktor:ktor-server-cio:2.3.5")

    // — Custom Tabs
    implementation("androidx.browser:browser:1.5.0")

    // — Spotify SDK
    implementation("com.spotify.android:auth:1.2.5")
    implementation("com.adamratzman:spotify-api-kotlin-android:4.1.0")
    implementation("com.adamratzman:spotify-api-kotlin-core:3.8.5")

    // — Network helpers
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.10.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // — Media3
    implementation("androidx.media:media:1.6.0")
    implementation("androidx.media3:media3-session:1.3.1")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // — Coil (images)
    implementation("io.coil-kt:coil:2.5.0")
    implementation("io.coil-kt:coil-compose:2.5.0")

    // — YouTube search
    implementation("com.github.TeamNewPipe:NewPipeExtractor:0.24.5")

    // — String similarity
    implementation("com.aallam.similarity:string-similarity-kotlin:0.1.0")

    // — TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.14.0")

    // — Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-paging:2.6.1")
    implementation("androidx.room:room-common:2.6.1")
    implementation("androidx.sqlite:sqlite:2.3.1")
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    ksp("androidx.room:room-compiler:2.6.1")

    // — WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // — Desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")

    // — Timber
    implementation("com.jakewharton.timber:timber:5.0.1")

    // — Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // — Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    // — Tests
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
