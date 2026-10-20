package cl.midosis.paciente.tratamiento

import cl.midosis.motor.planificacion.PlanEditable
import java.time.Duration
import java.time.LocalDateTime

/**
 * El paso en que el paciente revisa el calendario propuesto y lo ajusta antes de confirmarlo
 * (HU-14, CU-P-03). La regla es la del motor ([PlanEditable]); aquí solo se decide qué tomas
 * se muestran y se guarda el último aviso de cada medicamento.
 *
 * Se muestran las tomas del primer día de cada medicamento: mover una con sus siguientes corre
 * todo el tramo, así que basta con ver un día para dejar las horas a gusto.
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
     * Mueve una toma [minutos]. Con [conLasSiguientes] corre también las que vienen después,
     * y las distancias no cambian; sin ello mueve solo esa, y el motor impide acercarla a una
     * vecina más que el intervalo de la posología.
     */
    fun mover(producto: Int, toma: Int, minutos: Long, conLasSiguientes: Boolean, ahora: LocalDateTime): RevisionDelPlan {
        val actual = planes[producto]
        val resultado = if (conLasSiguientes) {
            actual.plan.moverDesde(toma, minutos, ahora)
        } else {
            actual.plan.moverSola(toma, minutos, ahora)
        }
        return when (resultado) {
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
