package cl.midosis.paciente.datos

import android.content.Context
import android.util.Log
import cl.midosis.paciente.cripto.Cifrador
import cl.midosis.paciente.cripto.ClaveDelDispositivo
import java.io.File

/**
 * Un archivo del teléfono guardado cifrado con la clave del almacén de Android (ADR-008),
 * en una carpeta que no entra en respaldos. Lo usan los datos que nunca salen del teléfono:
 * la rutina del paciente y sus tratamientos.
 *
 * Si no se puede descifrar, por ejemplo tras restaurar el teléfono sin la clave, el archivo
 * se descarta en vez de dejar la aplicación rota: los datos se vuelven a cargar.
 */
class ArchivoCifrado(
    contexto: Context,
    private val nombre: String,
    private val cifrador: Cifrador = Cifrador(ClaveDelDispositivo::obtener),
) {
    private val archivo = File(contexto.noBackupFilesDir, nombre)

    fun leer(): String? = synchronized(bloqueo(nombre)) {
        if (!archivo.exists()) return null
        return try {
            String(cifrador.descifrar(archivo.readBytes()), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w("MiDosis", "no se pudo descifrar $nombre; se descarta", e)
            archivo.delete()
            null
        }
    }

    fun escribir(texto: String) = synchronized(bloqueo(nombre)) {
        val temporal = File(archivo.parentFile, "$nombre.tmp")
        temporal.writeBytes(cifrador.cifrar(texto.toByteArray(Charsets.UTF_8)))
        // Reemplazo en un solo paso: un corte de energía no deja el archivo a medio escribir.
        if (!temporal.renameTo(archivo)) {
            archivo.delete()
            temporal.renameTo(archivo)
        }
    }

    fun borrar() = synchronized(bloqueo(nombre)) { archivo.delete() }

    private companion object {
        val bloqueos = HashMap<String, Any>()
        fun bloqueo(nombre: String): Any = synchronized(bloqueos) { bloqueos.getOrPut(nombre) { Any() } }
    }
}
