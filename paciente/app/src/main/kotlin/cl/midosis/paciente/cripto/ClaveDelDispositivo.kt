package cl.midosis.paciente.cripto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Clave AES de 256 bits guardada en el almacén de claves de Android (ADR-008). La clave
 * nunca sale del almacén: la aplicación le pide cifrar y descifrar, pero no puede leerla,
 * y no viaja en respaldos ni al cambiar de teléfono.
 *
 * No exige autenticación del usuario, porque las alarmas se reprograman al encender el
 * teléfono, antes de que nadie lo toque. Queda protegida por el bloqueo de pantalla del
 * propio teléfono.
 */
object ClaveDelDispositivo {

    private const val ALMACEN = "AndroidKeyStore"
    private const val ALIAS = "midosis-tratamiento"

    fun obtener(): SecretKey {
        val almacen = KeyStore.getInstance(ALMACEN).apply { load(null) }
        (almacen.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ALMACEN)
        generador.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generador.generateKey()
    }
}
