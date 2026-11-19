package cl.midosis.paciente.tratamiento

import cl.midosis.credencial.Carga
import cl.midosis.credencial.Producto
import cl.midosis.motor.posologia.Frecuencia
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Un producto del código nuevo que continúa el de un tratamiento ya cargado (HU-21): el tramo
 * siguiente de un tratamiento fraccionado.
 */
data class Continuacion(
    val anterior: TratamientoGuardado,
    val productoAnterior: Producto,
    val producto: Producto,
) {
    /** Misma dosis y misma frecuencia: las tomas siguen donde quedaron. */
    val mismaPosologia: Boolean
        get() = producto.dosis == productoAnterior.dosis && producto.intervalo == productoAnterior.intervalo
}

/**
 * La continuidad del tratamiento fraccionado (HU-21, RF-M-09). Cuando el paciente vuelve a la
 * farmacia y carga el código del tramo siguiente, el tratamiento sigue en vez de empezar de
 * nuevo:
 *
 *  - Con la misma posología, las tomas nuevas empiezan justo después de la última del tramo
 *    anterior: ni se repite ni se salta una dosis aunque haya retirado antes de agotarse.
 *  - Con otra posología, se muestran ambas y la nueva rige desde la carga: las tomas pendientes
 *    del tramo anterior se quitan.
 *  - El historial de respuestas no se toca: el tratamiento anterior queda guardado.
 */
object Continuidad {

    /**
     * Los productos de [nueva] que continúan un tratamiento guardado: el mismo GTIN, en un
     * tratamiento anterior cuyo período indicado no terminó cuando se emitió el código nuevo. Si
     * hay varios tramos anteriores, continúa el último cargado.
     */
    fun detectar(nueva: Carga, guardados: List<TratamientoGuardado>, zona: ZoneId = ZoneId.systemDefault()): List<Continuacion> {
        val emitido = fecha(nueva.emitidoEn, zona)
        return nueva.productos.mapNotNull { p ->
            guardados
                // El tratamiento que un código de reemplazo corrige no se continúa: se sustituye (HU-33).
                .filter { it.carga.id != nueva.id && it.carga.id != nueva.reemplazaA }
                .flatMap { t -> t.carga.productos.filter { it.gtin == p.gtin }.map { t to it } }
                .filter { (t, anterior) -> !emitido.isAfter(fecha(t.carga.emitidoEn, zona).plusDays(anterior.duracionIndicadaDias.toLong())) }
                .maxByOrNull { (t, _) -> t.cargadoEn }
                ?.let { (t, anterior) -> Continuacion(t, anterior, p) }
        }
    }

    /**
     * Cuándo empieza la cobertura de [producto] en [tratamiento]: el día del retiro o, si continúa
     * un tramo anterior con la misma posología que todavía no se agotaba, el día en que ese se
     * agota. Encadena todos los tramos: así «alcanza hasta» suma lo retirado antes de tiempo.
     */
    fun inicioDeCobertura(
        tratamiento: TratamientoGuardado,
        producto: Producto,
        guardados: List<TratamientoGuardado>,
        zona: ZoneId = ZoneId.systemDefault(),
    ): LocalDate {
        val retiro = fecha(tratamiento.carga.emitidoEn, zona)
        val previos = guardados.filter { it.cargadoEn < tratamiento.cargadoEn }
        val c = detectar(tratamiento.carga, previos, zona).firstOrNull { it.producto.gtin == producto.gtin } ?: return retiro
        if (!c.mismaPosologia) return retiro
        val finAnterior = inicioDeCobertura(c.anterior, c.productoAnterior, guardados, zona)
            .plusDays(Frecuencia.de(c.productoAnterior.intervalo).lapsoDe(c.productoAnterior.dosisEntregadas).toDays())
        return maxOf(retiro, finAnterior)
    }

    /** El número del tramo: 1 si no continúa ninguno, 2 si continúa el primero, y así. */
    fun numeroDeTramo(tratamiento: TratamientoGuardado, producto: Producto, guardados: List<TratamientoGuardado>, zona: ZoneId = ZoneId.systemDefault()): Int {
        val previos = guardados.filter { it.cargadoEn < tratamiento.cargadoEn }
        val c = detectar(tratamiento.carga, previos, zona).firstOrNull { it.producto.gtin == producto.gtin } ?: return 1
        return 1 + numeroDeTramo(c.anterior, c.productoAnterior, guardados, zona)
    }

    /**
     * Los tratamientos y productos que la pantalla principal muestra: de cada GTIN, solo el último
     * tramo cargado, que es el que manda. Los anteriores siguen guardados con su historial.
     */
    fun vigentes(guardados: List<TratamientoGuardado>): List<Pair<TratamientoGuardado, Producto>> =
        guardados
            .flatMap { t -> t.carga.productos.map { t to it } }
            // El mismo medicamento en dos perfiles son dos tratamientos (HU-31).
            .groupBy { (t, p) -> t.perfil to p.gtin }
            .values
            .map { tramos -> tramos.maxBy { (t, _) -> t.cargadoEn } }
            .sortedBy { (t, _) -> t.cargadoEn }

    /**
     * Desde cuándo planificar las tomas de una continuación con la misma posología: un intervalo
     * después de la última toma pendiente del tramo anterior, o null si no queda ninguna (se
     * planifica como un tramo nuevo, desde ahora).
     */
    fun inicioDeTomas(c: Continuacion, ultimaTomaAnterior: LocalDateTime?, ahora: LocalDateTime): LocalDateTime? {
        if (!c.mismaPosologia || ultimaTomaAnterior == null || !ultimaTomaAnterior.isAfter(ahora)) return null
        return ultimaTomaAnterior.plus(Frecuencia.de(c.producto.intervalo).intervalo)
    }

    private fun fecha(segundos: Long, zona: ZoneId): LocalDate = Instant.ofEpochSecond(segundos).atZone(zona).toLocalDate()
}
