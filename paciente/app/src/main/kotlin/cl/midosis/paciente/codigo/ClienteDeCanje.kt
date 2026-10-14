package cl.midosis.paciente.codigo

import cl.midosis.credencial.CodigoCorto
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Pide a core el código completo a partir del código corto y el PIN (HU-10). Lo que vuelve es
 * el mismo texto del QR, y se verifica igual que si se hubiera escaneado: este cliente no
 * confía en el servidor para nada más que entregarlo.
 *
 * No envía nada del paciente: solo el código corto y el PIN que tiene impresos.
 */
class ClienteDeCanje(private val urlCore: String) {

    sealed interface Respuesta {
        data class Codigo(val texto: String) : Respuesta

        /** Core respondió con un motivo que se puede mostrar tal cual. */
        data class Rechazo(val mensaje: String) : Respuesta

        data object SinConexion : Respuesta

        data object NoDisponible : Respuesta
    }

    val disponible: Boolean get() = urlCore.isNotBlank()

    /** Bloquea el hilo: llamarlo fuera del hilo principal. */
    fun canjear(codigo: CodigoCorto, pin: String): Respuesta {
        if (!disponible) return Respuesta.NoDisponible
        return try {
            val conexion = URL("$urlCore/api/canje").openConnection() as HttpURLConnection
            try {
                conexion.requestMethod = "POST"
                conexion.connectTimeout = TIEMPO_MS
                conexion.readTimeout = TIEMPO_MS
                conexion.doOutput = true
                conexion.setRequestProperty("Content-Type", "application/json")
                val cuerpo = JSONObject().put("codigo", codigo.valor).put("pin", pin).toString()
                conexion.outputStream.use { it.write(cuerpo.toByteArray(Charsets.UTF_8)) }
                val estado = conexion.responseCode
                val respuesta = (if (estado in 200..299) conexion.inputStream else conexion.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                interpretar(estado, respuesta)
            } finally {
                conexion.disconnect()
            }
        } catch (e: IOException) {
            Respuesta.SinConexion
        }
    }

    companion object {
        private const val TIEMPO_MS = 10_000

        /** Separado para probarlo sin red. */
        fun interpretar(estado: Int, cuerpo: String): Respuesta {
            val json = runCatching { JSONObject(cuerpo) }.getOrNull()
            if (estado == 200) {
                val texto = json?.optString("codigo").orEmpty()
                return if (texto.isNotBlank()) Respuesta.Codigo(texto) else Respuesta.Rechazo(GENERICO)
            }
            val mensaje = json?.optString("error")?.takeIf { it.isNotBlank() } ?: GENERICO
            val restantes = json?.optInt("intentosRestantes", -1) ?: -1
            return Respuesta.Rechazo(
                if (restantes >= 0) "$mensaje ${if (restantes == 1) "Te queda 1 intento." else "Te quedan $restantes intentos."}" else mensaje,
            )
        }

        private const val GENERICO = "No se pudo cargar con ese código. Intenta de nuevo o pide ayuda en la farmacia."
    }
}
