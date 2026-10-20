package cl.midosis.paciente.tratamiento

import cl.midosis.credencial.Observacion
import cl.midosis.motor.planificacion.PlanEditable
import cl.midosis.motor.planificacion.PlanificadorDeTomas
import cl.midosis.motor.planificacion.Restriccion
import cl.midosis.motor.planificacion.TomaProgramada
import cl.midosis.motor.posologia.Frecuencia
import cl.midosis.motor.rutina.Rutina
import java.time.LocalDateTime

/** El calendario de un medicamento, que el paciente puede ajustar antes de confirmarlo (HU-14). */
data class PlanDeProducto(val etiqueta: String, val dosis: String, val plan: PlanEditable)

/**
 * Las tomas del tramo cubierto de un tratamiento (HU-08): una por cada dosis entregada, con las
 * horas que propone el motor según la rutina del paciente.
 */
object Planificacion {

    /** El plan que propone el motor, un calendario por medicamento. */
    fun proponer(tratamiento: TratamientoGuardado, rutina: Rutina, desde: LocalDateTime): List<PlanDeProducto> {
        val planificador = PlanificadorDeTomas(rutina)
        return tratamiento.carga.productos.mapNotNull { p ->
            val frecuencia = Frecuencia.de(p.intervalo)
            val momentos = planificador.planificar(frecuencia, p.dosisEntregadas, restricciones(p.observaciones), desde)
            if (momentos.isEmpty()) null else PlanDeProducto(p.etiqueta, p.dosis, PlanEditable(momentos, frecuencia.intervalo))
        }
    }

    /** Las tomas de un plan ya revisado por el paciente. */
    fun tomasDe(tratamiento: TratamientoGuardado, planes: List<PlanDeProducto>): List<TomaProgramada> =
        planes.flatMap { p -> p.plan.tomas.map { momento -> TomaProgramada(tratamiento.carga.id, p.etiqueta, p.dosis, momento) } }

    fun tomasDe(tratamiento: TratamientoGuardado, rutina: Rutina, desde: LocalDateTime): List<TomaProgramada> =
        tomasDe(tratamiento, proponer(tratamiento, rutina, desde))

    /** Las observaciones del código que fijan la hora. Las demás se muestran, pero no la mueven. */
    fun restricciones(observaciones: List<Observacion>): Set<Restriccion> = observaciones.mapNotNull {
        when (it) {
            Observacion.EN_AYUNAS -> Restriccion.EN_AYUNAS
            Observacion.CON_ALIMENTOS -> Restriccion.CON_ALIMENTOS
            Observacion.AL_ACOSTARSE -> Restriccion.AL_ACOSTARSE
            Observacion.NO_PARTIR_NI_MASTICAR, Observacion.AGITAR_ANTES_DE_USAR -> null
        }
    }.toSet()
}
