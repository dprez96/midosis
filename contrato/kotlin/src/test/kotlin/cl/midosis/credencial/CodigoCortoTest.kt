package cl.midosis.credencial

import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CodigoCortoTest {

    private val alfabeto = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private val azar = SecureRandom.getInstance("SHA1PRNG").apply { setSeed(20261016L) }

    @Test
    fun `se genera con diez caracteres del alfabeto y se vuelve a leer`() {
        repeat(200) {
            val c = CodigoCorto.generar(azar)
            assertEquals(10, c.valor.length)
            assertTrue(c.valor.all { it in alfabeto })
            assertEquals(c, CodigoCorto.de(c.valor))
            assertEquals(c, CodigoCorto.de(c.agrupado))
        }
    }

    @Test
    fun `tolera como lo escribe una persona`() {
        val c = CodigoCorto.generar(azar)
        assertEquals(c, CodigoCorto.de("  ${c.agrupado.lowercase()} "))
        val conConfusiones = c.valor.replace('0', 'O').replace('1', 'I')
        assertEquals(c, CodigoCorto.de(conConfusiones))
    }

    @Test
    fun `detecta cualquier carácter mal escrito`() {
        repeat(100) {
            val c = CodigoCorto.generar(azar).valor
            for (i in c.indices) {
                for (otro in alfabeto) {
                    if (otro == c[i]) continue
                    val malo = c.substring(0, i) + otro + c.substring(i + 1)
                    assertNull(CodigoCorto.de(malo), "no detectó $malo (original $c)")
                }
            }
        }
    }

    @Test
    fun `rechaza largos, letras fuera del alfabeto y la U`() {
        val c = CodigoCorto.generar(azar).valor
        assertNull(CodigoCorto.de(c.dropLast(1)))
        assertNull(CodigoCorto.de(c + "0"))
        assertNull(CodigoCorto.de("UUUUUUUUUU"))
        assertNull(CodigoCorto.de(""))
    }

    @Test
    fun `agrupado en dos bloques de cinco`() {
        val c = assertNotNull(CodigoCorto.de("0000000000"))
        assertEquals("00000-00000", c.agrupado)
    }
}
