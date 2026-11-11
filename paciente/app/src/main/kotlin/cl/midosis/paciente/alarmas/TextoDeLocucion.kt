package cl.midosis.paciente.alarmas

import cl.midosis.credencial.Observacion
import cl.midosis.paciente.tratamiento.TratamientoGuardado

/**
 * Lo que se lee en voz alta cuando suena la toma (HU-22, RF-M-19): cada medicamento con su
 * dosis y su indicación de administración, en frases cortas y sin abreviaturas, para que la
 * síntesis de voz las pronuncie bien.
 *
 * Es una función pura: no depende de Android y se prueba en la computadora.
 */
object TextoDeLocucion {

    /**
     * [lineas] son las de la alarma («Losartán 50 mg · 1 comprimido»); [indicaciones], las
     * observaciones de cada producto según su etiqueta, tomadas de los tratamientos guardados.
     */
    fun armar(lineas: List<String>, indicaciones: Map<String, List<Observacion>> = emptyMap()): String {
        val titulo = if (lineas.size > 1) "Hora de tus medicamentos." else "Hora de tu medicamento."
        val frases = lineas.map { linea ->
            val producto = linea.substringBefore(SEPARADOR).trim()
            val dosis = linea.substringAfter(SEPARADOR, "").trim()
            val partes = listOf(producto, dosis).filter { it.isNotEmpty() } + indicaciones[producto].orEmpty().map(::frase)
            sinAbreviaturas(partes.joinToString(", ")) + "."
        }
        return (listOf(titulo) + frases).joinToString(" ")
    }

    /** Las indicaciones de cada producto de los tratamientos guardados, por su etiqueta. */
    fun indicacionesDe(tratamientos: List<TratamientoGuardado>): Map<String, List<Observacion>> =
        tratamientos.flatMap { it.carga.productos }
            .groupBy({ it.etiqueta }, { it.observaciones })
            .mapValues { (_, listas) -> listas.flatten().distinct() }

    fun frase(observacion: Observacion): String = when (observacion) {
        Observacion.EN_AYUNAS -> "en ayunas"
        Observacion.CON_ALIMENTOS -> "con alimentos"
        Observacion.AL_ACOSTARSE -> "al acostarse"
        Observacion.NO_PARTIR_NI_MASTICAR -> "sin partir ni masticar"
        Observacion.AGITAR_ANTES_DE_USAR -> "agitar antes de usar"
    }

    /** La voz leería «eme ge»: se escriben completas las unidades más comunes. */
    fun sinAbreviaturas(texto: String): String = ABREVIATURAS.fold(texto) { t, (patron, completo) -> patron.replace(t, completo) }

    private const val SEPARADOR = " · "

    private val ABREVIATURAS = listOf(
        Regex("""\bmcg\b""") to "microgramos",
        Regex("""\bmg\b""") to "miligramos",
        Regex("""\bml\b""") to "mililitros",
        Regex("""\bUI\b""") to "unidades",
        Regex("""\bcomp\.(?=\s|$|,)""") to "comprimido",
    )
}
