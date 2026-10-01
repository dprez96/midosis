package cl.midosis.core.dominio.catalogo

import cl.midosis.core.dominio.comuna.CodigoComuna

/**
 * Un producto del catálogo de una comuna: lo que el lector identifica en el mesón y lo
 * que el paciente ve en su tratamiento. Cada farmacia popular tiene su propio surtido,
 * así que el catálogo es por comuna.
 */
data class Producto(
    val comuna: CodigoComuna,
    val gtin: Gtin,
    val nombre: String,
    val principioActivo: String,
    val forma: String,
    val concentracion: String,
) {
    init {
        require(nombre.isNotBlank()) { "el producto necesita un nombre" }
        require(principioActivo.isNotBlank()) { "el producto necesita su principio activo" }
        require(forma.isNotBlank()) { "el producto necesita su forma farmacéutica" }
        require(concentracion.isNotBlank()) { "el producto necesita su concentración" }
    }
}

/** Acceso al catálogo. Solo se usa dentro de un contexto de comuna. */
interface CatalogoDeProductos {
    fun listar(): List<Producto>
    fun buscar(gtin: Gtin): Producto?

    /**
     * Productos cuyo nombre o principio activo contiene el término, o cuyo código empieza
     * por él. Ordenados por nombre y acotados a [limite].
     */
    fun buscarPorTexto(termino: TerminoDeBusqueda, limite: Int): List<Producto>

    fun guardar(producto: Producto)
}
