package cl.midosis.motor.rutina

import java.time.Duration
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RutinaTest {

    private fun h(texto: String) = LocalTime.parse(texto)

    private fun problema(despertar: String, desayuno: String, almuerzo: String, cena: String, acostarse: String) =
        Rutina.problema(h(despertar), h(desayuno), h(almuerzo), h(cena), h(acostarse))

    @Test
    fun `la rutina por defecto es la del informe`() {
        val r = Rutina.POR_DEFECTO
        assertEquals(listOf(h("07:00"), h("08:00"), h("13:00"), h("20:00"), h("23:00")), listOf(r.despertar) + r.comidas + r.acostarse)
        assertEquals(Duration.ofHours(16), r.vigilia)
    }

    @Test
    fun `una secuencia corriente es coherente`() {
        assertNull(problema("06:30", "07:00", "13:30", "21:00", "23:30"))
    }

    @Test
    fun `se puede acostar despues de la medianoche`() {
        val r = Rutina(h("09:00"), h("09:30"), h("14:00"), h("21:30"), h("01:00"))
        assertEquals(Duration.ofHours(16), r.vigilia)
        assertTrue(r.duerme(h("03:00")))
        assertFalse(r.duerme(h("00:30")))
    }

    @Test
    fun `el desayuno puede ser al despertar`() {
        assertNull(problema("07:00", "07:00", "13:00", "20:00", "23:00"))
    }

    @Test
    fun `la cena antes del almuerzo se señala con un mensaje claro`() {
        assertEquals("La cena debe ser después del almuerzo.", problema("07:00", "08:00", "13:00", "12:00", "23:00"))
        assertFailsWith<RutinaInvalida> { Rutina(h("07:00"), h("08:00"), h("13:00"), h("12:00"), h("23:00")) }
    }

    @Test
    fun `cada desorden tiene su propio mensaje`() {
        assertEquals("El desayuno debe ser después de despertar.", problema("08:00", "07:30", "13:00", "20:00", "23:00"))
        assertEquals("El almuerzo debe ser después del desayuno.", problema("07:00", "13:00", "08:00", "20:00", "23:00"))
        assertEquals("La hora de acostarse debe ser después de la cena.", problema("07:00", "08:00", "13:00", "22:00", "21:00"))
    }

    @Test
    fun `exige al menos tres horas de sueño`() {
        assertEquals("Entre acostarse y despertar deben quedar al menos 3 horas.", problema("07:00", "08:00", "13:00", "20:00", "05:00"))
        assertEquals("Entre acostarse y despertar deben quedar al menos 3 horas.", problema("07:00", "08:00", "13:00", "20:00", "07:00"))
        assertNull(problema("07:00", "08:00", "13:00", "20:00", "04:00"))
    }

    @Test
    fun `sabe cuando duerme el paciente`() {
        val r = Rutina.POR_DEFECTO
        assertTrue(r.duerme(h("02:00")))
        assertTrue(r.duerme(h("23:00")))
        assertFalse(r.duerme(h("07:00")))
        assertFalse(r.duerme(h("22:59")))
    }
}
