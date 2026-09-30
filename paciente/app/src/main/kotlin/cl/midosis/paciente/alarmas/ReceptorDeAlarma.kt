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

/**
 * Recibe la alarma del sistema y muestra la notificación de la toma, con todos los
 * medicamentos del aviso detallados (HU-18). También registra el disparo para el banco
 * de dispositivos.
 */
class ReceptorDeAlarma : BroadcastReceiver() {

    override fun onReceive(contexto: Context, intencion: Intent) {
        val disparada = System.currentTimeMillis()
        val id = intencion.getIntExtra(EXTRA_ID, 0)
        val programada = intencion.getLongExtra(EXTRA_PROGRAMADA, disparada)
        val lineas = intencion.getStringArrayExtra(EXTRA_LINEAS)?.toList().orEmpty()

        RegistroDeDisparos(contexto).anotar(id, programada, disparada)
        notificar(contexto, id, lineas)
    }

    private fun notificar(contexto: Context, id: Int, lineas: List<String>) {
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

    companion object {
        const val EXTRA_ID = "cl.midosis.paciente.ID"
        const val EXTRA_PROGRAMADA = "cl.midosis.paciente.PROGRAMADA"
        const val EXTRA_LINEAS = "cl.midosis.paciente.LINEAS"
    }
}
