package cl.midosis.motor.planificacion

import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Una toma concreta del calendario de un tratamiento: qué producto, cuánto y cuándo.
 * El momento es la hora de pared del teléfono; el cambio de zona horaria lo resuelve
 * la reprogramación (HU-17), no este modelo.
 */
data class TomaProgramada(
    val tratamiento: String,
    val producto: String,
    val dosis: String,
    val momento: LocalDateTime,
) {
    init {
        require(tratamiento.isNotBlank()) { "la toma debe indicar su tratamiento" }
        require(producto.isNotBlank()) { "la toma debe indicar su producto" }
        require(dosis.isNotBlank()) { "la toma debe indicar su dosis" }
    }
}

/**
 * Lo que el teléfono notifica: un solo aviso, en un momento, con todas las tomas que
 * corresponden a él detalladas una por una (HU-18).
 */
data class AvisoDeToma(
    val momento: LocalDateTime,
    val tomas: List<TomaProgramada>,
) {
    init {
        require(tomas.isNotEmpty()) { "un aviso sin tomas no tiene nada que notificar" }
    }

    /** Si el aviso reúne tomas de más de un tratamiento. */
    val esAgrupado: Boolean get() = tomas.map { it.tratamiento }.distinct().size > 1
}

/**
 * Agrupa las tomas que coinciden en horario en un solo aviso, para que el paciente no
 * acumule alarmas que terminan ignorándose (HU-18, RF-M-16).
 *
 * Coincidir es caer en el mismo minuto. Los segundos no cuentan: nadie distingue una
 * alarma de las 8:00:00 de otra de las 8:00:30.
 */
object AgrupadorDeTomas {

    /**
     * Ventana de tolerancia para considerar coincidentes dos tomas de minutos
     * distintos. Es cero: solo se agrupa el mismo minuto.
     *
     * Una ventana mayor reduce avisos, pero **adelanta la toma posterior** hasta la hora
     * de la primera del grupo. Eso es una regla clínica: no se cambia sin el visto bueno
     * del químico farmacéutico asesor.
     */
    val VENTANA_AGRUPACION: Duration = Duration.ZERO

    /** Más allá de esto ya no es agrupar dos tomas, es moverlas. */
    val VENTANA_MAXIMA: Duration = Duration.ofMinutes(60)

    /**
     * Devuelve los avisos ordenados en el tiempo. Cada aviso toma la hora de su primera
     * toma, y dentro de él las tomas conservan su orden cronológico.
     *
     * Con una ventana mayor que cero, un grupo se abre con la toma más temprana y reúne
     * las que caen hasta esa hora más la ventana, medida siempre desde la primera: los
     * grupos no se encadenan, así ninguna toma se adelanta más que la ventana.
     */
    fun agrupar(
        tomas: List<TomaProgramada>,
        ventana: Duration = VENTANA_AGRUPACION,
    ): List<AvisoDeToma> {
        require(!ventana.isNegative) { "la ventana de agrupación no puede ser negativa: $ventana" }
        require(ventana <= VENTANA_MAXIMA) {
            "una ventana de $ventana ya no agrupa tomas, las mueve; el máximo es $VENTANA_MAXIMA"
        }

        val ordenadas = tomas
            .map { it.copy(momento = it.momento.truncatedTo(ChronoUnit.MINUTES)) }
            .sortedWith(compareBy({ it.momento }, { it.tratamiento }))

        val avisos = mutableListOf<AvisoDeToma>()
        var grupo = mutableListOf<TomaProgramada>()
        for (toma in ordenadas) {
            val inicio = grupo.firstOrNull()?.momento
            if (inicio == null || !toma.momento.isAfter(inicio.plus(ventana))) {
                grupo.add(toma)
            } else {
                avisos.add(AvisoDeToma(inicio, grupo))
                grupo = mutableListOf(toma)
            }
        }
        grupo.firstOrNull()?.let { avisos.add(AvisoDeToma(it.momento, grupo)) }
        return avisos
    }
}
