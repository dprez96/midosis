package cl.midosis.core.aplicacion.catalogo

import cl.midosis.core.dominio.catalogo.CatalogoDeProductos
import cl.midosis.core.dominio.catalogo.Gtin
import cl.midosis.core.dominio.catalogo.Producto
import cl.midosis.core.dominio.catalogo.TerminoDeBusqueda
import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.core.infraestructura.persistencia.ContextoComuna
import org.springframework.stereotype.Service

/** Consulta del catálogo, siempre dentro del contexto de la comuna de quien pregunta. */
@Service
class ConsultaCatalogo(
    private val contexto: ContextoComuna,
    private val catalogo: CatalogoDeProductos,
) {
    fun listar(comuna: CodigoComuna): List<Producto> =
        contexto.en(comuna) { catalogo.listar() }

    fun buscar(comuna: CodigoComuna, gtin: Gtin): Producto? =
        contexto.en(comuna) { catalogo.buscar(gtin) }

    /** Búsqueda manual asistida, para cuando el lector no resuelve el producto (HU-02). */
    fun buscarPorTexto(comuna: CodigoComuna, termino: TerminoDeBusqueda): List<Producto> =
        contexto.en(comuna) { catalogo.buscarPorTexto(termino, LIMITE_BUSQUEDA) }

    companion object {
        /** En el mesón no se recorre una lista larga: si no aparece, se precisa la búsqueda. */
        const val LIMITE_BUSQUEDA = 20
    }
}
