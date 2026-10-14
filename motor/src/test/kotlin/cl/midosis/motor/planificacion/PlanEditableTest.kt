package cl.midosis.motor.planificacion

import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class PlanEditableTest {

    private val ahora = LocalDateTime.of(2026, 10, 14, 10, 0)
    private fun a(hora: String, dias: Long = 0) = LocalDateTime.parse("2026-10-14T$hora").plusDays(dias)

    /** Cada 12 horas, con alimentos: cena y desayuno. */
    private val plan = PlanEditable(listOf(a("20:00"), a("08:00", 1), a("20:00", 1), a("08:00", 2)), Duration.ofHours(12))

    @Test
    fun `correr una toma y las siguientes se acepta y conserva las distancias`() {
        val r = assertIs<PlanEditable.Resultado.Aceptado>(plan.moverDesde(1, 60, ahora))
        assertEquals(listOf(a("20:00"), a("09:00", 1), a("21:00", 1), a("09:00", 2)), r.plan.tomas)
    }

    @Test
    fun `atrasar la primera toma corre todo el plan`() {
        val r = assertIs<PlanEditable.Resultado.Aceptado>(plan.moverDesde(0, 30, ahora))
        assertEquals(a("20:30"), r.plan.tomas.first())
    }

    @Test
    fun `mover una sola toma que se acerca a la siguiente se impide y explica el intervalo`() {
        val r = assertIs<PlanEditable.Resultado.Rechazado>(plan.moverSola(1, 60, ahora))
        assertEquals(
            "Tu medicamento es cada 12 horas: dos tomas no pueden quedar a menos de 12 horas. " +
                "Para cambiar la hora, mueve también las tomas siguientes.",
            r.motivo,
        )
    }

    @Test
    fun `mover sola la última toma más tarde se acepta`() {
        assertIs<PlanEditable.Resultado.Aceptado>(plan.moverSola(3, 45, ahora))
    }

    @Test
    fun `adelantar la primera toma al pasado se impide`() {
        val r = assertIs<PlanEditable.Resultado.Rechazado>(plan.moverDesde(0, -11 * 60, ahora))
        assertEquals("La primera toma no puede quedar antes de ahora.", r.motivo)
    }

    @Test
    fun `una vez al día se explica en palabras`() {
        val diario = PlanEditable(listOf(a("23:00"), a("23:00", 1)), Duration.ofHours(24))
        val r = assertIs<PlanEditable.Resultado.Rechazado>(diario.moverSola(0, 60, ahora))
        assertEquals(true, r.motivo.startsWith("Tu medicamento es una vez al día"))
    }

    @Test
    fun `no acepta un plan que ya viola el intervalo`() {
        assertFailsWith<IllegalArgumentException> { PlanEditable(listOf(a("08:00"), a("12:00")), Duration.ofHours(8)) }
    }
}
