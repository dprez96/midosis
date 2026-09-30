package cl.midosis.paciente.alarmas

import cl.midosis.motor.planificacion.AgrupadorDeTomas
import cl.midosis.motor.planificacion.TomaProgramada
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Una alarma lista para entregarle al sistema operativo: cuándo sonar y qué mostrar.
 *
 * El identificador se deriva del minuto de la alarma. Programar dos veces la misma alarma
 * la reemplaza en vez de duplicarla, que es lo que necesita la reprogramación tras un
 * reinicio (HU-17).
 */
data class SolicitudDeAlarma(
    val id: Int,
    val momento: LocalDateTime,
    val lineas: List<String>,
)

object PlanDeAlarmas {

    /**
     * Agrupa las tomas coincidentes (HU-18) y descarta las que ya pasaron: una alarma en
     * el pasado sonaría de inmediato y confundiría al paciente.
     */
    fun construir(tomas: List<TomaProgramada>, ahora: LocalDateTime): List<SolicitudDeAlarma> =
        AgrupadorDeTomas.agrupar(tomas)
            .filter { it.momento.isAfter(ahora) }
            .map { aviso ->
                SolicitudDeAlarma(
                    id = identificador(aviso.momento),
                    momento = aviso.momento,
                    lineas = aviso.tomas.map { "${it.producto} · ${it.dosis}" },
                )
            }

    /** Minutos desde 1970: cabe en un Int hasta el año 6053 y es único por minuto. */
    fun identificador(momento: LocalDateTime): Int =
        Math.toIntExact(momento.toEpochSecond(ZoneOffset.UTC) / 60)
}
