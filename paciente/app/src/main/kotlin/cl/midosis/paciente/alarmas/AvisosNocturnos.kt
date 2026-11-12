package cl.midosis.paciente.alarmas

import cl.midosis.motor.rutina.Rutina
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Un aviso antes de acostarse: cuándo mostrarlo y a qué horas sonarán las alarmas esa noche. */
data class AvisoNocturno(val momento: LocalDateTime, val horas: List<LocalTime>)

/**
 * Los avisos de alarmas nocturnas. No se le pregunta al paciente si acepta que una alarma lo
 * despierte: si el tratamiento la necesita, suena igual (RN-15). A cambio, la noche en que va a
 * sonar alguna, la aplicación lo avisa [ANTICIPACION] antes de la hora de acostarse que declaró,
 * para que deje el teléfono cerca y con volumen.
 *
 * Una alarma es nocturna si cae después de la hora de acostarse y antes de la de despertar. Las
 * de una misma noche van en un solo aviso. Si la hora del aviso ya pasó, no se muestra.
 */
object AvisosNocturnos {

    val ANTICIPACION: Duration = Duration.ofMinutes(30)

    fun calcular(alarmas: List<SolicitudDeAlarma>, rutina: Rutina, ahora: LocalDateTime): List<AvisoNocturno> =
        alarmas
            .map { it.momento }
            .filter { rutina.duerme(it.toLocalTime()) }
            .mapNotNull { momento ->
                val acostarse = acostarseAntesDe(momento, rutina.acostarse)
                // Una toma justo a la hora de acostarse no despierta a nadie.
                if (momento.isAfter(acostarse)) acostarse to momento else null
            }
            .groupBy({ it.first }, { it.second })
            // En el orden de la noche: las 23:30 van antes de las 03:00.
            .map { (acostarse, momentos) -> AvisoNocturno(acostarse.minus(ANTICIPACION), momentos.sorted().map { it.toLocalTime() }.distinct()) }
            .filter { it.momento.isAfter(ahora) }
            .sortedBy { it.momento }

    /**
     * «Tienes una toma a las 03:00.» o «Tienes tomas a las 23:30 y a las 03:00.». Sin el
     * medicamento: el aviso se ve con el teléfono bloqueado.
     */
    fun frase(horas: List<LocalTime>): String {
        val partes = horas.map { "a las ${it.format(HORA)}" }
        return if (partes.size <= 1) {
            "Tienes una toma ${partes.joinToString()}."
        } else {
            "Tienes tomas ${partes.dropLast(1).joinToString(", ")} y ${partes.last()}."
        }
    }

    private val HORA = DateTimeFormatter.ofPattern("HH:mm")

    /** La hora de acostarse de la noche en que cae [momento]: ese mismo día o el anterior. */
    private fun acostarseAntesDe(momento: LocalDateTime, acostarse: LocalTime): LocalDateTime {
        val mismoDia = momento.toLocalDate().atTime(acostarse)
        return if (mismoDia.isAfter(momento)) mismoDia.minusDays(1) else mismoDia
    }
}
