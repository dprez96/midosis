package cl.midosis.paciente.alarmas

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cl.midosis.paciente.R
import cl.midosis.paciente.ui.PantallaPrincipal
import java.time.Instant

/**
 * Recibe la alarma del sistema y muestra la notificación de la toma, con todos los
 * medicamentos del aviso detallados (HU-18) y los botones para contestar (HU-16). También
 * registra el disparo para el banco de dispositivos.
 *
 * Si el paciente no contesta, el recordatorio se repite a los 10 y a los 25 minutos, y después
 * deja de insistir. Cada repetición revisa antes el historial: si ya contestó, no suena.
 */
class ReceptorDeAlarma : BroadcastReceiver() {

    override fun onReceive(contexto: Context, intencion: Intent) {
        val disparada = System.currentTimeMillis()
        val id = intencion.getIntExtra(EXTRA_ID, 0)
        val programada = intencion.getLongExtra(EXTRA_PROGRAMADA, disparada)
        val lineas = intencion.getStringArrayExtra(EXTRA_LINEAS)?.toList().orEmpty()
        val intento = intencion.getIntExtra(EXTRA_INTENTO, 0)
        val programador = ProgramadorDeAlarmas(contexto)

        if (intento == 0) {
            RegistroDeDisparos(contexto).anotar(id, programada, disparada)
            notificar(contexto, id, programada, lineas)
            Reiteraciones.momentos(Instant.ofEpochMilli(programada)).forEachIndexed { i, momento ->
                programador.programarRecordatorio(id, lineas, programada, momento.toEpochMilli(), i + 1)
            }
            // Cada alarma que suena hace avanzar la ventana de las que se entregan al sistema.
            programador.reprogramarGuardadas()
        } else if (!HistorialDeTomas(contexto).cerrado(id, Instant.ofEpochSecond(programada / 1000))) {
            notificar(contexto, id, programada, lineas)
        }
    }

    private fun notificar(contexto: Context, id: Int, programada: Long, lineas: List<String>) {
        val permitido = ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!permitido) return

        val titulo = contexto.getString(
            if (lineas.size > 1) R.string.notificacion_titulo_varios else R.string.notificacion_titulo,
        )
        val estilo = NotificationCompat.InboxStyle().setBigContentTitle(titulo)
        lineas.forEach { estilo.addLine(it) }

        val notificacion = NotificationCompat.Builder(contexto, CanalDeTomas.ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(titulo)
            .setContentText(lineas.joinToString(", "))
            .setStyle(estilo)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setVibrate(CanalDeTomas.VIBRACION)
            .setDefaults(Notification.DEFAULT_LIGHTS)
            .setAutoCancel(true)
            .addAction(0, contexto.getString(R.string.respuesta_tomada), respuesta(contexto, id, programada, lineas, Respuesta.TOMADA))
            .addAction(0, contexto.getString(R.string.respuesta_pospuesta), respuesta(contexto, id, programada, lineas, Respuesta.POSPUESTA))
            .addAction(0, contexto.getString(R.string.respuesta_omitida), respuesta(contexto, id, programada, lineas, Respuesta.OMITIDA))
            .setContentIntent(
                PendingIntent.getActivity(
                    contexto,
                    id,
                    Intent(contexto, PantallaPrincipal::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

        try {
            NotificationManagerCompat.from(contexto).notify(id, notificacion)
        } catch (e: SecurityException) {
            // El paciente retiró el permiso de notificaciones justo ahora.
        }
    }

    private fun respuesta(contexto: Context, id: Int, programada: Long, lineas: List<String>, r: Respuesta): PendingIntent =
        PendingIntent.getBroadcast(
            contexto,
            id,
            Intent(contexto, ReceptorDeRespuesta::class.java)
                .setAction(ReceptorDeRespuesta.ACCION + r.codigo)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_PROGRAMADA, programada)
                .putExtra(EXTRA_LINEAS, lineas.toTypedArray()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        /** 0 es el aviso programado; 1 y 2, las reiteraciones; [POSPUESTO], el que pidió el paciente. */
        const val EXTRA_INTENTO = "cl.midosis.paciente.INTENTO"
        const val POSPUESTO = 3
        const val EXTRA_ID = "cl.midosis.paciente.ID"
        const val EXTRA_PROGRAMADA = "cl.midosis.paciente.PROGRAMADA"
        const val EXTRA_LINEAS = "cl.midosis.paciente.LINEAS"
    }
}
