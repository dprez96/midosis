package cl.midosis.paciente.seguridad

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifrado autenticado AES-GCM para los datos del tratamiento guardados en el teléfono
 * (ADR-008). El resultado lleva adelante el vector de inicialización, que es aleatorio en
 * cada cifrado. Si alguien altera un solo byte, descifrar falla en vez de devolver datos
 * corruptos.
 *
 * La clave se recibe desde afuera: en el teléfono viene del almacén de claves de Android;
 * en las pruebas, de un generador en memoria.
 */
class Cifrador(private val clave: () -> SecretKey) {

    fun cifrar(datos: ByteArray): ByteArray {
        val cifra = Cipher.getInstance(TRANSFORMACION)
        val iv = ByteArray(LARGO_IV).also { SecureRandom().nextBytes(it) }
        cifra.init(Cipher.ENCRYPT_MODE, clave(), GCMParameterSpec(BITS_ETIQUETA, iv))
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
