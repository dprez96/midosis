package cl.midosis.core.dominio.demanda

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProyeccionDeDemandaTest {

    private val losartan = "7802250012344"
    private val metformina = "7802250012405"

    /** Lunes 2 de noviembre de 2026. */
    private val lunes = LocalDate.of(2026, 11, 2)

    private fun retiro(gtin: String = losartan, dia: LocalDate, indicado: Int = 180, cobertura: Int = 30, unidades: Double = 30.0) =
        Retiro(gtin, dia, indicado, cobertura, unidades)

    @Test
    fun `un tratamiento cronico anuncia un retiro cada vez que se agota lo entregado`() {
        // Retiró el 20/10 para 30 días: vuelve el 19/11, el 19/12 y el 18/1 (semanas 3, 7 y 12).
        val p = ProyeccionDeDemanda.proyectar(listOf(retiro(dia = LocalDate.of(2026, 10, 20))), lunes)

        assertEquals(12, p.semanas.size)
        assertEquals(lunes, p.semanas.first())
        assertEquals(listOf(0, 0, 30, 0, 0, 0, 30, 0, 0, 0, 0, 30), p.porProducto.getValue(losartan))
    }

    @Test
    fun `no proyecta mas alla del tratamiento indicado y el ultimo retiro es parcial`() {
        // 45 días indicados, 30 entregados: falta un retiro por 15 días, la mitad de las unidades.
        val p = ProyeccionDeDemanda.proyectar(listOf(retiro(dia = LocalDate.of(2026, 10, 20), indicado = 45)), lunes)
        assertEquals(15, p.porProducto.getValue(losartan).sum())
    }

    @Test
    fun `si lo entregado completa lo indicado no hay demanda futura`() {
        val p = ProyeccionDeDemanda.proyectar(listOf(retiro(dia = LocalDate.of(2026, 10, 28), indicado = 7, cobertura = 7, unidades = 21.0)), lunes)
        assertTrue(p.porProducto.isEmpty())
    }

    @Test
    fun `un retiro que ya debio renovarse no se proyecta otra vez`() {
        // El del 1/9 debía volver el 1/10; su paciente, si volvió, es el retiro del 1/10.
        val p = ProyeccionDeDemanda.proyectar(
            listOf(retiro(dia = LocalDate.of(2026, 9, 1)), retiro(dia = LocalDate.of(2026, 10, 1))),
            lunes,
        )
        // Y el del 1/10 debía volver el 31/10, también antes del lunes 2/11: nada se proyecta dos veces.
        assertTrue(p.porProducto.isEmpty())
    }

    @Test
    fun `la tasa de retorno corrige la demanda y se declara`() {
        // Diez retiros de antes de la ventana debían renovarse dentro de ella (el 15/9).
        val debian = (1..10).map { retiro(dia = LocalDate.of(2026, 8, 1), cobertura = 45, unidades = 45.0) }
        val volvieron = (1..5).map { retiro(dia = LocalDate.of(2026, 9, 25), indicado = 30) }
        val nuevo = retiro(dia = LocalDate.of(2026, 10, 20), indicado = 60)
        val p = ProyeccionDeDemanda.proyectar(debian + volvieron + nuevo, lunes)

        // En la ventana hubo 6 retiros (5 + el nuevo) frente a 10 esperados: 60 %.
        assertEquals(0.6, p.tasaDeRetorno.getValue(losartan), 1e-9)
        assertEquals(18, p.porProducto.getValue(losartan).sum())
        assertTrue(ProyeccionDeDemanda.SUPUESTO.contains("tasa de retorno"))
    }

    @Test
    fun `con pocos datos la tasa se supone completa`() {
        val p = ProyeccionDeDemanda.proyectar(listOf(retiro(dia = LocalDate.of(2026, 10, 20))), lunes)
        assertEquals(1.0, p.tasaDeRetorno.getValue(losartan))
    }

    @Test
    fun `separa los productos`() {
        val p = ProyeccionDeDemanda.proyectar(
            listOf(retiro(dia = LocalDate.of(2026, 10, 20)), retiro(gtin = metformina, dia = LocalDate.of(2026, 10, 27), unidades = 60.0)),
            lunes,
        )
        assertEquals(setOf(losartan, metformina), p.porProducto.keys)
        assertEquals(60, p.porProducto.getValue(metformina)[3])
    }

    @Test
    fun `la desviacion contrasta lo proyectado con lo retirado`() {
        // Proyectado: el retiro del 6/10 debía volver el 5/11 (semana del 2/11). Real: nadie volvió.
        val retiros = listOf(retiro(dia = LocalDate.of(2026, 10, 6)), retiro(gtin = metformina, dia = LocalDate.of(2026, 11, 3), unidades = 60.0))
        val desvios = ProyeccionDeDemanda.desviacion(retiros, hoy = LocalDate.of(2026, 11, 12), semanas = 1)

        assertEquals(
            setOf(Desvio(losartan, lunes, proyectado = 30, real = 0), Desvio(metformina, lunes, proyectado = 0, real = 60)),
            desvios.toSet(),
        )
    }
}
