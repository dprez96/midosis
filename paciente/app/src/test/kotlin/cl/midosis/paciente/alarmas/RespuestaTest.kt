package cl.midosis.paciente.alarmas

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class RespuestaTest {

    private val programada = Instant.parse("2026-10-27T23:00:00Z")

    @Test
    fun `sin respuesta el recordatorio se repite a los 10 y a los 25 minutos y nada más`() {
        assertEquals(
            listOf(Instant.parse("2026-10-27T23:10:00Z"), Instant.parse("2026-10-27T23:25:00Z")),
            Reiteraciones.momentos(programada),
        )
    }

    @Test
    fun `el historial guarda la hora real de la respuesta y no la programada`() {
        val respuestas = listOf(
            RespuestaRegistrada(29877922, programada, Respuesta.TOMADA, Instant.parse("2026-10-27T23:07:41Z"), listOf("Losartán 50 mg · 1 comprimido")),
            RespuestaRegistrada(
                29877922 + 720, programada.plusSeconds(43_200), Respuesta.OMITIDA, Instant.parse("2026-10-28T11:30:00Z"),
                listOf("Metformina 850 mg · 1 comprimido", "Atorvastatina, 20 mg\tcon\ttabuladores · 1 comprimido"),
            ),
        )
        val leidas = FormatoDelHistorial.leer(FormatoDelHistorial.escribir(respuestas))
        assertEquals(respuestas, leidas)
        assertEquals(Instant.parse("2026-10-27T23:07:41Z"), leidas.first().respondidaEn)
    }

    @Test
    fun `los códigos de respuesta se leen de vuelta`() {
        Respuesta.entries.forEach { assertEquals(it, Respuesta.deCodigo(it.codigo)) }
    }
}
