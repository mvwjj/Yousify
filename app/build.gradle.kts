plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.veshikov.try1"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.veshikov.try1"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        
        // Добавляем манифестные плейсхолдеры для URL перенаправления
        manifestPlaceholders["redirectHostName"] = "127.0.0.1"
        manifestPlaceholders["redirectSchemeName"] = "http"
    }
    buildFeatures { 
        viewBinding = true 
        dataBinding = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Ktor server (CIO)
    implementation("io.ktor:ktor-server-cio:2.3.5")
    implementation("io.ktor:ktor-server-core:2.3.5")

    // Custom Tabs
    implementation("androidx.browser:browser:1.5.0")

    // Spotify Auth SDK
    implementation("com.spotify.android:auth:1.2.5")

    // Spotify Web API Kotlin MPP
    implementation("com.adamratzman:spotify-api-kotlin-android:4.1.0")

    // Retrofit/OkHttp (optional)
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.10.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Coil
    implementation("io.coil-kt:coil:2.2.2")
    
    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    
    // ConstraintLayout
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}