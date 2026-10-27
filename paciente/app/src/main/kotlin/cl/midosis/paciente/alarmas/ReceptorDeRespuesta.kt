package cl.midosis.paciente.alarmas

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Recibe lo que el paciente toca en la notificación: «Tomé», «Posponer» u «Omitir» (HU-16).
 * Anota la respuesta con la hora real en que se dio, cancela las reiteraciones pendientes y
 * cierra la notificación. «Posponer» vuelve a mostrar el aviso en 10 minutos.
 */
class ReceptorDeRespuesta : BroadcastReceiver() {

    override fun onReceive(contexto: Context, intencion: Intent) {
        val respuesta = intencion.action?.removePrefix(ACCION)?.let { codigo ->
            Respuesta.entries.firstOrNull { it.codigo == codigo }
        } ?: return
        val id = intencion.getIntExtra(ReceptorDeAlarma.EXTRA_ID, 0)
        val programada = intencion.getLongExtra(ReceptorDeAlarma.EXTRA_PROGRAMADA, 0)
        val lineas = intencion.getStringArrayExtra(ReceptorDeAlarma.EXTRA_LINEAS)?.toList().orEmpty()
        responder(contexto, id, programada, lineas, respuesta)
    }

    companion object {
        const val ACCION = "cl.midosis.paciente.RESPUESTA_"

        /**
         * Anota la respuesta y deja de insistir. La usan los botones de la notificación y los
         * de la pantalla de la toma.
         */
        fun responder(contexto: Context, id: Int, programada: Long, lineas: List<String>, respuesta: Respuesta) {
            val ahora = Instant.now()

            HistorialDeTomas(contexto).anotar(
                RespuestaRegistrada(id, Instant.ofEpochSecond(programada / 1000), respuesta, ahora.truncatedTo(ChronoUnit.SECONDS), lineas),
            )
            val programador = ProgramadorDeAlarmas(contexto)
            programador.cancelarRecordatorios(id)
            NotificationManagerCompat.from(contexto).cancel(id)
            if (respuesta == Respuesta.POSPUESTA) {
                programador.programarRecordatorio(
                    id, lineas, programada, ahora.plus(Reiteraciones.POSPONER).toEpochMilli(), ReceptorDeAlarma.POSPUESTO,
                )
            }
        }
    }
}
