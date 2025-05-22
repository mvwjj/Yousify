// Top-level build file where you can add configuration options common to all sub-projects/modules.

// ИСПРАВЛЕНО: Удалена установка свойства org.sqlite.tmpdir
// System.setProperty(
//     "org.sqlite.tmpdir",
//     "C:/Users/aleksandr/AndroidStudioProjects/Youify/build/tmp/sqlite"
// )

plugins {
    // через version catalogs оставляем alias’ы без apply,
    // но для KSP подключаем конкретный плагин по ID
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android)     apply false
    id("com.google.devtools.ksp")          apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
}

allprojects {
    configurations.all {
        resolutionStrategy {
            // Фиксируем версии плагинов данных:
            force("org.xerial:sqlite-jdbc:3.44.1.0")
            force("androidx.databinding:databinding-ktx:8.10.0")
        }
    }
}

subprojects {
    configurations.all {
        resolutionStrategy {
            // Принудительно фиксируем Kotlin 2.1.21 для всех модулей
            force("org.jetbrains.kotlin:kotlin-stdlib:2.1.21")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.1.21")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.1.21")
            force("org.jetbrains.kotlin:kotlin-reflect:2.1.21")
            force("org.jetbrains.kotlin:kotlin-stdlib-common:2.1.21")
            force("org.jetbrains.kotlin:kotlin-annotation-processing-gradle:2.1.21")
        }
    }
}