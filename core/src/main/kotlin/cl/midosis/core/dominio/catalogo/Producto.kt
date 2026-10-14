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
    /** Null si el producto no está sujeto a control legal. */
    val controlLegal: ControlLegal? = null,
) {
    init {
        require(nombre.isNotBlank()) { "el producto necesita un nombre" }
        require(principioActivo.isNotBlank()) { "el producto necesita su principio activo" }
        require(forma.isNotBlank()) { "el producto necesita su forma farmacéutica" }
        require(concentracion.isNotBlank()) { "el producto necesita su concentración" }
    }
}

/**
 * Régimen de control legal de un producto (RN-10). El comprobante impreso omite el nombre
 * de estos productos (HU-06): circula en papel y revelaría el tratamiento del paciente.
 */
enum class ControlLegal(val codigo: String, val descripcion: String) {
    ESTUPEFACIENTE("DS404", "Estupefaciente, DS 404 de 1983"),
    PSICOTROPICO("DS405", "Psicotrópico, DS 405 de 1983"),
    ;

    companion object {
        fun de(codigo: String): ControlLegal =
            entries.firstOrNull { it.codigo == codigo } ?: throw IllegalArgumentException("control legal desconocido «$codigo»")
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
