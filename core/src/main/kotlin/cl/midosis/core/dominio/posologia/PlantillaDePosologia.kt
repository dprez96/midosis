package cl.midosis.core.dominio.posologia

import cl.midosis.core.dominio.catalogo.Gtin
import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.motor.posologia.Frecuencia
import cl.midosis.motor.posologia.FrecuenciaInvalida
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Unidades en que se indica una dosis. Lista cerrada: la posología se registra en campos
 * estructurados (HU-03), no como texto libre. El código es lo que viaja por la API; la
 * web de farmacia tiene la misma lista con sus nombres para mostrar.
 */
enum class UnidadDeDosis(val codigo: String) {
    COMPRIMIDO("comprimido"),
    CAPSULA("capsula"),
    MILILITRO("ml"),
    GOTA("gota"),
    SOBRE("sobre"),
    INHALACION("inhalacion"),
    APLICACION("aplicacion"),
    SUPOSITORIO("supositorio"),
    PARCHE("parche"),
    UNIDAD_INTERNACIONAL("ui"),
    ;

    companion object {
        fun de(codigo: String): UnidadDeDosis =
            entries.firstOrNull { it.codigo == codigo.trim().lowercase() }
                ?: throw PosologiaInvalida("unidad de dosis desconocida «$codigo»")
    }
}

/** Cuánto se toma cada vez: una cantidad de hasta dos decimales, como 0,5 comprimido. */
data class Dosis(val cantidad: BigDecimal, val unidad: UnidadDeDosis) {
    init {
        if (cantidad.signum() <= 0) throw PosologiaInvalida("la cantidad por toma debe ser mayor que cero")
        if (cantidad > CANTIDAD_MAXIMA) throw PosologiaInvalida("la cantidad por toma no puede superar $CANTIDAD_MAXIMA")
        if (cantidad.stripTrailingZeros().scale() > 2) {
            throw PosologiaInvalida("la cantidad por toma admite hasta dos decimales")
        }
    }

    companion object {
        val CANTIDAD_MAXIMA = BigDecimal("999.99")

        /** Sin ceros sobrantes y sin notación científica: 1,00 queda 1 y 10,00 queda 10. */
        fun normalizar(cantidad: BigDecimal): BigDecimal {
            val sinCeros = cantidad.stripTrailingZeros()
            return if (sinCeros.scale() < 0) sinCeros.setScale(0) else sinCeros
        }
    }
}

/**
 * Lo que el químico farmacéutico configura para un producto de su comuna. La duración es
 * opcional: en los tratamientos crónicos la decide cada receta.
 */
data class NuevaPlantilla(
    val gtin: Gtin,
    val dosis: Dosis,
    val frecuencia: Frecuencia,
    val duracionDias: Int?,
    val indicaciones: String?,
) {
    init {
        if (duracionDias != null && duracionDias !in 1..DURACION_MAXIMA_DIAS) {
            throw PosologiaInvalida("la duración va de 1 a $DURACION_MAXIMA_DIAS días")
        }
        if (indicaciones != null && indicaciones.length > LARGO_MAXIMO_INDICACIONES) {
            throw PosologiaInvalida("las indicaciones admiten hasta $LARGO_MAXIMO_INDICACIONES caracteres")
        }
    }

    companion object {
        const val DURACION_MAXIMA_DIAS = 365

        /** Viajan en el código impreso, que tiene espacio limitado. */
        const val LARGO_MAXIMO_INDICACIONES = 120

        private val ESPACIOS = Regex("""\s+""")

        fun de(
            gtin: Gtin,
            cantidad: BigDecimal,
            unidad: String,
            frecuencia: String,
            duracionDias: Int?,
            indicaciones: String?,
        ) = NuevaPlantilla(
            gtin = gtin,
            dosis = Dosis(Dosis.normalizar(cantidad), UnidadDeDosis.de(unidad)),
            frecuencia = try {
                Frecuencia.de(frecuencia.trim().uppercase())
            } catch (e: FrecuenciaInvalida) {
                throw PosologiaInvalida("la frecuencia no es válida: ${e.message}")
            },
            duracionDias = duracionDias,
            indicaciones = limpiar(indicaciones),
        )

        /**
         * Los caracteres de control, como un salto de línea, pasan a ser espacios; los
         * espacios repetidos se juntan. Vacías equivalen a no tener indicaciones.
         */
        fun limpiar(texto: String?): String? =
            texto?.map { if (it.isISOControl()) ' ' else it }?.joinToString("")
                ?.replace(ESPACIOS, " ")?.trim()?.ifEmpty { null }
    }
}

data class PlantillaDePosologia(
    val id: UUID,
    val comuna: CodigoComuna,
    val gtin: Gtin,
    val dosis: Dosis,
    val frecuencia: Frecuencia,
    val duracionDias: Int?,
    val indicaciones: String?,
    val creadaEn: Instant,
)

/** Acceso a las plantillas. Solo se usa dentro de un contexto de comuna. */
interface PlantillasDePosologia {
    /** Las plantillas del producto, de la más antigua a la más nueva. */
    fun listar(gtin: Gtin): List<PlantillaDePosologia>

    /** Falla con DuplicateKeyException si ya existe una igual para el producto. */
    fun crear(comuna: CodigoComuna, nueva: NuevaPlantilla, autor: String): PlantillaDePosologia
}

class PosologiaInvalida(mensaje: String) : IllegalArgumentException(mensaje)

class ProductoNoEncontrado : RuntimeException("el producto no está en el catálogo de la comuna")

class PlantillaDuplicada : RuntimeException("ya existe una plantilla con esa misma posología para este producto")
