package cl.midosis.paciente.tratamiento

import cl.midosis.credencial.Carga
import cl.midosis.credencial.Producto
import cl.midosis.credencial.Via
import cl.midosis.motor.rutina.Rutina
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AvisosDeRetiroTest {

    private val zona = ZoneOffset.UTC
    /** El retiro: 1 de noviembre de 2026 a las 10:00 UTC. */
    private val emitido = LocalDateTime.of(2026, 11, 1, 10, 0).toEpochSecond(zona)
    private val ahora = LocalDateTime.of(2026, 11, 1, 12, 0)

    private fun producto(gtin: String, etiqueta: String, indicado: Int, entregadas: Int, intervalo: String = "PT24H") = Producto(
        gtin = gtin, etiqueta = etiqueta, principioActivo = etiqueta, dosis = "1 comprimido", intervalo = intervalo,
        duracionIndicadaDias = indicado, dosisEntregadas = entregadas, coberturaDias = 0, via = Via.ORAL, observaciones = emptyList(),
    )

    private fun tratamiento(id: String, emitidoEn: Long, cargadoEn: Long, vararg productos: Producto) = TratamientoGuardado(
        carga = Carga("13123", "CL-FP-13123-01", id, emitidoEn, emitidoEn + 72 * 3600, productos.toList()),
        cargadoEn = cargadoEn,
        kid = "prueba-1",
    )

    @Test
    fun `avisa 5 dias antes de que se acabe, a la hora del desayuno`() {
        // 30 comprimidos una vez al día desde el 1/11: se acaba el 1/12 y se avisa el 26/11.
        val avisos = AvisosDeRetiro.calcular(
            listOf(tratamiento("01M0877000WQNC8FHDYAA6Y83C", emitido, emitido, producto("7802250012344", "Losartán 50 mg", 180, 30))),
            Rutina.POR_DEFECTO,
            ahora,
            zona,
        )
        assertEquals(1, avisos.size)
        assertEquals(LocalDateTime.of(2026, 11, 26, 8, 0), avisos.single().momento)
        assertEquals(listOf(ProductoPorAcabarse("Losartán 50 mg", LocalDate.of(2026, 12, 1))), avisos.single().productos)
    }

    @Test
    fun `si lo retirado completa el tratamiento no hay aviso`() {
        val avisos = AvisosDeRetiro.calcular(
            listOf(tratamiento("01M0877000WQNC8FHDYAA6Y83C", emitido, emitido, producto("7802250012344", "Amoxicilina 500 mg", 7, 21, "PT8H"))),
            Rutina.POR_DEFECTO,
            ahora,
            zona,
        )
        assertTrue(avisos.isEmpty())
    }

    @Test
    fun `los medicamentos que se acaban el mismo dia van en un solo aviso`() {
        val avisos = AvisosDeRetiro.calcular(
            listOf(
                tratamiento(
                    "01M0877000WQNC8FHDYAA6Y83C", emitido, emitido,
                    producto("7802250012344", "Losartán 50 mg", 180, 30),
                    producto("7802250012405", "Metformina 850 mg", 180, 60, "PT12H"),
                ),
            ),
            Rutina.POR_DEFECTO,
            ahora,
            zona,
        )
        assertEquals(1, avisos.size)
        assertEquals(listOf("Losartán 50 mg", "Metformina 850 mg"), avisos.single().productos.map { it.etiqueta })
    }

    @Test
    fun `al cargar el tramo siguiente del mismo producto, avisa por ese y no por el anterior`() {
        val siguiente = LocalDateTime.of(2026, 11, 28, 10, 0).toEpochSecond(zona)
        val avisos = AvisosDeRetiro.calcular(
            listOf(
                tratamiento("01M0877000WQNC8FHDYAA6Y83C", emitido, emitido, producto("7802250012344", "Losartán 50 mg", 180, 30)),
                tratamiento("01M0877000WQNC8FHDYAA6Y83D", siguiente, siguiente, producto("7802250012344", "Losartán 50 mg", 150, 30)),
            ),
            Rutina.POR_DEFECTO,
            ahora,
            zona,
        )
        // El tramo nuevo se acaba el 28/12: se avisa el 23/12 y ya no el 26/11.
        assertEquals(listOf(LocalDateTime.of(2026, 12, 23, 8, 0)), avisos.map { it.momento })
    }

    @Test
    fun `un aviso cuya hora ya paso no se programa`() {
        val avisos = AvisosDeRetiro.calcular(
            listOf(tratamiento("01M0877000WQNC8FHDYAA6Y83C", emitido, emitido, producto("7802250012344", "Losartán 50 mg", 180, 30))),
            Rutina.POR_DEFECTO,
            LocalDateTime.of(2026, 11, 27, 9, 0),
            zona,
        )
        assertTrue(avisos.isEmpty())
    }
}
