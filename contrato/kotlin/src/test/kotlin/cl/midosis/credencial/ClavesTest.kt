package cl.midosis.credencial

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClavesTest {

    private val publica = ByteArray(32) { 1 }

    @Test
    fun `un conjunto de producción no admite claves de prueba`() {
        val error = assertFailsWith<IllegalArgumentException> {
            ClavesDeConfianza(listOf(ClaveDeEmisor("prueba-1", publica, "13123", 0)), emptyList())
        }
        assertTrue("prueba-1" in error.message!!)
        assertFailsWith<IllegalArgumentException> {
            ClavesDeConfianza(emptyList(), listOf(ClaveDeContenido("prueba-c1", ByteArray(32))))
        }
    }

    @Test
    fun `un conjunto de producción admite sus propias claves`() {
        ClavesDeConfianza(listOf(ClaveDeEmisor("13123-2026", publica, "13123", 0)), listOf(ClaveDeContenido("c1", ByteArray(32))))
    }

    @Test
    fun `no admite dos claves con el mismo kid`() {
        assertFailsWith<IllegalArgumentException> {
            ClavesDeConfianza(
                listOf(ClaveDeEmisor("a", publica, "13123", 0), ClaveDeEmisor("a", publica, "13101", 0)),
                emptyList(),
            )
        }
    }

    @Test
    fun `rechaza claves de largo incorrecto`() {
        assertFailsWith<IllegalArgumentException> { ClaveDeEmisor("a", ByteArray(31), "13123", 0) }
        assertFailsWith<IllegalArgumentException> { ClaveDeContenido("c", ByteArray(16)) }
    }

    @Test
    fun `la vigencia incluye el inicio y excluye el término`() {
        val clave = ClaveDeEmisor("a", publica, "13123", vigenteDesde = 100, vigenteHasta = 200)
        assertFalse(clave.vigenteEn(99))
        assertTrue(clave.vigenteEn(100))
        assertTrue(clave.vigenteEn(199))
        assertFalse(clave.vigenteEn(200))
    }

    @Test
    fun `el firmante de prueba produce la clave pública del vector`() {
        val firmante = Vectores.firmante("prueba-1")
        val publica = Vectores.leer("claves-de-prueba.json").get("firma").toList().first().get("publica").asString()
        kotlin.test.assertEquals(publica, firmante.publica.hex())
    }
}
