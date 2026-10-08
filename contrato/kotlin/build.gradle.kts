import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.20"
    `java-library`
    jacoco
}

group = "cl.midosis"
version = "0.1.0"

repositories {
    mavenCentral()
}

// Igual que el motor: se construye con el JDK 21 y genera bytecode de Java 17,
// porque la usan core (JVM 21) y la aplicación Android.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    // CBOR: rechaza por omisión las claves repetidas y los bytes sobrantes.
    implementation("com.upokecenter:cbor:4.5.6")
    // Ed25519. Android lo trae recién desde la API 33 y la aplicación soporta la 28.
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")

    testImplementation(kotlin("test"))
    testImplementation("tools.jackson.core:jackson-databind:3.1.5")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Las pruebas corren contra los vectores de contrato/vectores, la fuente de
// verdad del formato: si cambian, tienen que volver a correr.
val vectores = layout.projectDirectory.dir("../vectores")

tasks.test {
    useJUnitPlatform()
    inputs.dir(vectores).withPropertyName("vectores")
    systemProperty("midosis.vectores", vectores.asFile.absolutePath)
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}
