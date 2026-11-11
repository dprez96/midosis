package cl.midosis.paciente.privacidad

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import cl.midosis.paciente.alarmas.ProgramadorDeAlarmas
import cl.midosis.paciente.cripto.ClaveDelDispositivo

/**
 * El derecho de supresión (HU-25, RF-M-18, RN-17): borra todo lo que MiDosis guarda en el
 * teléfono, sin pedir justificación ni conexión.
 *
 * Es solo local, por decisión del 2026-10-27: el servidor no guarda datos del paciente que
 * borrar (ADR-002) y la aplicación usa internet solo para el canje (ADR-017).
 *
 * Borra carpetas enteras y no archivo por archivo, para que lo que se guarde en el futuro
 * también quede cubierto. Al final destruye la clave del almacén de Android: si algo
 * cifrado sobreviviera en otra parte, ya no se podría leer.
 */
object Supresion {

    fun ejecutar(contexto: Context) {
        // Primero las alarmas, que se leen del plan guardado; después se borra todo.
        ProgramadorDeAlarmas(contexto).cancelarTodas()
        NotificationManagerCompat.from(contexto).cancelAll()
        listOf(contexto.noBackupFilesDir, contexto.filesDir, contexto.cacheDir).forEach { carpeta ->
            carpeta.listFiles()?.forEach { it.deleteRecursively() }
        }
        ClaveDelDispositivo.borrar()
    }
}
