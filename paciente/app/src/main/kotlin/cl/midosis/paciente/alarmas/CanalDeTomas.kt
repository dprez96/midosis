package cl.midosis.paciente.alarmas

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import cl.midosis.paciente.R

/**
 * Canal de notificación de las tomas: importancia alta, sonido de alarma y vibración.
 *
 * El sonido usa el atributo de alarma y no el de notificación. Así respeta el volumen de
 * alarmas del teléfono, que la gente mayor suele dejar alto, y no el de notificaciones,
 * que suele estar bajo o en silencio.
 */
object CanalDeTomas {

    const val ID = "tomas"

    /** Larga y reconocible: tres pulsos. */
    val VIBRACION = longArrayOf(0, 800, 400, 800, 400, 800)

    fun crear(contexto: Context) {
        val sonido = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val canal = NotificationChannel(
            ID,
            contexto.getString(R.string.canal_tomas),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = contexto.getString(R.string.canal_tomas_descripcion)
            setSound(
                sonido,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = VIBRACION
            setBypassDnd(false)
        }
        contexto.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }
}
