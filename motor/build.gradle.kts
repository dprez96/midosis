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

// Se construye con el JDK 21, pero genera bytecode de Java 17: la biblioteca la
// consumen core (JVM 21) y la aplicacion Android, que no acepta cualquier version.
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
    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}
