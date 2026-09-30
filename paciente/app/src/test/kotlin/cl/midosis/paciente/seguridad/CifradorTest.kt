package cl.midosis.paciente.seguridad

import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class CifradorTest {

    /** En el teléfono la clave viene del almacén de Android; aquí, de un generador en memoria. */
    private val clave = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cifrador = Cifrador { clave }
    private val datos = "Losartán 50 mg · 1 comprimido".toByteArray(Charsets.UTF_8)

    @Test
    fun `lo cifrado se recupera intacto`() {
        assertContentEquals(datos, cifrador.descifrar(cifrador.cifrar(datos)))
    }

    @Test
    fun `lo cifrado no contiene el texto original`() {
        val cifrado = String(cifrador.cifrar(datos), Charsets.ISO_8859_1)
        assertFalse(cifrado.contains("Losart"))
    }

    @Test
    fun `cifrar dos veces lo mismo da resultados distintos`() {
        assertFalse(cifrador.cifrar(datos).contentEquals(cifrador.cifrar(datos)))
    }

    @Test
    fun `un solo byte alterado hace fallar el descifrado`() {
        val cifrado = cifrador.cifrar(datos)
        cifrado[cifrado.size - 1] = (cifrado[cifrado.size - 1] + 1).toByte()
        assertFailsWith<AEADBadTagException> { cifrador.descifrar(cifrado) }
    }

    @Test
    fun `con otra clave no se puede descifrar`() {
        val otra = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertFailsWith<AEADBadTagException> { Cifrador { otra }.descifrar(cifrador.cifrar(datos)) }
    }

    @Test
    fun `datos incompletos se rechazan`() {
        assertFailsWith<IllegalArgumentException> { cifrador.descifrar(ByteArray(5)) }
    }
}
