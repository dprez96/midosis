package cl.midosis.paciente.tratamiento

import cl.midosis.motor.planificacion.PlanEditable
import cl.midosis.motor.rutina.Rutina
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * El paso en que el paciente revisa el calendario propuesto y lo ajusta antes de confirmarlo
 * (HU-14, CU-P-03). La regla es la del motor ([PlanEditable]); aquí solo se decide qué tomas
 * se muestran y se guarda el último aviso de cada medicamento.
 *
 * Se muestran las tomas del primer día de cada medicamento, y mover cualquiera corre todas las
 * de ese medicamento: el paciente piensa en un horario («a las 9 y a las 21»), no en tomas
 * sueltas. Así el intervalo de la posología se respeta siempre (RN-14), y lo único que se puede
 * rechazar es que la primera toma quede en el pasado.
 */
data class RevisionDelPlan(
    val planes: List<PlanDeProducto>,
    /** Por medicamento, por qué no se aceptó el último cambio. */
    val avisos: Map<Int, String> = emptyMap(),
) {
    /** Los índices, dentro del plan de cada medicamento, de las tomas que se muestran. */
    fun visibles(producto: Int): List<Int> {
        val tomas = planes[producto].plan.tomas
        val limite = tomas.first().plus(UN_DIA)
        return tomas.indices.filter { tomas[it].isBefore(limite) }
    }

    /**
     * Las horas de las tomas visibles que caen mientras el paciente duerme, sin repetir y en el
     * orden del reloj. Suenan igual (RN-15): la pantalla lo dice antes de activar las alarmas.
     */
    fun horasNocturnas(rutina: Rutina): List<LocalTime> =
        planes.indices
            .flatMap { i -> visibles(i).map { planes[i].plan.tomas[it].toLocalTime() } }
            .filter { rutina.duerme(it) && it != rutina.acostarse }
            .distinct()
            .sorted()

    /** Corre [minutos] todas las tomas de un medicamento; las distancias entre ellas no cambian. */
    fun mover(producto: Int, minutos: Long, ahora: LocalDateTime): RevisionDelPlan {
        val actual = planes[producto]
        return when (val resultado = actual.plan.moverDesde(0, minutos, ahora)) {
            is PlanEditable.Resultado.Aceptado -> copy(
                planes = planes.toMutableList().also { it[producto] = actual.copy(plan = resultado.plan) },
                avisos = avisos - producto,
            )
            is PlanEditable.Resultado.Rechazado -> copy(avisos = avisos + (producto to resultado.motivo))
        }
    }

    private companion object {
        val UN_DIA: Duration = Duration.ofDays(1)
    }
}
