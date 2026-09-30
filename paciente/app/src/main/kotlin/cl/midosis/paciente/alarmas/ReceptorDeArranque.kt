package cl.midosis.paciente.alarmas

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Reprograma las alarmas guardadas cuando el teléfono termina de encender y cuando se
 * actualiza la aplicación (HU-17, primer criterio): en ambos casos Android borra todas las
 * alarmas, y el paciente se quedaría sin recordatorios sin darse cuenta.
 *
 * El aviso de encendido llega después de que el usuario desbloquea el teléfono por primera
 * vez: recién ahí están disponibles los archivos de la aplicación y su clave de cifrado.
 */
class ReceptorDeArranque : BroadcastReceiver() {

    override fun onReceive(contexto: Context, intencion: Intent) {
        if (intencion.action !in ACCIONES) return
        val reprogramadas = ProgramadorDeAlarmas(contexto).reprogramarGuardadas()
        Log.i("MiDosis", "alarmas reprogramadas tras ${intencion.action}: $reprogramadas")
    }

    private companion object {
        val ACCIONES = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
