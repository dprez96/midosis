package cl.midosis.paciente.alarmas

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import cl.midosis.paciente.ui.PantallaPrincipal
import java.time.ZoneId

/**
 * Entrega las alarmas al sistema operativo con AlarmManager.setAlarmClock.
 *
 * Se usa setAlarmClock y no setExactAndAllowWhileIdle (ADR-015): en el modo de ahorro
 * profundo Android limita la frecuencia de las segundas y puede retrasar varios minutos una
 * toma cercana a otra, lo que rompe el margen de 5 minutos del criterio de la HU-15. Las de
 * setAlarmClock son las de un despertador: el sistema sale del ahorro para cumplirlas.
 */
class ProgramadorDeAlarmas(private val contexto: Context) {

    private val alarmas = contexto.getSystemService(AlarmManager::class.java)

    /** Identificadores de las alarmas vigentes, para poder cancelarlas después. */
    private val vigentes = contexto.getSharedPreferences("alarmas", Context.MODE_PRIVATE)

    /**
     * Desde Android 12 hace falta permiso para alarmas exactas, y desde Android 14 lo
     * concede el paciente en Ajustes. Antes de Android 12 no existe ese permiso.
     */
    fun puedeProgramarExactas(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmas.canScheduleExactAlarms()

    /**
     * Programa las alarmas del plan. Devuelve cuántas quedaron programadas: cero si falta
     * el permiso, para que quien llama lo muestre en vez de fallar en silencio (HU-24).
     */
    fun programar(plan: List<SolicitudDeAlarma>): Int {
        if (!puedeProgramarExactas()) return 0
        var programadas = 0
        for (solicitud in plan) {
            val cuando = solicitud.momento.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val info = AlarmManager.AlarmClockInfo(cuando, intencionDePantalla())
            try {
                alarmas.setAlarmClock(info, intencionDeAlarma(solicitud.id, solicitud.lineas, cuando))
                recordar(solicitud.id)
                programadas++
            } catch (e: SecurityException) {
                // El paciente revocó el permiso entre la verificación y la programación.
                return programadas
            }
        }
        return programadas
    }

    /** Cancela todas las alarmas que esta aplicación dejó programadas. */
    fun cancelarTodas() {
        for (id in ids()) {
            alarmas.cancel(intencionDeAlarma(id, emptyList(), 0))
        }
        vigentes.edit().remove(CLAVE).apply()
    }

    fun cantidadVigentes(): Int = ids().size

    private fun ids(): Set<Int> =
        vigentes.getStringSet(CLAVE, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    private fun recordar(id: Int) {
        vigentes.edit().putStringSet(CLAVE, ids().map { it.toString() }.toSet() + id.toString()).apply()
    }

    /** La próxima alarma de esta aplicación que el sistema tiene registrada, si hay. */
    fun proxima(): Long? = alarmas.nextAlarmClock
        ?.takeIf { it.showIntent?.creatorPackage == contexto.packageName }
        ?.triggerTime

    /**
     * Android identifica la alarma por el código y el destino, no por los datos extra: la
     * misma alarma se reemplaza o se cancela con cualquier contenido.
     */
    private fun intencionDeAlarma(id: Int, lineas: List<String>, cuando: Long): PendingIntent =
        PendingIntent.getBroadcast(
            contexto,
            id,
            Intent(contexto, ReceptorDeAlarma::class.java)
                .putExtra(ReceptorDeAlarma.EXTRA_ID, id)
                .putExtra(ReceptorDeAlarma.EXTRA_PROGRAMADA, cuando)
                .putExtra(ReceptorDeAlarma.EXTRA_LINEAS, lineas.toTypedArray()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun intencionDePantalla(): PendingIntent =
        PendingIntent.getActivity(
            contexto,
            0,
            Intent(contexto, PantallaPrincipal::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val CLAVE = "ids"
    }
}
