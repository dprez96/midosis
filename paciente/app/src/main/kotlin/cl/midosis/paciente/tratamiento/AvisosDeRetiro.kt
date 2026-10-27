package cl.midosis.paciente.tratamiento

import cl.midosis.motor.posologia.Frecuencia
import cl.midosis.motor.posologia.TramoDispensado
import cl.midosis.motor.rutina.Rutina
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Un aviso de próximo retiro: cuándo sonar y qué medicamentos se acaban, y cuándo. */
data class AvisoDeRetiro(val momento: LocalDateTime, val productos: List<ProductoPorAcabarse>)

data class ProductoPorAcabarse(val etiqueta: String, val seAgotaEl: LocalDate)

/**
 * Los avisos de próximo retiro (HU-20, RF-M-08): antes de que se acabe lo retirado, el
 * teléfono avisa que hay que volver a la farmacia. La anticipación es la del motor,
 * [TramoDispensado.ANTICIPACION_AVISO_DIAS] (5 días), confirmada por el químico farmacéutico
 * asesor (RN-07).
 *
 * El aviso suena a la hora del desayuno del paciente: una hora en que está despierto y que él
 * mismo eligió. Los medicamentos que se avisan el mismo día van en un solo aviso.
 *
 * No se avisa cuando lo retirado completa el tratamiento indicado, ni cuando ya se cargó un
 * tramo posterior del mismo producto (HU-21). Si la hora del aviso ya pasó, no se programa:
 * la pantalla principal ya dice «vuelve a la farmacia» (Resumen.Estado.VOLVER_PRONTO).
 */
object AvisosDeRetiro {

    fun calcular(
        tratamientos: List<TratamientoGuardado>,
        rutina: Rutina,
        ahora: LocalDateTime,
        zona: ZoneId = ZoneId.systemDefault(),
    ): List<AvisoDeRetiro> {
        // El último tramo cargado de cada producto es el que manda.
        val vigentes = tratamientos
            .flatMap { t -> t.carga.productos.map { p -> Triple(p, t.carga.emitidoEn, t.cargadoEn) } }
            .groupBy { (p, _, _) -> p.gtin }
            .values
            .map { tramos -> tramos.maxBy { (_, _, cargadoEn) -> cargadoEn } }

        return vigentes
            .mapNotNull { (p, emitidoEn, _) ->
                val tramo = TramoDispensado(p.duracionIndicadaDias, p.dosisEntregadas, Frecuencia.de(p.intervalo))
                val retiro = Instant.ofEpochSecond(emitidoEn).atZone(zona).toLocalDate()
                val dia = tramo.avisoDeProximoRetiro(retiro) ?: return@mapNotNull null
                val momento = dia.atTime(rutina.desayuno)
                if (!momento.isAfter(ahora)) null else momento to ProductoPorAcabarse(p.etiqueta, tramo.seAgotaEl(retiro))
            }
            .groupBy({ it.first }, { it.second })
            .map { (momento, productos) -> AvisoDeRetiro(momento, productos.sortedBy { it.etiqueta }) }
            .sortedBy { it.momento }
    }
}
