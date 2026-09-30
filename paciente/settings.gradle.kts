pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// El resolutor de cadenas de herramientas permite que Gradle obtenga por su
// cuenta el JDK 21 cuando el equipo no lo tiene instalado.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "paciente"
include(":app")

// El motor de planificación se compila desde su código fuente: el teléfono usa
// exactamente la misma lógica que core (ADR-007).
includeBuild("../motor")
