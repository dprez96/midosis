package cl.midosis.core.dominio.emision

import cl.midosis.core.dominio.catalogo.Gtin
import cl.midosis.core.dominio.posologia.Dosis
import cl.midosis.credencial.Via
import cl.midosis.motor.posologia.Frecuencia
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * Consentimiento informado del paciente (HU-05, RN-11): qué versión del texto se le mostró y
 * cuándo aceptó. Sin consentimiento no se emite; la entrega del medicamento sigue igual.
 */
data class Consentimiento(val version: String, val aceptadoEn: Instant) {
    init {
        if (version != VERSION_VIGENTE) throw EmisionInvalida("el consentimiento no corresponde al texto vigente")
    }

    companion object {
        /** La versión del texto que muestra la web. Cambiar el texto obliga a cambiar la versión. */
        const val VERSION_VIGENTE = "2026-10-v1"
    }
}

/** Un producto de la atención, tal como lo registró el mesón (HU-02, HU-03 y HU-04). */
data class LineaDeEmision(
    val gtin: Gtin,
    val dosis: Dosis,
    val frecuencia: Frecuencia,
    val duracionIndicadaDias: Int,
    /** En la unidad de la dosis: comprimidos, inhalaciones, ml. */
    val unidadesEntregadas: BigDecimal,
    val via: Via,
    val indicaciones: String?,
) {
    init {
        if (duracionIndicadaDias !in 1..365) throw EmisionInvalida("la duración indicada va de 1 a 365 días")
        if (unidadesEntregadas.signum() <= 0) throw EmisionInvalida("indique la cantidad entregada")
        if (tomasEntregadas !in 1..9999) {
            throw EmisionInvalida("lo entregado debe alcanzar para al menos una toma y como máximo 9999")
        }
    }

    /** «e» del código: tomas completas, no unidades (ADR-010). */
    val tomasEntregadas: Int
        get() = unidadesEntregadas.divide(dosis.cantidad, 0, RoundingMode.FLOOR).toInt()
}

data class SolicitudDeEmision(val lineas: List<LineaDeEmision>, val consentimiento: Consentimiento) {
    init {
        if (lineas.isEmpty()) throw EmisionInvalida("la atención no tiene productos")
        if (lineas.size > 20) throw EmisionInvalida("un código admite hasta 20 productos")
    }
}

/** Lo que se imprime en el comprobante: el código y su respaldo para cargarlo a mano. */
data class CodigoEmitido(
    val id: String,
    val codigo: String,
    val codigoCorto: String,
    val pin: String,
    val expiraEn: Instant,
)

class EmisionInvalida(mensaje: String) : IllegalArgumentException(mensaje)

/** No hay claves para firmar los códigos de la comuna: la emisión no está disponible. */
class EmisionNoDisponible(mensaje: String) : RuntimeException(mensaje)
