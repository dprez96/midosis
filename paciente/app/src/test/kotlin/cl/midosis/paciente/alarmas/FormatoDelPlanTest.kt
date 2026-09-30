package cl.midosis.paciente.alarmas

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FormatoDelPlanTest {

    private val plan = listOf(
        SolicitudDeAlarma(29845290, LocalDateTime.of(2026, 10, 1, 8, 0), listOf("Losartán 50 mg · 1 comprimido", "Metformina 850 mg · 1 comprimido")),
        SolicitudDeAlarma(29845350, LocalDateTime.of(2026, 10, 1, 9, 0), listOf("Levotiroxina 100 mcg · 1 comprimido")),
    )

    @Test
    fun `el plan sobrevive a escribirse y leerse`() {
        assertEquals(plan, FormatoDelPlan.leer(FormatoDelPlan.escribir(plan)))
    }

    @Test
    fun `los caracteres que usa el formato como separadores no lo rompen`() {
        val raro = listOf(
            SolicitudDeAlarma(1, LocalDateTime.of(2026, 10, 1, 8, 0), listOf("Uno, dos y tres", "Con espacio y\nsalto de línea", "ñandú · áéíóú")),
        )
        assertEquals(raro, FormatoDelPlan.leer(FormatoDelPlan.escribir(raro)))
    }

    @Test
    fun `un aviso sin lineas y un plan vacio tambien se conservan`() {
        val sinLineas = listOf(SolicitudDeAlarma(7, LocalDateTime.of(2026, 10, 1, 8, 0), emptyList()))
        assertEquals(sinLineas, FormatoDelPlan.leer(FormatoDelPlan.escribir(sinLineas)))
        assertEquals(emptyList(), FormatoDelPlan.leer(FormatoDelPlan.escribir(emptyList())))
    }

    @Test
    fun `un formato desconocido se rechaza en vez de interpretarse mal`() {
        assertFailsWith<IllegalArgumentException> { FormatoDelPlan.leer("9\n") }
        assertFailsWith<IllegalArgumentException> { FormatoDelPlan.leer("") }
    }
}
