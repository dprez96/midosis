package cl.midosis.paciente.tratamiento

import cl.midosis.credencial.Carga
import cl.midosis.credencial.Observacion
import cl.midosis.credencial.Producto
import cl.midosis.credencial.Via
import cl.midosis.motor.rutina.Rutina
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RevisionDelPlanTest {

    private val ahora = LocalDateTime.of(2026, 10, 14, 10, 7)

    private fun producto(etiqueta: String, f: String, e: Int, o: List<Observacion> = emptyList()) = Producto(
        gtin = "7802250012344", etiqueta = etiqueta, principioActivo = etiqueta, dosis = "1 comprimido",
        intervalo = f, duracionIndicadaDias = 180, dosisEntregadas = e, coberturaDias = 0, via = Via.ORAL, observaciones = o,
    )

    private val tratamiento = TratamientoGuardado(
        carga = Carga(
            "13123", "CL-FP-13123-01", "01M0877000WQNC8FHDYAA6Y83C", 0, 1,
            listOf(producto("Losartán", "PT12H", 60, listOf(Observacion.CON_ALIMENTOS)), producto("Atorvastatina", "PT24H", 30)),
        ),
        cargadoEn = 0,
        kid = "prueba-1",
    )

    private val revision = RevisionDelPlan(Planificacion.proponer(tratamiento, Rutina.POR_DEFECTO, ahora))

    @Test
    fun `muestra las tomas del primer día de cada medicamento`() {
        val losartan = revision.visibles(0).map { revision.planes[0].plan.tomas[it] }
        assertEquals(listOf(LocalDateTime.of(2026, 10, 14, 20, 0), LocalDateTime.of(2026, 10, 15, 8, 0)), losartan)
        assertEquals(1, revision.visibles(1).size)
    }

    @Test
    fun `mover una toma corre todas las del medicamento y conserva el intervalo`() {
        val movida = revision.mover(0, 60, ahora)
        val tomas = movida.planes[0].plan.tomas
        assertEquals(LocalDateTime.of(2026, 10, 14, 21, 0), tomas.first())
        assertEquals(LocalDateTime.of(2026, 10, 15, 9, 0), tomas[1])
        assertEquals(LocalDateTime.of(2026, 11, 13, 9, 0), tomas.last())
        assertEquals(60, tomas.size)
        assertTrue(movida.avisos.isEmpty())
        // El otro medicamento no cambia.
        assertEquals(revision.planes[1], movida.planes[1])
    }

    @Test
    fun `dejar la primera toma en el pasado se impide y se explica`() {
        val movida = revision.mover(0, -11 * 60, ahora)
        assertEquals(revision.planes, movida.planes)
        assertEquals("La primera toma no puede quedar antes de ahora.", movida.avisos[0])
    }

    @Test
    fun `un cambio aceptado borra el aviso anterior de ese medicamento`() {
        val rechazada = revision.mover(0, -11 * 60, ahora)
        val aceptada = rechazada.mover(0, -30, ahora)
        assertNull(aceptada.avisos[0])
        assertEquals(LocalDateTime.of(2026, 10, 14, 19, 30), aceptada.planes[0].plan.tomas.first())
    }

    @Test
    fun `las tomas confirmadas son las del plan revisado`() {
        val movida = revision.mover(1, -15, ahora)
        val tomas = Planificacion.tomasDe(tratamiento, movida.planes)
        assertEquals(90, tomas.size)
        assertEquals(movida.planes[1].plan.tomas.first(), tomas.filter { it.producto == "Atorvastatina" }.minOf { it.momento })
    }
}
