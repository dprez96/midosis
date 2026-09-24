package cl.midosis.core.dominio.catalogo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GtinTest {

    /** El del ejemplo de carga util del informe, con su verificador corregido. */
    private val losartan = "7802250012344"

    @Test
    fun `acepta un GTIN-13 con verificador correcto`() {
        assertEquals(losartan, Gtin.de(losartan).valor)
    }

    @Test
    fun `acepta los demas largos normalizados`() {
        assertTrue(Gtin.esValido("96385074"))
        assertTrue(Gtin.esValido("012345678905"))
        assertTrue(Gtin.esValido("00012345678905"))
    }

    @Test
    fun `rechaza un verificador que no corresponde`() {
        assertFalse(Gtin.esValido("7802250012345"))
    }

    @Test
    fun `rechaza largos que ningun GTIN tiene`() {
        assertFailsWith<GtinInvalido> { Gtin.de("78012345") }
        assertFailsWith<GtinInvalido> { Gtin.de("780123456789") }
    }

    @Test
    fun `rechaza lo que no son digitos`() {
        assertFailsWith<GtinInvalido> { Gtin.de("78012E4567890") }
    }

    @Test
    fun `ignora los espacios que deja el lector`() {
        assertEquals(losartan, Gtin.de("  $losartan ").valor)
    }
}
