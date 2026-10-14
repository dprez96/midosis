package cl.midosis.paciente.rutina

import cl.midosis.motor.rutina.Rutina
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EntrevistaTest {

    private fun h(texto: String) = LocalTime.parse(texto)

    @Test
    fun `aceptar lo propuesto toma cinco pasos y da la rutina por defecto`() {
        var e = Entrevista()
        val pasos = mutableListOf(e.paso.numero)
        repeat(4) {
            e = e.siguiente()
            pasos += e.paso.numero
        }
        assertEquals(listOf(1, 2, 3, 4, 5), pasos)
        assertEquals(Rutina.POR_DEFECTO, e.rutina())
    }

    @Test
    fun `una cena antes del almuerzo no deja avanzar y dice por qué`() {
        val e = Entrevista().siguiente().mover(Entrevista.Hora.CENA, -9 * 60)
        assertEquals(Entrevista.Paso.COMIDAS, e.paso)
        assertEquals("La cena debe ser después del almuerzo.", e.problema)
        assertEquals(e, e.siguiente())
    }

    @Test
    fun `acostarse antes de la cena se detecta en su paso`() {
        val e = Entrevista().siguiente().siguiente().mover(Entrevista.Hora.ACOSTARSE, -4 * 60)
        assertEquals("La hora de acostarse debe ser después de la cena.", e.problema)
        assertNull(e.rutina())
    }

    @Test
    fun `una cena tarde no se objeta antes de preguntar la hora de acostarse`() {
        val e = Entrevista().siguiente().mover(Entrevista.Hora.CENA, 3 * 60)
        assertNull(e.problema)
    }

    @Test
    fun `mover una hora da la vuelta al reloj`() {
        val e = Entrevista().mover(Entrevista.Hora.ACOSTARSE, 2 * 60)
        assertEquals(h("01:00"), e.acostarse)
    }

    @Test
    fun `ajustar parte desde la rutina guardada`() {
        val propia = Rutina(h("06:00"), h("06:30"), h("12:30"), h("19:30"), h("22:00"), toleraAlarmasNocturnas = false)
        assertEquals(propia, Entrevista.desde(propia).rutina())
    }

    @Test
    fun `el formato guardado va y vuelve`() {
        val g = RutinaGuardada(Rutina(h("09:00"), h("09:30"), h("14:00"), h("21:30"), h("01:00"), false), porDefecto = false)
        assertEquals(g, FormatoDeRutina.leer(FormatoDeRutina.escribir(g)))
        assertEquals("07:00;08:00;13:00;20:00;23:00;nocturnas=si;defecto=si", FormatoDeRutina.escribir(RutinaGuardada(Rutina.POR_DEFECTO, true)))
    }
}
