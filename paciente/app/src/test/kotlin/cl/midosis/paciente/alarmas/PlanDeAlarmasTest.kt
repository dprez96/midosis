package cl.midosis.paciente.alarmas

import cl.midosis.motor.planificacion.TomaProgramada
import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PlanDeAlarmasTest {

    private val ahora = LocalDateTime.of(2026, 10, 1, 7, 30)
    private fun a(h: Int, m: Int) = LocalDateTime.of(2026, 10, 1, h, m)

    @Test
    fun `las tomas coincidentes producen una sola alarma con ambos medicamentos`() {
        val plan = PlanDeAlarmas.construir(
            listOf(
                TomaProgramada("T-1", "Losartán 50 mg", "1 comprimido", a(8, 0)),
                TomaProgramada("T-2", "Metformina 850 mg", "1 comprimido", a(8, 0)),
            ),
            ahora,
        )

        assertEquals(1, plan.size)
        assertEquals(listOf("Losartán 50 mg · 1 comprimido", "Metformina 850 mg · 1 comprimido"), plan.single().lineas)
    }

    @Test
    fun `las tomas que ya pasaron no se programan`() {
        val plan = PlanDeAlarmas.construir(
            listOf(
                TomaProgramada("T-1", "Losartán 50 mg", "1 comprimido", a(7, 0)),
                TomaProgramada("T-1", "Losartán 50 mg", "1 comprimido", a(7, 30)),
                TomaProgramada("T-1", "Losartán 50 mg", "1 comprimido", a(20, 0)),
            ),
            ahora,
        )

        assertEquals(listOf(a(20, 0)), plan.map { it.momento })
    }

    @Test
    fun `el identificador es el mismo para el mismo minuto y distinto para otro`() {
        assertEquals(PlanDeAlarmas.identificador(a(8, 0)), PlanDeAlarmas.identificador(a(8, 0)))
        assertNotEquals(PlanDeAlarmas.identificador(a(8, 0)), PlanDeAlarmas.identificador(a(8, 1)))
    }

    @Test
    fun `programar dos veces el mismo plan da los mismos identificadores`() {
        val tomas = SerieDePrueba.serie(ahora, Duration.ofMinutes(30), Duration.ofHours(3))
        assertEquals(
            PlanDeAlarmas.construir(tomas, ahora).map { it.id },
            PlanDeAlarmas.construir(tomas, ahora).map { it.id },
        )
    }

    @Test
    fun `la alarma unica de prueba suena en un minuto y agrupa dos medicamentos`() {
        val plan = PlanDeAlarmas.construir(SerieDePrueba.alarmaUnica(ahora), ahora)

        assertEquals(a(7, 31), plan.single().momento)
        assertEquals(2, plan.single().lineas.size)
    }

    @Test
    fun `la serie de banco reparte las alarmas en el intervalo pedido`() {
        val plan = PlanDeAlarmas.construir(
            SerieDePrueba.serie(ahora, Duration.ofMinutes(30), Duration.ofHours(24)),
            ahora,
        )

        assertEquals(48, plan.size)
        assertEquals(a(8, 0), plan.first().momento)
        assertTrue(plan.zipWithNext().all { (x, y) -> Duration.between(x.momento, y.momento) == Duration.ofMinutes(30) })
    }

    @Test
    fun `la serie de banco no supera el maximo permitido`() {
        assertFailsWith<IllegalArgumentException> {
            SerieDePrueba.serie(ahora, Duration.ofMinutes(1), Duration.ofDays(1))
        }
    }
}
