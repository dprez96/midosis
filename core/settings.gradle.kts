// El resolutor de cadenas de herramientas permite que Gradle obtenga por su
// cuenta el JDK 21 cuando el equipo no lo tiene instalado. Sin esto, quien solo
// tenga el JRE no puede compilar, que es el caso de una instalación corriente
// de Java en Windows.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "core"
