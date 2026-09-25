// El resolutor de cadenas de herramientas permite que Gradle obtenga por su
// cuenta el JDK 21 cuando el equipo no lo tiene instalado.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "motor"
