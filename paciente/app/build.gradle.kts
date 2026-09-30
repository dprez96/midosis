plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "cl.midosis.paciente"
    // Las bibliotecas actuales de AndroidX exigen compilar contra la API 37.
    compileSdk = 37

    defaultConfig {
        applicationId = "cl.midosis.paciente"
        // Android 9, según el informe: cubre los teléfonos que usan los adultos mayores.
        minSdk = 28
        // Comportamiento objetivo: Android 16, la versión probada en el banco de dispositivos.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("cl.midosis:motor")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test-junit"))
}
