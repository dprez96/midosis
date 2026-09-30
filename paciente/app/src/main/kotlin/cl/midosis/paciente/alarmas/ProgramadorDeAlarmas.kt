package cl.midosis.paciente.alarmas

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import cl.midosis.paciente.ui.PantallaPrincipal
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Entrega las alarmas al sistema operativo con AlarmManager.setAlarmClock.
 *
 * Se usa setAlarmClock y no setExactAndAllowWhileIdle (ADR-015): en el modo de ahorro
 * profundo Android limita la frecuencia de las segundas y puede retrasar varios minutos una
 * toma cercana a otra, lo que rompe el margen de 5 minutos del criterio de la HU-15. Las de
 * setAlarmClock son las de un despertador: el sistema sale del ahorro para cumplirlas.
 */
class ProgramadorDeAlarmas(
    private val contexto: Context,
    private val almacen: AlmacenDelPlan = AlmacenDelPlan(contexto),
) {

    private val alarmas = contexto.getSystemService(AlarmManager::class.java)

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
    fun programar(plan: List<SolicitudDeAlarma>, ahora: LocalDateTime = LocalDateTime.now()): Int {
        val programadas = entregar(plan)
        // Se guarda lo que de verdad quedó programado, para reprogramarlo tras un reinicio.
        almacen.agregar(programadas, ahora)
        return programadas.size
    }

    /**
     * Vuelve a entregar al sistema las alarmas guardadas que todavía no pasan (HU-17).
     * Se llama al encender el teléfono y al actualizar la aplicación, porque en ambos casos
     * Android borra las alarmas. Devuelve cuántas se reprogramaron.
     */
    fun reprogramarGuardadas(ahora: LocalDateTime = LocalDateTime.now()): Int =
        entregar(almacen.leer().filter { it.momento.isAfter(ahora) }).size

    /** Cancela todas las alarmas que esta aplicación dejó programadas. */
    fun cancelarTodas() {
        for (solicitud in almacen.leer()) {
            alarmas.cancel(intencionDeAlarma(solicitud.id, emptyList(), 0))
        }
        almacen.vaciar()
    }

    fun cantidadVigentes(ahora: LocalDateTime = LocalDateTime.now()): Int =
        almacen.leer().count { it.momento.isAfter(ahora) }

    /** Entrega las alarmas al sistema y devuelve las que quedaron programadas. */
    private fun entregar(plan: List<SolicitudDeAlarma>): List<SolicitudDeAlarma> {
        if (!puedeProgramarExactas()) return emptyList()
        val entregadas = mutableListOf<SolicitudDeAlarma>()
        for (solicitud in plan) {
            val cuando = solicitud.momento.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val info = AlarmManager.AlarmClockInfo(cuando, intencionDePantalla())
            try {
                alarmas.setAlarmClock(info, intencionDeAlarma(solicitud.id, solicitud.lineas, cuando))
                entregadas.add(solicitud)
            } catch (e: SecurityException) {
                // El paciente revocó el permiso entre la verificación y la programación.
                return entregadas
            }
        }
        return entregadas
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

}
