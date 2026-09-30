package cl.midosis.paciente

import android.app.Application
import cl.midosis.paciente.alarmas.CanalDeTomas

class AplicacionMiDosis : Application() {
    override fun onCreate() {
        super.onCreate()
        // El canal debe existir antes de la primera alarma, incluso si la aplicación no
        // se ha abierto desde que se reinició el teléfono.
        CanalDeTomas.crear(this)
    }
}
