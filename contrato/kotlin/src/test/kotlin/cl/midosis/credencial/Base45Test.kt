package cl.midosis.credencial

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Base45Test {

    /** Los ejemplos de RFC 9285, sección 4.3. */
    private val ejemplos = listOf(
        "AB" to "BB8",
        "Hello!!" to "%69 VD92EX0",
        "base-45" to "UJCLQE7W581",
        "ietf!" to "QED8WEX0",
    )

    @Test
    fun `codifica los ejemplos del RFC`() {
        for ((texto, base45) in ejemplos) assertEquals(base45, Base45.codificar(texto.toByteArray()))
    }

    @Test
    fun `decodifica los ejemplos del RFC`() {
        for ((texto, base45) in ejemplos) assertContentEquals(texto.toByteArray(), Base45.decodificar(base45))
    }

    @Test
    fun `ida y vuelta con todos los valores de un byte`() {
        val todos = ByteArray(256) { it.toByte() }
        assertContentEquals(todos, Base45.decodificar(Base45.codificar(todos)))
        assertContentEquals(ByteArray(0), Base45.decodificar(""))
    }

    @Test
    fun `rechaza un grupo que no cabe en dos bytes`() {
        // GGW es 65536 y ZZZ, 91124: el RFC exige rechazarlos.
        assertFailsWith<IllegalArgumentException> { Base45.decodificar("GGW") }
        assertFailsWith<IllegalArgumentException> { Base45.decodificar("ZZZ") }
    }

    @Test
    fun `rechaza un par que no cabe en un byte`() {
        assertFailsWith<IllegalArgumentException> { Base45.decodificar("ZZ") }
    }

    @Test
    fun `rechaza caracteres fuera del alfabeto y largos imposibles`() {
        assertFailsWith<IllegalArgumentException> { Base45.decodificar("bb8") }
        assertFailsWith<IllegalArgumentException> { Base45.decodificar("BBÑ") }
        assertFailsWith<IllegalArgumentException> { Base45.decodificar("BB8A") }
    }
}
