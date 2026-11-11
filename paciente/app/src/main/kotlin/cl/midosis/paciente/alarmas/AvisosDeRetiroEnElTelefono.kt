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
import cl.midosis.paciente.tratamiento.AlmacenDeTratamientos
import cl.midosis.paciente.tratamiento.AvisosDeRetiro
import cl.midosis.paciente.ui.PantallaPrincipal
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Programa en el teléfono los avisos de próximo retiro (HU-20). Se recalculan enteros desde los
 * tratamientos guardados cada vez que algo cambia (un tratamiento nuevo, el encendido del
 * teléfono), así que nunca quedan desfasados.
 *
 * Son pocos y no necesitan la precisión de una toma: se programan con setAndAllowWhileIdle,
 * que puede retrasarse unos minutos en el ahorro profundo.
 */
object AvisosDeRetiroEnElTelefono {

    const val CANAL = "retiros"
    private const val ACCION = "cl.midosis.paciente.RETIRO"

    /** Solo los identificadores de los avisos programados, para cancelarlos; sin datos de salud. */
    private const val ARCHIVO = "avisos-de-retiro.txt"

    internal const val EXTRA_TEXTO = "cl.midosis.paciente.TEXTO_RETIRO"
    internal const val EXTRA_ID = "cl.midosis.paciente.ID_RETIRO"
    private val FECHA = DateTimeFormatter.ofPattern("dd-MM")

    fun crearCanal(contexto: Context) {
        val canal = NotificationChannel(
            CANAL,
            contexto.getString(R.string.canal_retiros),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = contexto.getString(R.string.canal_retiros_descripcion) }
        contexto.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    fun programar(contexto: Context, ahora: LocalDateTime = LocalDateTime.now()) {
        val rutina = AlmacenDeRutina(contexto).leer()?.rutina ?: return
        val avisos = AvisosDeRetiro.calcular(AlmacenDeTratamientos(contexto).leer(), rutina, ahora)
        val alarmas = contexto.getSystemService(AlarmManager::class.java)
        cancelar(contexto)
        avisos.forEach { aviso ->
            val id = identificador(aviso.momento)
            val texto = aviso.productos.joinToString("\n") { "${it.etiqueta}: se acaba el ${it.seAgotaEl.format(FECHA)}" }
            val cuando = aviso.momento.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            alarmas.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, intencion(contexto, id, texto))
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
            Intent(contexto, ReceptorDeRetiro::class.java)
                .setAction(ACCION)
                .putExtra(EXTRA_TEXTO, texto)
                .putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun archivo(contexto: Context) = File(contexto.noBackupFilesDir, ARCHIVO)

    private fun identificador(momento: LocalDateTime): Int = Math.toIntExact(momento.toEpochSecond(ZoneOffset.UTC) / 60)
}

/** Muestra el aviso de próximo retiro. No dice «terminado»: el tratamiento sigue (RN-07). */
class ReceptorDeRetiro : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intencion: Intent) {
        val texto = intencion.getStringExtra(AvisosDeRetiroEnElTelefono.EXTRA_TEXTO).orEmpty()
        val id = intencion.getIntExtra(AvisosDeRetiroEnElTelefono.EXTRA_ID, 0)
        val cuerpo = texto + "\n" + contexto.getString(R.string.retiro_cuerpo)
        val notificacion = NotificationCompat.Builder(contexto, AvisosDeRetiroEnElTelefono.CANAL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(contexto.getString(R.string.retiro_titulo))
            .setContentText(texto.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
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
            // Sin permiso de notificaciones: la pantalla principal igual dice «vuelve a la farmacia».
        }
    }
}
