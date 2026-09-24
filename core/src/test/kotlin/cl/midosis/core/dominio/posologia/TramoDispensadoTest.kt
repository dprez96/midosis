package cl.midosis.core.dominio.posologia

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TramoDispensadoTest {

    /** El caso del informe: losartan, 180 dias indicados, 60 dosis cada doce horas. */
    private val losartan = TramoDispensado(
        duracionIndicadaDias = 180,
        dosisEntregadas = 60,
        frecuencia = Frecuencia.de("PT12H"),
    )

    @Test
    fun `sesenta dosis cada doce horas cubren treinta dias`() {
        assertEquals(30, losartan.coberturaDias)
    }

    @Test
    fun `un tramo parcial no da el tratamiento por terminado`() {
        assertFalse(losartan.completaLoIndicado)
        assertEquals(150, losartan.diasPendientes)
    }

    @Test
    fun `un retiro que cubre lo indicado cierra el tratamiento`() {
        val completo = TramoDispensado(30, 30, Frecuencia.de("P1D"))
        assertTrue(completo.completaLoIndicado)
        assertEquals(0, completo.diasPendientes)
    }

    @Test
    fun `entregar de mas queda registrado en vez de pasar inadvertido`() {
        val demasiado = TramoDispensado(10, 30, Frecuencia.de("P1D"))
        assertTrue(demasiado.excedeLoIndicado)
        assertEquals(0, demasiado.diasPendientes)
    }

    @Test
    fun `calcula el dia en que se agota lo entregado`() {
        val retiro = LocalDate.of(2026, 10, 1)
        assertEquals(LocalDate.of(2026, 10, 31), losartan.seAgotaEl(retiro))
    }

    @Test
    fun `avisa del proximo retiro con anticipacion`() {
        val retiro = LocalDate.of(2026, 10, 1)
        assertEquals(LocalDate.of(2026, 10, 26), losartan.avisoDeProximoRetiro(retiro))
    }

    @Test
    fun `no avisa de un proximo retiro cuando el tratamiento esta completo`() {
        val completo = TramoDispensado(30, 30, Frecuencia.de("P1D"))
        assertNull(completo.avisoDeProximoRetiro(LocalDate.of(2026, 10, 1)))
    }

    @Test
    fun `un tramo muy corto no adelanta el aviso antes del propio retiro`() {
        val retiro = LocalDate.of(2026, 10, 1)
        val breve = TramoDispensado(90, 3, Frecuencia.de("P1D"))
        assertEquals(retiro, breve.avisoDeProximoRetiro(retiro))
    }

    @Test
    fun `una frecuencia semanal cubre siete dias por dosis`() {
        val semanal = TramoDispensado(90, 4, Frecuencia.de("P7D"))
        assertEquals(28, semanal.coberturaDias)
        assertEquals(62, semanal.diasPendientes)
    }

    @Test
    fun `rechaza valores que no describen ninguna dispensacion`() {
        assertFailsWith<IllegalArgumentException> {
            TramoDispensado(0, 60, Frecuencia.de("PT12H"))
        }
        assertFailsWith<IllegalArgumentException> {
            TramoDispensado(180, 0, Frecuencia.de("PT12H"))
        }
    }
}
