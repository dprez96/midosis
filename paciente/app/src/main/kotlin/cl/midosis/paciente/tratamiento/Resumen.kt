package cl.midosis.paciente.tratamiento

import cl.midosis.credencial.Producto
import cl.midosis.motor.posologia.Frecuencia
import cl.midosis.motor.posologia.TramoDispensado
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Lo que la pantalla principal dice de cada medicamento (HU-19, RF-M-07): cuánto dura el
 * tratamiento indicado y hasta cuándo alcanza lo retirado, dos cifras distintas (ADR-010).
 *
 * Mientras lo indicado supere lo entregado, el tratamiento nunca se da por terminado y se
 * avisa cuándo volver a la farmacia (RN-07). El cálculo es el del motor.
 */
data class Resumen(
    val etiqueta: String,
    val indicadoDias: Int,
    val cubiertoDias: Int,
    val retiro: LocalDate,
    val alcanzaHasta: LocalDate,
    /** Días de medicamento que quedan desde hoy; cero si ya se acabó. */
    val quedanDias: Long,
    val estado: Estado,
) {
    enum class Estado {
        /** Lo retirado completa lo indicado: no hace falta volver. */
        COMPLETO,

        /** Queda medicamento y todavía no es hora de avisar. */
        AL_DIA,

        /** Se acerca el agotamiento: hay que volver a la farmacia (RN-07). */
        VOLVER_PRONTO,

        /** Se acabó lo retirado, pero el tratamiento indicado sigue. */
        SE_ACABO,
    }

    companion object {
        fun de(producto: Producto, emitidoEn: Long, hoy: LocalDate, zona: ZoneId = ZoneId.systemDefault()): Resumen {
            val tramo = TramoDispensado(
                duracionIndicadaDias = producto.duracionIndicadaDias,
                dosisEntregadas = producto.dosisEntregadas,
                frecuencia = Frecuencia.de(producto.intervalo),
            )
            // El retiro es el día en que la farmacia emitió el código, en la hora del teléfono.
            val retiro = Instant.ofEpochSecond(emitidoEn).atZone(zona).toLocalDate()
            val hasta = tramo.seAgotaEl(retiro)
            val aviso = tramo.avisoDeProximoRetiro(retiro)
            val estado = when {
                tramo.completaLoIndicado -> Estado.COMPLETO
                !hoy.isBefore(hasta) -> Estado.SE_ACABO
                aviso != null && !hoy.isBefore(aviso) -> Estado.VOLVER_PRONTO
                else -> Estado.AL_DIA
            }
            return Resumen(
                etiqueta = producto.etiqueta,
                indicadoDias = producto.duracionIndicadaDias,
                cubiertoDias = tramo.coberturaDias,
                retiro = retiro,
                alcanzaHasta = hasta,
                quedanDias = maxOf(0, ChronoUnit.DAYS.between(hoy, hasta)),
                estado = estado,
            )
        }
    }
}
