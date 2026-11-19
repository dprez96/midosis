package cl.midosis.paciente.alarmas

import java.time.LocalDateTime
import java.util.Base64

/**
 * Convierte el plan de alarmas en texto y de vuelta, para guardarlo cifrado.
 *
 * Cada campo va en Base64: los nombres de medicamentos pueden traer cualquier carácter,
 * incluidos los que se usan como separadores, y así ninguno rompe el formato.
 */
object FormatoDelPlan {

    private const val VERSION = "2"
    private const val VERSION_ANTERIOR = "1"
    private val codificar = Base64.getEncoder()
    private val decodificar = Base64.getDecoder()

    fun escribir(plan: List<SolicitudDeAlarma>): String =
        buildString {
            append(VERSION).append('\n')
            for (s in plan) {
                val lineas = s.lineas.joinToString(",") { b64(it) }
                append(s.id).append(' ').append(b64(s.momento.toString())).append(' ').append(lineas)
                    .append(' ').append(b64(s.perfil)).append('\n')
            }
        }

    fun leer(texto: String): List<SolicitudDeAlarma> {
        val filas = texto.split('\n').filter { it.isNotEmpty() }
        // La versión 1, anterior a los perfiles, no trae el perfil: es del principal.
        require(filas.firstOrNull() == VERSION || filas.firstOrNull() == VERSION_ANTERIOR) { "formato del plan desconocido" }
        return filas.drop(1).map { fila ->
            val partes = fila.split(' ')
            require(partes.size == 3 || partes.size == 4) { "fila del plan mal formada" }
            SolicitudDeAlarma(
                id = partes[0].toInt(),
                momento = LocalDateTime.parse(desde(partes[1])),
                lineas = if (partes[2].isEmpty()) emptyList() else partes[2].split(',').map(::desde),
                perfil = partes.getOrNull(3)?.let(::desde).orEmpty(),
            )
        }
    }

    private fun b64(texto: String) = codificar.encodeToString(texto.toByteArray(Charsets.UTF_8))
    private fun desde(b64: String) = String(decodificar.decode(b64), Charsets.UTF_8)
}
