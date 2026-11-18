package cl.midosis.core.dominio.emision

import cl.midosis.core.dominio.catalogo.Producto
import cl.midosis.core.dominio.posologia.PlantillaDePosologia
import java.time.Duration

/**
 * Cuándo una emisión necesita la validación de un químico farmacéutico (HU-32, RN-24): ninguna
 * transcripción sin revisión llega al paciente.
 *
 *  - La posología no coincide con ninguna plantilla del producto (misma dosis y frecuencia).
 *  - El producto está sujeto a control legal (DS 404 o DS 405).
 *  - Hay una advertencia de coherencia: lo entregado cubre más días que lo indicado, lo que suele
 *    delatar una receta mal transcrita.
 *
 * Cada motivo se escribe para mostrarlo tal cual en el mesón.
 */
object ReglasDeValidacion {

    fun motivos(lineas: List<LineaDeEmision>, productos: Map<String, Producto>, plantillas: Map<String, List<PlantillaDePosologia>>): List<String> =
        lineas.flatMap { linea ->
            val gtin = linea.gtin.valor
            val nombre = productos[gtin]?.nombre ?: gtin
            buildList {
                val conPlantilla = plantillas[gtin].orEmpty().any {
                    it.dosis.cantidad.compareTo(linea.dosis.cantidad) == 0 && it.dosis.unidad == linea.dosis.unidad && it.frecuencia == linea.frecuencia
                }
                if (!conPlantilla) add("$nombre: la posología no proviene de una plantilla.")
                productos[gtin]?.controlLegal?.let { add("$nombre: producto sujeto a control legal (${it.codigo}).") }
                val cubre = linea.frecuencia.lapsoDe(linea.tomasEntregadas)
                if (cubre > Duration.ofDays(linea.duracionIndicadaDias.toLong())) {
                    add("$nombre: lo entregado cubre más días que el tratamiento indicado.")
                }
            }
        }
}

/** Quién validó la posología: el identificador del químico farmacéutico en su token. */
data class Validacion(val qf: String)

/** La emisión necesita la validación de un químico farmacéutico y no la trae. */
class ValidacionRequerida(val motivos: List<String>) :
    RuntimeException("la posología necesita la validación de un químico farmacéutico")

/** La credencial presentada para validar no es de un químico farmacéutico de la comuna. */
class ValidacionInvalida(mensaje: String) : RuntimeException(mensaje)
