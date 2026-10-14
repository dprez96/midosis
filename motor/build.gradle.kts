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
    // Solo para leer los vectores de contrato/ en las pruebas; la biblioteca no la usa.
    testImplementation("tools.jackson.core:jackson-databind:3.2.3")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Los vectores de prueba del contrato son la fuente de verdad del calculo de
// cobertura: si cambian, las pruebas del motor tienen que volver a correr.
val vectoresDelContrato = layout.projectDirectory.dir("../contrato/vectores")

tasks.test {
    useJUnitPlatform()
    inputs.dir(vectoresDelContrato).withPropertyName("vectoresDelContrato")
    systemProperty("midosis.vectores", vectoresDelContrato.asFile.absolutePath)
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}
