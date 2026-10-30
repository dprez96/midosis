package cl.midosis.paciente.alarmas

import java.time.Duration
import java.time.Instant
import java.util.Base64

/** Lo que el paciente contesta al recordatorio de una toma (HU-16, RF-M-12). */
enum class Respuesta(val codigo: String) {
    TOMADA("tomada"),
    POSPUESTA("pospuesta"),
    OMITIDA("omitida"),
    ;

    companion object {
        fun deCodigo(codigo: String): Respuesta = entries.first { it.codigo == codigo }
    }
}

/**
 * Una respuesta registrada: a qué aviso, cuándo estaba programado y cuándo contestó el paciente.
 * [respondidaEn] es la hora real de la respuesta y no la programada (criterio de la HU-16): es
 * la que cuenta para la adherencia.
 */
data class RespuestaRegistrada(
    val aviso: Int,
    val programada: Instant,
    val respuesta: Respuesta,
    val respondidaEn: Instant,
    /** Los medicamentos del aviso, como se mostraron («Losartán 50 mg · 1 comprimido»). */
    val lineas: List<String>,
)

/**
 * Si el paciente no contesta, el recordatorio se repite a los 10 y a los 25 minutos de la hora
 * programada, y después deja de insistir (RF-M-13). «Posponer» lo vuelve a mostrar en 10 minutos.
 */
object Reiteraciones {
    val DESPUES: List<Duration> = listOf(Duration.ofMinutes(10), Duration.ofMinutes(25))
    val POSPONER: Duration = Duration.ofMinutes(10)

    /** Los momentos de las reiteraciones, en orden; el intento 1 es el primero. */
    fun momentos(programada: Instant): List<Instant> = DESPUES.map { programada.plus(it) }
}

/**
 * Una línea por respuesta, separada por tabuladores. Los medicamentos van en Base64 porque
 * pueden traer cualquier carácter. El archivo se guarda cifrado (ADR-008): son datos de salud.
 */
object FormatoDelHistorial {
    fun escribir(respuestas: List<RespuestaRegistrada>): String =
        respuestas.joinToString("\n") { r ->
            listOf(
                r.aviso.toString(),
                r.programada.epochSecond.toString(),
                r.respuesta.codigo,
                r.respondidaEn.epochSecond.toString(),
                Base64.getEncoder().encodeToString(r.lineas.joinToString("\n").toByteArray(Charsets.UTF_8)),
            ).joinToString("\t")
        }

    fun leer(texto: String): List<RespuestaRegistrada> =
        texto.lines().filter { it.isNotBlank() }.map { linea ->
            val c = linea.split("\t")
            require(c.size == 5) { "respuesta guardada con ${c.size} campos" }
            RespuestaRegistrada(
                aviso = c[0].toInt(),
                programada = Instant.ofEpochSecond(c[1].toLong()),
                respuesta = Respuesta.deCodigo(c[2]),
                respondidaEn = Instant.ofEpochSecond(c[3].toLong()),
                lineas = String(Base64.getDecoder().decode(c[4]), Charsets.UTF_8).split("\n").filter { it.isNotEmpty() },
            )
        }
}
