package cl.midosis.motor.planificacion

import cl.midosis.motor.posologia.Frecuencia
import cl.midosis.motor.rutina.Rutina
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Lo que la posología exige sobre el momento de la toma («o» del código). */
enum class Restriccion { EN_AYUNAS, CON_ALIMENTOS, AL_ACOSTARSE }

/**
 * Traduce la posología y la rutina del paciente en las horas concretas de cada toma (HU-08,
 * RF-M-06), con las reglas de la sección de planificación del informe:
 *
 *  - La primera toma se propone en el siguiente cuarto de hora después de cargar el código.
 *  - Con un intervalo de 8 horas o menos las tomas se reparten en las 24 horas desde la
 *    primera, aunque alguna caiga de noche (RN-15).
 *  - Con un intervalo mayor se corre la fase para que las tomas caigan mientras el paciente
 *    está despierto, y las restricciones de administración anclan la hora: en ayunas al
 *    despertar, con alimentos en una comida, al acostarse a esa hora.
 *  - El intervalo entre tomas nunca se acorta (RN-14): todas quedan a exactamente un intervalo
 *    de la anterior. Correr la fase solo atrasa el inicio.
 *  - Las horas se redondean a múltiplos de cinco minutos.
 *
 * Son reglas clínicas: no se cambian sin el visto bueno del químico farmacéutico asesor.
 */
class PlanificadorDeTomas(private val rutina: Rutina) {

    fun planificar(
        frecuencia: Frecuencia,
        dosis: Int,
        restricciones: Set<Restriccion>,
        desde: LocalDateTime,
    ): List<LocalDateTime> {
        require(dosis > 0) { "debe haber al menos una toma" }
        val inicio = siguienteCuartoDeHora(desde)
        val primera = if (frecuencia.intervalo <= INTERVALO_NOCTURNO) inicio else conFase(frecuencia.intervalo, restricciones, inicio)
        return (0 until dosis).map { primera.plus(frecuencia.intervalo.multipliedBy(it.toLong())) }
    }

    /**
     * La primera toma desde [inicio] cuyas tomas del primer día caen en la vigilia y respetan
     * la restricción. Si en dos días no hay ninguna, se parte en [inicio]: es preferible tomar
     * el medicamento que esperar más.
     */
    private fun conFase(intervalo: Duration, restricciones: Set<Restriccion>, inicio: LocalDateTime): LocalDateTime {
        val anclas = anclas(restricciones)
        var candidata = inicio
        val limite = inicio.plusDays(2)
        while (candidata.isBefore(limite)) {
            val hora = candidata.toLocalTime()
            val respetaAncla = anclas == null || anclas.any { it == hora }
            if (respetaAncla && (Restriccion.AL_ACOSTARSE in restricciones || primerDiaDespierto(candidata, intervalo))) {
                return candidata
            }
            candidata = candidata.plusMinutes(PASO_MIN)
        }
        return inicio
    }

    /** Las horas permitidas por las restricciones, o null si cualquiera sirve. */
    private fun anclas(restricciones: Set<Restriccion>): List<LocalTime>? {
        val horas = when {
            Restriccion.AL_ACOSTARSE in restricciones -> listOf(rutina.acostarse)
            Restriccion.EN_AYUNAS in restricciones -> listOf(rutina.despertar)
            Restriccion.CON_ALIMENTOS in restricciones -> rutina.comidas
            else -> return null
        }
        return horas.map(::redondear)
    }

    private fun primerDiaDespierto(primera: LocalDateTime, intervalo: Duration): Boolean {
        var toma = primera
        val finDelDia = primera.plusDays(1)
        while (toma.isBefore(finDelDia)) {
            if (rutina.duerme(toma.toLocalTime())) return false
            toma = toma.plus(intervalo)
        }
        return true
    }

    companion object {
        /** Hasta aquí se admiten tomas nocturnas (RN-15). */
        val INTERVALO_NOCTURNO: Duration = Duration.ofHours(8)

        private const val PASO_MIN = 5L

        fun siguienteCuartoDeHora(momento: LocalDateTime): LocalDateTime {
            val base = momento.truncatedTo(ChronoUnit.HOURS)
            val minutos = momento.minute + if (momento.second > 0 || momento.nano > 0) 1 else 0
            return base.plusMinutes(((minutos + 14) / 15) * 15L)
        }

        private fun redondear(hora: LocalTime): LocalTime {
            val minutos = (hora.toSecondOfDay() / 60 + 2) / 5 * 5
            return LocalTime.MIDNIGHT.plusMinutes(minutos.toLong())
        }
    }
}
