package cl.midosis.core.dominio.comuna

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CodigoComunaTest {

    @Test
    fun `acepta el codigo de cinco digitos del INE`() {
        assertEquals("13123", CodigoComuna.de("13123").valor)
    }

    @Test
    fun `rechaza cualquier otro formato`() {
        assertFailsWith<ComunaInvalida> { CodigoComuna.de("1312") }
        assertFailsWith<ComunaInvalida> { CodigoComuna.de("131234") }
        assertFailsWith<ComunaInvalida> { CodigoComuna.de("Providencia") }
        assertFailsWith<ComunaInvalida> { CodigoComuna.de("") }
    }
}
