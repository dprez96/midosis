package cl.midosis.core.dominio.posologia

import cl.midosis.core.dominio.catalogo.Gtin
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PlantillaDePosologiaTest {

    private val losartan = Gtin.de("7802250012344")

    private fun nueva(
        cantidad: String = "1",
        unidad: String = "comprimido",
        frecuencia: String = "PT12H",
        duracionDias: Int? = 30,
        indicaciones: String? = null,
    ) = NuevaPlantilla.de(losartan, BigDecimal(cantidad), unidad, frecuencia, duracionDias, indicaciones)

    @Test
    fun `arma la posologia a partir de los campos estructurados`() {
        val plantilla = nueva(cantidad = "0.50", unidad = " Comprimido ", frecuencia = "pt8h", indicaciones = "Con alimentos")

        assertEquals(BigDecimal("0.5"), plantilla.dosis.cantidad)
        assertEquals(UnidadDeDosis.COMPRIMIDO, plantilla.dosis.unidad)
        assertEquals("PT8H", plantilla.frecuencia.toString())
        assertEquals("Con alimentos", plantilla.indicaciones)
    }

    @Test
    fun `una frecuencia en dias se guarda en horas, como la entrega el motor`() {
        assertEquals("PT24H", nueva(frecuencia = "P1D").frecuencia.toString())
    }

    @Test
    fun `la cantidad queda sin ceros sobrantes ni notacion cientifica`() {
        assertEquals("10", Dosis.normalizar(BigDecimal("10.00")).toString())
        assertEquals("2.5", Dosis.normalizar(BigDecimal("2.50")).toString())
    }

    @Test
    fun `rechaza cantidades fuera de rango o con mas de dos decimales`() {
        assertFailsWith<PosologiaInvalida> { nueva(cantidad = "0") }
        assertFailsWith<PosologiaInvalida> { nueva(cantidad = "-1") }
        assertFailsWith<PosologiaInvalida> { nueva(cantidad = "1000") }
        assertFailsWith<PosologiaInvalida> { nueva(cantidad = "0.125") }
    }

    @Test
    fun `rechaza una unidad fuera de la lista`() {
        val error = assertFailsWith<PosologiaInvalida> { nueva(unidad = "cucharada sopera") }
        assertEquals("unidad de dosis desconocida «cucharada sopera»", error.message)
    }

    @Test
    fun `rechaza una frecuencia que el motor no admite`() {
        assertFailsWith<PosologiaInvalida> { nueva(frecuencia = "PT10M") }
        assertFailsWith<PosologiaInvalida> { nueva(frecuencia = "cada ocho horas") }
    }

    @Test
    fun `la duracion es opcional pero acotada`() {
        assertNull(nueva(duracionDias = null).duracionDias)
        assertFailsWith<PosologiaInvalida> { nueva(duracionDias = 0) }
        assertFailsWith<PosologiaInvalida> { nueva(duracionDias = 366) }
    }

    @Test
    fun `las indicaciones se limpian y vacias equivalen a no tener`() {
        assertEquals("En ayunas, con agua", nueva(indicaciones = " En  ayunas,\ncon agua\u0000 ").indicaciones)
        assertNull(nueva(indicaciones = "   ").indicaciones)
        assertFailsWith<PosologiaInvalida> { nueva(indicaciones = "a".repeat(121)) }
    }
}
