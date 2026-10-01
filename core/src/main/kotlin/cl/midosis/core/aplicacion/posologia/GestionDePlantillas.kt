package cl.midosis.core.aplicacion.posologia

import cl.midosis.core.dominio.catalogo.CatalogoDeProductos
import cl.midosis.core.dominio.catalogo.Gtin
import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.core.dominio.posologia.NuevaPlantilla
import cl.midosis.core.dominio.posologia.PlantillaDePosologia
import cl.midosis.core.dominio.posologia.PlantillaDuplicada
import cl.midosis.core.dominio.posologia.PlantillasDePosologia
import cl.midosis.core.dominio.posologia.ProductoNoEncontrado
import cl.midosis.core.infraestructura.persistencia.ContextoComuna
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service

/**
 * Plantillas de posología frecuente de la comuna (HU-03). Quién puede crearlas lo decide
 * la configuración de seguridad: solo el químico farmacéutico.
 */
@Service
class GestionDePlantillas(
    private val contexto: ContextoComuna,
    private val catalogo: CatalogoDeProductos,
    private val plantillas: PlantillasDePosologia,
) {
    /** Un producto ajeno a la comuna responde igual que uno inexistente. */
    fun listar(comuna: CodigoComuna, gtin: Gtin): List<PlantillaDePosologia> =
        contexto.en(comuna) {
            catalogo.buscar(gtin) ?: throw ProductoNoEncontrado()
            plantillas.listar(gtin)
        }

    fun crear(comuna: CodigoComuna, nueva: NuevaPlantilla, autor: String): PlantillaDePosologia =
        try {
            contexto.en(comuna) {
                catalogo.buscar(nueva.gtin) ?: throw ProductoNoEncontrado()
                plantillas.crear(comuna, nueva, autor)
            }
        } catch (e: DuplicateKeyException) {
            throw PlantillaDuplicada()
        }
}
