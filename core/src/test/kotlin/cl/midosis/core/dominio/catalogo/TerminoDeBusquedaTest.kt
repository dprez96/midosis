package cl.midosis.core.dominio.catalogo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TerminoDeBusquedaTest {

    @Test
    fun `quita tildes, dieresis y mayusculas`() {
        assertEquals("losartan", TerminoDeBusqueda.de("Losartán").normalizado)
        assertEquals("pinguino", TerminoDeBusqueda.de("PINGÜINO").normalizado)
        assertEquals("nino", TerminoDeBusqueda.de("Niño").normalizado)
    }

    @Test
    fun `junta los espacios repetidos y recorta los extremos`() {
        assertEquals("acido acetilsalicilico", TerminoDeBusqueda.de("  Ácido   acetilsalicílico ").normalizado)
    }

    @Test
    fun `descarta los caracteres de control`() {
        assertEquals("losartan", TerminoDeBusqueda.de("losar\u0000tan\n").normalizado)
    }

    @Test
    fun `exige un minimo de caracteres`() {
        assertFailsWith<TerminoInvalido> { TerminoDeBusqueda.de("l") }
        assertFailsWith<TerminoInvalido> { TerminoDeBusqueda.de("   ") }
        assertEquals("lo", TerminoDeBusqueda.de("lo").normalizado)
    }

    @Test
    fun `acota el largo`() {
        assertFailsWith<TerminoInvalido> { TerminoDeBusqueda.de("a".repeat(TerminoDeBusqueda.LARGO_MAXIMO + 1)) }
    }

    @Test
    fun `los comodines de SQL quedan como texto`() {
        assertEquals("%_", TerminoDeBusqueda.de("%_").normalizado)
    }
}
