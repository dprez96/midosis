package cl.midosis.paciente.cripto

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifrado autenticado AES-GCM para los datos del tratamiento guardados en el teléfono
 * (ADR-008). El resultado lleva adelante el vector de inicialización, que es aleatorio en
 * cada cifrado. Si alguien altera un solo byte, descifrar falla en vez de devolver datos
 * corruptos.
 *
 * El vector lo genera el propio cifrador, nunca la aplicación: el almacén de claves de
 * Android lo exige ("Caller-provided IV not permitted") para que ningún error de
 * programación pueda repetir un vector con la misma clave, que en GCM es catastrófico.
 *
 * La clave se recibe desde afuera: en el teléfono viene del almacén de claves de Android;
 * en las pruebas, de un generador en memoria.
 */
class Cifrador(private val clave: () -> SecretKey) {

    fun cifrar(datos: ByteArray): ByteArray {
        val cifra = Cipher.getInstance(TRANSFORMACION)
        cifra.init(Cipher.ENCRYPT_MODE, clave())
        val iv = cifra.iv
        check(iv != null && iv.size == LARGO_IV) { "el cifrador no generó un vector de ${LARGO_IV} bytes" }
        return iv + cifra.doFinal(datos)
    }

    fun descifrar(cifrado: ByteArray): ByteArray {
        require(cifrado.size > LARGO_IV) { "los datos cifrados están incompletos" }
        val cifra = Cipher.getInstance(TRANSFORMACION)
        val iv = cifrado.copyOfRange(0, LARGO_IV)
        cifra.init(Cipher.DECRYPT_MODE, clave(), GCMParameterSpec(BITS_ETIQUETA, iv))
        return cifra.doFinal(cifrado, LARGO_IV, cifrado.size - LARGO_IV)
    }

    private companion object {
        const val TRANSFORMACION = "AES/GCM/NoPadding"
        const val LARGO_IV = 12
        const val BITS_ETIQUETA = 128
    }
}
