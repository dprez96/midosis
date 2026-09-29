package cl.midosis.motor.planificacion

import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgrupadorDeTomasTest {

    private fun a(hora: Int, minuto: Int, segundo: Int = 0) =
        LocalDateTime.of(2026, 10, 1, hora, minuto, segundo)

    private val losartan = TomaProgramada("T-1", "Losartán 50 mg", "1 comprimido", a(8, 0))
    private val metformina = TomaProgramada("T-2", "Metformina 850 mg", "1 comprimido", a(8, 0))

    /** El criterio de aceptación de la HU-18, tal como está escrito. */
    @Test
    fun `dos tratamientos a la misma hora generan un solo aviso con ambos detallados`() {
        val avisos = AgrupadorDeTomas.agrupar(listOf(losartan, metformina))

        assertEquals(1, avisos.size)
        val aviso = avisos.single()
        assertEquals(a(8, 0), aviso.momento)
        assertEquals(listOf("Losartán 50 mg", "Metformina 850 mg"), aviso.tomas.map { it.producto })
        assertEquals(listOf("1 comprimido", "1 comprimido"), aviso.tomas.map { it.dosis })
        assertTrue(aviso.esAgrupado)
    }

    @Test
    fun `tomas en minutos distintos generan avisos distintos`() {
        val avisos = AgrupadorDeTomas.agrupar(listOf(losartan, metformina.copy(momento = a(8, 1))))

        assertEquals(listOf(a(8, 0), a(8, 1)), avisos.map { it.momento })
        assertFalse(avisos.any { it.esAgrupado })
    }

    @Test
    fun `los segundos no separan dos tomas del mismo minuto`() {
        val avisos = AgrupadorDeTomas.agrupar(
            listOf(losartan.copy(momento = a(8, 0, 5)), metformina.copy(momento = a(8, 0, 50)))
        )

        assertEquals(1, avisos.size)
        assertEquals(a(8, 0), avisos.single().momento)
    }

    @Test
    fun `tres medicamentos coincidentes van en un mismo aviso`() {
        val atorvastatina = TomaProgramada("T-3", "Atorvastatina 20 mg", "1 comprimido", a(8, 0))
        val avisos = AgrupadorDeTomas.agrupar(listOf(metformina, atorvastatina, losartan))

        assertEquals(3, avisos.single().tomas.size)
    }

    @Test
    fun `los avisos salen en orden cronologico aunque las tomas lleguen desordenadas`() {
        val noche = losartan.copy(momento = a(20, 0))
        val tarde = metformina.copy(momento = a(14, 0))
        val avisos = AgrupadorDeTomas.agrupar(listOf(noche, losartan, tarde))

        assertEquals(listOf(a(8, 0), a(14, 0), a(20, 0)), avisos.map { it.momento })
    }

    @Test
    fun `sin tomas no hay avisos`() {
        assertEquals(emptyList(), AgrupadorDeTomas.agrupar(emptyList()))
    }

    @Test
    fun `una sola toma genera un aviso que no es agrupado`() {
        val aviso = AgrupadorDeTomas.agrupar(listOf(losartan)).single()
        assertFalse(aviso.esAgrupado)
    }

    /**
     * Regla clínica pendiente del químico farmacéutico. Si esta prueba falla, alguien
     * cambió la ventana: agrupar minutos distintos adelanta tomas y necesita su visto bueno.
     */
    @Test
    fun `por defecto solo se agrupa el mismo minuto`() {
        assertEquals(Duration.ZERO, AgrupadorDeTomas.VENTANA_AGRUPACION)
    }

    @Test
    fun `con ventana se agrupan tomas cercanas a la hora de la primera`() {
        val avisos = AgrupadorDeTomas.agrupar(
            listOf(losartan, metformina.copy(momento = a(8, 5))),
            ventana = Duration.ofMinutes(10),
        )

        assertEquals(1, avisos.size)
        assertEquals(a(8, 0), avisos.single().momento)
    }

    @Test
    fun `con ventana los grupos no se encadenan`() {
        // 8:00, 8:08 y 8:16 con diez minutos: la tercera queda fuera, porque la ventana
        // se mide desde la primera toma del grupo y no desde la última.
        val t3 = TomaProgramada("T-3", "Atorvastatina 20 mg", "1 comprimido", a(8, 16))
        val avisos = AgrupadorDeTomas.agrupar(
            listOf(losartan, metformina.copy(momento = a(8, 8)), t3),
            ventana = Duration.ofMinutes(10),
        )

        assertEquals(listOf(a(8, 0), a(8, 16)), avisos.map { it.momento })
        assertEquals(2, avisos.first().tomas.size)
    }

    @Test
    fun `la toma justo en el borde de la ventana entra al grupo`() {
        val avisos = AgrupadorDeTomas.agrupar(
            listOf(losartan, metformina.copy(momento = a(8, 10))),
            ventana = Duration.ofMinutes(10),
        )

        assertEquals(1, avisos.size)
    }

    @Test
    fun `rechaza ventanas negativas o que ya moverian las tomas`() {
        assertFailsWith<IllegalArgumentException> {
            AgrupadorDeTomas.agrupar(listOf(losartan), ventana = Duration.ofMinutes(-1))
        }
        assertFailsWith<IllegalArgumentException> {
            AgrupadorDeTomas.agrupar(listOf(losartan), ventana = Duration.ofMinutes(61))
        }
    }

    @Test
    fun `una toma sin producto, dosis o tratamiento no se puede programar`() {
        assertFailsWith<IllegalArgumentException> { losartan.copy(producto = " ") }
        assertFailsWith<IllegalArgumentException> { losartan.copy(dosis = "") }
        assertFailsWith<IllegalArgumentException> { losartan.copy(tratamiento = "") }
    }
}
