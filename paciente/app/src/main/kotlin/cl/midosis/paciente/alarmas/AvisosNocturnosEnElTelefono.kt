package cl.midosis.paciente.alarmas

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cl.midosis.paciente.R
import cl.midosis.paciente.rutina.AlmacenDeRutina
import cl.midosis.paciente.ui.PantallaPrincipal
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Programa en el teléfono los avisos de alarmas nocturnas ([AvisosNocturnos]). Se recalculan
 * enteros desde el plan guardado cada vez que se programan o reprograman las alarmas, y cubren
 * la misma ventana que ellas ([ProgramadorDeAlarmas.VENTANA]).
 *
 * No necesitan la precisión de una toma: se programan con setAndAllowWhileIdle, como los avisos
 * de próximo retiro.
 */
object AvisosNocturnosEnElTelefono {

    const val CANAL = "nocturnas"
    private const val ACCION = "cl.midosis.paciente.NOCTURNA"

    /** Solo los identificadores de los avisos programados, para cancelarlos; sin datos de salud. */
    private const val ARCHIVO = "avisos-nocturnos.txt"

    internal const val EXTRA_TEXTO = "cl.midosis.paciente.TEXTO_NOCTURNA"
    internal const val EXTRA_ID = "cl.midosis.paciente.ID_NOCTURNA"

    fun crearCanal(contexto: Context) {
        val canal = NotificationChannel(
            CANAL,
            contexto.getString(R.string.canal_nocturnas),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = contexto.getString(R.string.canal_nocturnas_descripcion) }
        contexto.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    fun programar(contexto: Context, plan: List<SolicitudDeAlarma>, ahora: LocalDateTime = LocalDateTime.now()) {
        cancelar(contexto)
        val rutina = AlmacenDeRutina(contexto).leer()?.rutina ?: return
        val avisos = AvisosNocturnos.calcular(ProgramadorDeAlarmas.dentroDeLaVentana(plan, ahora), rutina, ahora)
        val alarmas = contexto.getSystemService(AlarmManager::class.java)
        avisos.forEach { aviso ->
            // El receptor es otro, así que el identificador no choca con el de una toma.
            val id = identificador(aviso.momento)
            val cuando = aviso.momento.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            alarmas.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, intencion(contexto, id, AvisosNocturnos.frase(aviso.horas)))
        }
        archivo(contexto).writeText(avisos.joinToString("\n") { identificador(it.momento).toString() })
    }

    fun cancelar(contexto: Context) {
        val alarmas = contexto.getSystemService(AlarmManager::class.java)
        val archivo = archivo(contexto)
        if (!archivo.exists()) return
        archivo.readLines().mapNotNull { it.toIntOrNull() }.forEach { alarmas.cancel(intencion(contexto, it, "")) }
        archivo.delete()
    }

    private fun intencion(contexto: Context, id: Int, texto: String): PendingIntent =
        PendingIntent.getBroadcast(
            contexto,
            id,
            Intent(contexto, ReceptorDeAvisoNocturno::class.java)
                .setAction(ACCION)
                .putExtra(EXTRA_TEXTO, texto)
                .putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun archivo(contexto: Context) = File(contexto.noBackupFilesDir, ARCHIVO)

    private fun identificador(momento: LocalDateTime): Int = Math.toIntExact(momento.toEpochSecond(ZoneOffset.UTC) / 60)
}

/** Muestra el aviso: esta noche sonará una alarma, y a qué hora. */
class ReceptorDeAvisoNocturno : BroadcastReceiver() {
    private companion object {
        const val ETIQUETA = "nocturna"
    }

    override fun onReceive(contexto: Context, intencion: Intent) {
        val texto = intencion.getStringExtra(AvisosNocturnosEnElTelefono.EXTRA_TEXTO).orEmpty()
        val id = intencion.getIntExtra(AvisosNocturnosEnElTelefono.EXTRA_ID, 0)
        val notificacion = NotificationCompat.Builder(contexto, AvisosNocturnosEnElTelefono.CANAL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(contexto.getString(R.string.nocturna_titulo))
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto + "\n" + contexto.getString(R.string.nocturna_cuerpo)))
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
            // Con etiqueta propia: el número es un minuto, como el de las tomas, y no debe reemplazar
            // la notificación de una toma a esa misma hora.
            NotificationManagerCompat.from(contexto).notify(ETIQUETA, id, notificacion)
        } catch (e: SecurityException) {
            // Sin permiso de notificaciones: la alarma de la noche suena igual.
        }
    }
}
