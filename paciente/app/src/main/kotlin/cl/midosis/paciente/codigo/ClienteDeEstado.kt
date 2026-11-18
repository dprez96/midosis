package cl.midosis.paciente.codigo

import cl.midosis.paciente.tratamiento.Correcciones
import cl.midosis.paciente.tratamiento.EstadoDeCodigo
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Pregunta a core el estado de los códigos cargados en el teléfono (RF-B-15, RF-M-30): si la
 * farmacia corrigió o revocó alguno. Envía solo los jti, que son aleatorios: nada del paciente
 * ni del tratamiento. Sin conexión no pasa nada: se vuelve a intentar al abrir la aplicación.
 */
class ClienteDeEstado(private val urlCore: String) {

    /** Bloquea el hilo: llamarlo fuera del hilo principal. Null si no se pudo consultar. */
    fun consultar(ids: List<String>): Map<String, EstadoDeCodigo>? {
        if (urlCore.isBlank() || ids.isEmpty()) return null
        return try {
            val conexion = URL("$urlCore/api/estado").openConnection() as HttpURLConnection
            try {
                conexion.requestMethod = "POST"
                conexion.connectTimeout = TIEMPO_MS
                conexion.readTimeout = TIEMPO_MS
                conexion.doOutput = true
                conexion.setRequestProperty("Content-Type", "application/json")
                conexion.outputStream.use { it.write(Correcciones.consulta(ids).toByteArray(Charsets.UTF_8)) }
                if (conexion.responseCode != 200) return null
                Correcciones.interpretar(conexion.inputStream.bufferedReader().use { it.readText() })
            } finally {
                conexion.disconnect()
            }
        } catch (e: IOException) {
            null
        }
    }

    private companion object {
        const val TIEMPO_MS = 10_000
    }
}
