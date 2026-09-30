package cl.midosis.paciente.alarmas

import android.content.Context
import android.os.Build
import java.io.File
import java.time.Instant

/**
 * Registro de cada disparo para el banco de dispositivos (HU-15): cuándo debía sonar,
 * cuándo sonó y el desfase. Con él se mide el criterio de que el 99 % de los disparos
 * ocurra dentro de 5 minutos.
 *
 * No guarda medicamentos, dosis ni nada del tratamiento: solo tiempos y el modelo del
 * teléfono. Por eso puede vivir en un archivo plano y no en la base cifrada (ADR-008).
 *
 * Se extrae de un teléfono de pruebas con:
 *   adb shell run-as cl.midosis.paciente cat files/disparos.csv
 */
class RegistroDeDisparos(contexto: Context) {

    private val archivo = File(contexto.filesDir, ARCHIVO)

    fun anotar(id: Int, programadaMs: Long, disparadaMs: Long) {
        synchronized(BLOQUEO) {
            if (!archivo.exists()) archivo.writeText(ENCABEZADO + "\n")
            archivo.appendText(linea(id, programadaMs, disparadaMs) + "\n")
        }
    }

    fun disparos(): List<Disparo> = synchronized(BLOQUEO) {
        if (!archivo.exists()) return emptyList()
        archivo.readLines().drop(1).mapNotNull(Disparo::desde)
    }

    fun vaciar() = synchronized(BLOQUEO) { archivo.delete() }

    companion object {
        const val ARCHIVO = "disparos.csv"
        const val ENCABEZADO = "id,programada,disparada,desfase_segundos,fabricante,modelo,sdk"
        private val BLOQUEO = Any()

        fun linea(id: Int, programadaMs: Long, disparadaMs: Long): String = listOf(
            id,
            Instant.ofEpochMilli(programadaMs),
            Instant.ofEpochMilli(disparadaMs),
            (disparadaMs - programadaMs) / 1000,
            Build.MANUFACTURER.orEmpty().replace(",", " "),
            Build.MODEL.orEmpty().replace(",", " "),
            Build.VERSION.SDK_INT,
        ).joinToString(",")
    }
}

data class Disparo(val programada: Instant, val disparada: Instant, val desfaseSegundos: Long) {
    companion object {
        fun desde(linea: String): Disparo? {
            val c = linea.split(",")
            if (c.size < 4) return null
            return runCatching {
                Disparo(Instant.parse(c[1]), Instant.parse(c[2]), c[3].toLong())
            }.getOrNull()
        }
    }
}
