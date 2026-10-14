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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // BuildConfig.DEBUG decide si se admiten las claves de prueba.
        buildConfig = true
    }

    // Las pruebas en la computadora leen los vectores de contrato/vectores.
    testOptions {
        unitTests.all {
            it.systemProperty("midosis.vectores", rootProject.file("../contrato/vectores").absolutePath)
            it.inputs.dir(rootProject.file("../contrato/vectores")).withPropertyName("vectoresDelContrato")
        }
    }
}

dependencies {
    implementation("cl.midosis:motor")
    implementation("cl.midosis:credencial")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test-junit"))
    // Android trae org.json, pero en las pruebas de la computadora solo hay esqueletos.
    testImplementation("org.json:json:20260814")

    // Pruebas que corren en un teléfono real: lo que depende del almacén de claves de
    // Android no se puede probar en la computadora.
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation(kotlin("test-junit"))
}
