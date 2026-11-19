package cl.midosis.paciente.alarmas

import cl.midosis.motor.planificacion.AgrupadorDeTomas
import cl.midosis.motor.planificacion.TomaProgramada
import java.time.LocalDateTime
import java.time.ZoneId
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
    /** El perfil del paciente (HU-31): tomas de perfiles distintos nunca comparten aviso (RN-23). */
    val perfil: String = "",
)

object PlanDeAlarmas {

    /**
     * Agrupa las tomas coincidentes (HU-18) y descarta las que ya pasaron: una alarma en
     * el pasado sonaría de inmediato y confundiría al paciente.
     */
    fun construir(tomas: List<TomaProgramada>, ahora: LocalDateTime, perfil: String = "", orden: Int = 0): List<SolicitudDeAlarma> =
        AgrupadorDeTomas.agrupar(tomas)
            .filter { it.momento.isAfter(ahora) }
            .map { aviso ->
                SolicitudDeAlarma(
                    id = identificador(aviso.momento, orden),
                    momento = aviso.momento,
                    lineas = aviso.tomas.map { "${it.producto} · ${it.dosis}" },
                    perfil = perfil,
                )
            }

    /**
     * El instante en que suena una toma prevista a [momento], hora local (HU-17). El plan guarda
     * la hora local y no el instante: si el teléfono cambia de zona o entra el horario de verano,
     * al volver a entregar las alarmas cada toma conserva su hora en el reloj. Una hora que el
     * cambio de horario se salta (de 00:00 a 01:00 en Chile) suena una hora después; una que se
     * repite, la primera vez.
     */
    fun instante(momento: LocalDateTime, zona: ZoneId = ZoneId.systemDefault()): Long =
        momento.atZone(zona).toInstant().toEpochMilli()

    /**
     * Minutos desde 1970: cabe en un Int hasta el año 6053 y es único por minuto. El perfil
     * principal ([orden] 0) usa el minuto tal cual, como antes de los perfiles; los demás, un
     * número negativo distinto para cada uno, así dos perfiles a la misma hora tienen dos avisos
     * (RN-23).
     */
    fun identificador(momento: LocalDateTime, orden: Int = 0): Int {
        val minuto = Math.toIntExact(momento.toEpochSecond(ZoneOffset.UTC) / 60)
        return if (orden == 0) minuto else -Math.addExact(Math.multiplyExact(minuto, 4), orden)
    }
}
