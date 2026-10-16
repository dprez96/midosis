package cl.midosis.paciente.tratamiento

import cl.midosis.credencial.Observacion
import cl.midosis.motor.planificacion.PlanificadorDeTomas
import cl.midosis.motor.planificacion.Restriccion
import cl.midosis.motor.planificacion.TomaProgramada
import cl.midosis.motor.posologia.Frecuencia
import cl.midosis.motor.rutina.Rutina
import java.time.LocalDateTime

/**
 * Las tomas del tramo cubierto de un tratamiento (HU-08): una por cada dosis entregada, con las
 * horas que propone el motor según la rutina del paciente.
 */
object Planificacion {

    fun tomasDe(tratamiento: TratamientoGuardado, rutina: Rutina, desde: LocalDateTime): List<TomaProgramada> {
        val planificador = PlanificadorDeTomas(rutina)
        return tratamiento.carga.productos.flatMap { p ->
            planificador.planificar(Frecuencia.de(p.intervalo), p.dosisEntregadas, restricciones(p.observaciones), desde)
                .map { momento -> TomaProgramada(tratamiento.carga.id, p.etiqueta, p.dosis, momento) }
        }
    }

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
