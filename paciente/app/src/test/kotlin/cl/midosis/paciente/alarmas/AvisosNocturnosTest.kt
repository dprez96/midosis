package cl.midosis.paciente.alarmas

import cl.midosis.motor.rutina.Rutina
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AvisosNocturnosTest {

    private fun m(texto: String) = LocalDateTime.parse(texto)
    private fun h(texto: String) = LocalTime.parse(texto)
    private fun alarma(texto: String) = SolicitudDeAlarma(PlanDeAlarmas.identificador(m(texto)), m(texto), listOf("Amoxicilina · 1 cápsula"))

    /** Se levanta a las 07:00 y se acuesta a las 23:00. */
    private val rutina = Rutina.POR_DEFECTO
    private val ahora = m("2026-11-12T10:00")

    @Test
    fun `una toma de madrugada se avisa media hora antes de acostarse la noche anterior`() {
        val avisos = AvisosNocturnos.calcular(listOf(alarma("2026-11-13T03:00")), rutina, ahora)
        assertEquals(listOf(AvisoNocturno(m("2026-11-12T22:30"), listOf(h("03:00")))), avisos)
    }

    @Test
    fun `las tomas de una misma noche van en un solo aviso, en el orden de la noche`() {
        val alarmas = listOf("2026-11-12T23:30", "2026-11-13T03:00", "2026-11-13T15:00").map(::alarma)
        val avisos = AvisosNocturnos.calcular(alarmas, rutina, ahora)
        assertEquals(listOf(AvisoNocturno(m("2026-11-12T22:30"), listOf(h("23:30"), h("03:00")))), avisos)
    }

    @Test
    fun `cada noche con tomas tiene su aviso`() {
        val alarmas = listOf("2026-11-13T02:00", "2026-11-14T02:00").map(::alarma)
        assertEquals(listOf(m("2026-11-12T22:30"), m("2026-11-13T22:30")), AvisosNocturnos.calcular(alarmas, rutina, ahora).map { it.momento })
    }

    @Test
    fun `sin tomas de noche no hay aviso, tampoco por una toma a la hora de acostarse`() {
        val alarmas = listOf("2026-11-12T08:00", "2026-11-12T20:00", "2026-11-12T23:00").map(::alarma)
        assertTrue(AvisosNocturnos.calcular(alarmas, rutina, ahora).isEmpty())
    }

    @Test
    fun `un aviso cuya hora ya pasó no se programa`() {
        val tarde = m("2026-11-12T22:45")
        assertTrue(AvisosNocturnos.calcular(listOf(alarma("2026-11-13T03:00")), rutina, tarde).isEmpty())
    }

    @Test
    fun `con la hora de acostarse después de medianoche la noche se cuenta desde ahí`() {
        val noctambulo = Rutina(h("09:00"), h("09:30"), h("14:00"), h("21:30"), h("01:00"))
        val avisos = AvisosNocturnos.calcular(listOf(alarma("2026-11-13T04:00")), noctambulo, ahora)
        assertEquals(listOf(AvisoNocturno(m("2026-11-13T00:30"), listOf(h("04:00")))), avisos)
    }

    @Test
    fun `la frase concuerda con la cantidad de tomas`() {
        assertEquals("Tienes una toma a las 03:00.", AvisosNocturnos.frase(listOf(h("03:00"))))
        assertEquals("Tienes tomas a las 23:30 y a las 03:00.", AvisosNocturnos.frase(listOf(h("23:30"), h("03:00"))))
    }
}
