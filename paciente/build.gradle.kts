plugins {
    id("com.android.application") version "9.4.1" apply false
    // Fija la versión de Kotlin que usa el soporte integrado del plugin de Android,
    // para que coincida con la de motor/ y los metadatos sean compatibles.
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
