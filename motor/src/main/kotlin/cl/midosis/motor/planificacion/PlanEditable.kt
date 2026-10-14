package cl.midosis.motor.planificacion

import java.time.Duration
import java.time.LocalDateTime

/**
 * El calendario propuesto de un medicamento, que el paciente revisa y ajusta antes de
 * confirmarlo (HU-14, RF-M-10, CU-P-03).
 *
 * Toda edición respeta el intervalo de la posología (RN-14): dos tomas seguidas nunca quedan
 * más cerca que eso. Por eso hay dos formas de mover:
 *
 *  - [moverDesde] corre una toma y todas las siguientes: las distancias no cambian y siempre se
 *    acepta, salvo que la primera quede en el pasado. Es lo que el paciente suele querer: «tomo
 *    la de la noche a las 21:00 y no a las 20:00».
 *  - [moverSola] corre solo esa toma. Acercarla a una vecina más que el intervalo se rechaza, con
 *    la explicación de cuál es el intervalo exigido.
 */
class PlanEditable(
    tomas: List<LocalDateTime>,
    val intervalo: Duration,
) {
    val tomas: List<LocalDateTime> = tomas.sorted()

    init {
        require(this.tomas.isNotEmpty()) { "un plan sin tomas no tiene nada que editar" }
        require(this.tomas.zipWithNext().all { (a, b) -> Duration.between(a, b) >= intervalo }) {
            "el plan propuesto ya viola el intervalo de $intervalo"
        }
    }

    sealed interface Resultado {
        data class Aceptado(val plan: PlanEditable) : Resultado

        /** El cambio se impide; [motivo] lo explica en palabras para el paciente. */
        data class Rechazado(val motivo: String) : Resultado
    }

    fun moverDesde(indice: Int, minutos: Long, ahora: LocalDateTime): Resultado {
        require(indice in tomas.indices) { "no existe la toma $indice" }
        val movidas = tomas.mapIndexed { i, t -> if (i >= indice) t.plusMinutes(minutos) else t }
        return validar(movidas, ahora)
    }

    fun moverSola(indice: Int, minutos: Long, ahora: LocalDateTime): Resultado {
        require(indice in tomas.indices) { "no existe la toma $indice" }
        val movidas = tomas.mapIndexed { i, t -> if (i == indice) t.plusMinutes(minutos) else t }
        return validar(movidas, ahora)
    }

    private fun validar(propuestas: List<LocalDateTime>, ahora: LocalDateTime): Resultado {
        if (propuestas.first().isBefore(ahora)) {
            return Resultado.Rechazado("La primera toma no puede quedar antes de ahora.")
        }
        if (propuestas.zipWithNext().any { (a, b) -> Duration.between(a, b) < intervalo }) {
            return Resultado.Rechazado(
                "Tu medicamento es ${describir(intervalo)}: dos tomas no pueden quedar a menos de ${horas(intervalo)}. " +
                    "Para cambiar la hora, mueve también las tomas siguientes.",
            )
        }
        return Resultado.Aceptado(PlanEditable(propuestas, intervalo))
    }

    private companion object {
        fun describir(intervalo: Duration): String = when {
            intervalo.toMinutes() == 24L * 60 -> "una vez al día"
            intervalo.toMinutes() % 60 == 0L -> "cada ${intervalo.toHours()} horas"
            else -> "cada ${intervalo.toMinutes()} minutos"
        }

        fun horas(intervalo: Duration): String =
            if (intervalo.toMinutes() % 60 == 0L) "${intervalo.toHours()} horas" else "${intervalo.toMinutes()} minutos"
    }
}
