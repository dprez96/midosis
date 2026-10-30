package cl.midosis.core.dominio.demanda

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil
import kotlin.math.min

/**
 * Un retiro registrado en la comuna, tal como queda en el registro de emisión seudonimizado
 * (sección 7.7 del informe): sin ningún dato del paciente.
 */
data class Retiro(
    val gtin: String,
    val dia: LocalDate,
    val duracionIndicadaDias: Int,
    val coberturaDias: Int,
    /** En la unidad del producto: comprimidos, ml, inhalaciones. */
    val unidades: Double,
)

data class Proyeccion(
    /** El lunes de cada una de las semanas proyectadas. */
    val semanas: List<LocalDate>,
    /** Por GTIN, las unidades estimadas de cada semana, ya corregidas por la tasa de retorno. */
    val porProducto: Map<String, List<Int>>,
    /** Por GTIN, la tasa de retorno aplicada (de 0 a 1). */
    val tasaDeRetorno: Map<String, Double>,
)

/** Lo proyectado para una semana pasada, contrastado con lo que de verdad se retiró. */
data class Desvio(val gtin: String, val semana: LocalDate, val proyectado: Int, val real: Int)

/**
 * La proyección de la demanda de retiros (HU-27, RF-B-08): cuántas unidades de cada producto se
 * van a retirar en las próximas semanas, para programar la compra a CENABAST.
 *
 * Cada retiro cuyo tratamiento indicado sigue después de lo entregado anuncia retiros futuros:
 * uno cada vez que se agota lo entregado, con las mismas unidades, hasta completar lo indicado.
 * Solo se proyectan los retiros que todavía no debieron ocurrir: si un paciente ya debía haber
 * vuelto, o volvió, y entonces ese retiro nuevo proyecta lo que sigue, o no volvió, y la tasa de
 * retorno lo refleja. Así un mismo tratamiento no se cuenta dos veces aunque los retiros no se
 * puedan vincular entre sí (no llevan ningún dato del paciente).
 */
object ProyeccionDeDemanda {

    const val SEMANAS = 12

    /** Cuánto hacia atrás se mira para estimar la tasa de retorno. */
    const val VENTANA_DE_RETORNO_DIAS = 84L

    /** Con menos renovaciones esperadas que esto, la tasa no es confiable y se supone 1. */
    const val MINIMO_PARA_TASA = 5

    val SUPUESTO: String =
        "La tasa de retorno de cada producto es la razón entre los retiros registrados en las últimas " +
            "12 semanas y las renovaciones que debían ocurrir en ese período, con un máximo de 100 %. Como " +
            "los retiros no identifican al paciente, un paciente nuevo cuenta como un retorno: la tasa puede " +
            "sobrestimarse. Con menos de $MINIMO_PARA_TASA renovaciones esperadas se supone que todos vuelven."

    fun proyectar(retiros: List<Retiro>, desde: LocalDate, semanas: Int = SEMANAS): Proyeccion {
        val lunes = desde.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val fin = lunes.plusWeeks(semanas.toLong())
        val previos = retiros.filter { it.dia.isBefore(lunes) }
        val tasas = tasasDeRetorno(previos, lunes)
        val cubos = HashMap<String, DoubleArray>()

        for (r in previos) {
            if (r.coberturaDias <= 0) continue
            var pendiente = r.duracionIndicadaDias - r.coberturaDias
            var proximo = r.dia.plusDays(r.coberturaDias.toLong())
            // Ya debió renovarse: si el paciente volvió, ese retiro proyecta lo que sigue.
            if (proximo.isBefore(lunes)) continue
            val porDia = r.unidades / r.coberturaDias
            val tasa = tasas[r.gtin] ?: 1.0
            while (pendiente > 0 && proximo.isBefore(fin)) {
                val semana = ChronoUnit.WEEKS.between(lunes, proximo).toInt()
                cubos.getOrPut(r.gtin) { DoubleArray(semanas) }[semana] += porDia * min(pendiente, r.coberturaDias) * tasa
                pendiente -= r.coberturaDias
                proximo = proximo.plusDays(r.coberturaDias.toLong())
            }
        }

        return Proyeccion(
            semanas = (0 until semanas).map { lunes.plusWeeks(it.toLong()) },
            // Hacia arriba: para comprar, falta una unidad es peor que sobra una.
            porProducto = cubos.mapValues { (_, cubo) -> cubo.map { ceil(it - 1e-9).toInt() } },
            tasaDeRetorno = tasas,
        )
    }

    /**
     * Para cada una de las últimas [semanas] semanas, lo que la proyección hecha al comenzar esa
     * semana estimaba, frente a las unidades que de verdad se retiraron (tercer criterio).
     */
    fun desviacion(retiros: List<Retiro>, hoy: LocalDate, semanas: Int = 4): List<Desvio> {
        val lunesActual = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (semanas downTo 1).flatMap { atras ->
            val semana = lunesActual.minusWeeks(atras.toLong())
            val proyectado = proyectar(retiros, semana, semanas = 1).porProducto
            val real = retiros
                .filter { !it.dia.isBefore(semana) && it.dia.isBefore(semana.plusWeeks(1)) }
                .groupBy { it.gtin }
                .mapValues { (_, rs) -> ceil(rs.sumOf { it.unidades } - 1e-9).toInt() }
            (proyectado.keys + real.keys).map { gtin ->
                Desvio(gtin, semana, proyectado[gtin]?.first() ?: 0, real[gtin] ?: 0)
            }
        }
    }

    private fun tasasDeRetorno(previos: List<Retiro>, lunes: LocalDate): Map<String, Double> {
        val inicio = lunes.minusDays(VENTANA_DE_RETORNO_DIAS)
        fun enVentana(dia: LocalDate) = !dia.isBefore(inicio) && dia.isBefore(lunes)
        return previos.groupBy { it.gtin }.mapValues { (_, rs) ->
            val esperados = rs.count { it.duracionIndicadaDias > it.coberturaDias && enVentana(it.dia.plusDays(it.coberturaDias.toLong())) }
            val observados = rs.count { enVentana(it.dia) }
            if (esperados < MINIMO_PARA_TASA) 1.0 else min(1.0, observados.toDouble() / esperados)
        }
    }
}
