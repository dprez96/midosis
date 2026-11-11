package cl.midosis.core.aplicacion.demanda

import cl.midosis.core.dominio.catalogo.CatalogoDeProductos
import cl.midosis.core.dominio.catalogo.Gtin
import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.core.dominio.demanda.ProyeccionDeDemanda
import cl.midosis.core.infraestructura.persistencia.ContextoComuna
import cl.midosis.core.infraestructura.persistencia.EmisionesJdbc
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.springframework.stereotype.Service

/**
 * La demanda proyectada de la comuna (HU-27): por producto y por semana, las próximas 12
 * semanas, corregida por la tasa de retorno y contrastada con lo retirado en las 4 últimas.
 */
@Service
class DemandaDeRetiros(
    private val contexto: ContextoComuna,
    private val emisiones: EmisionesJdbc,
    private val catalogo: CatalogoDeProductos,
) {
    data class Producto(
        val gtin: String,
        val nombre: String,
        /** Unidades por semana, en el orden de [Demanda.semanas]. */
        val unidades: List<Int>,
        val tasaDeRetorno: Double,
    )

    data class Desvio(val gtin: String, val nombre: String, val semana: LocalDate, val proyectado: Int, val real: Int)

    data class Demanda(
        val semanas: List<LocalDate>,
        val productos: List<Producto>,
        val supuesto: String,
        val desviacion: List<Desvio>,
    )

    fun calcular(comuna: CodigoComuna, hoy: LocalDate = LocalDate.now(ZONA)): Demanda = contexto.en(comuna) {
        // Un tratamiento indicado dura a lo más 365 días: lo anterior ya no anuncia retiros.
        val desde = hoy.minusDays(400).atStartOfDay(ZONA).toInstant()
        val retiros = emisiones.retiros(desde, ZONA)
        val proyeccion = ProyeccionDeDemanda.proyectar(retiros, hoy)
        val nombres = HashMap<String, String>()
        fun nombre(gtin: String) = nombres.getOrPut(gtin) {
            runCatching { catalogo.buscar(Gtin.de(gtin))?.nombre }.getOrNull() ?: gtin
        }
        Demanda(
            semanas = proyeccion.semanas,
            productos = proyeccion.porProducto
                .filterValues { it.any { u -> u > 0 } }
                .map { (gtin, unidades) -> Producto(gtin, nombre(gtin), unidades, proyeccion.tasaDeRetorno[gtin] ?: 1.0) }
                .sortedByDescending { it.unidades.sum() },
            supuesto = ProyeccionDeDemanda.SUPUESTO,
            desviacion = ProyeccionDeDemanda.desviacion(retiros, hoy)
                .map { Desvio(it.gtin, nombre(it.gtin), it.semana, it.proyectado, it.real) }
                .sortedWith(compareBy({ it.semana }, { it.nombre })),
        )
    }

    private companion object {
        /** Las semanas y los días son los de Chile continental, donde operan las farmacias populares. */
        val ZONA: ZoneId = ZoneId.of("America/Santiago")
    }
}
