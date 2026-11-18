package cl.midosis.core.infraestructura.web

import cl.midosis.core.aplicacion.emision.CorreccionDeCodigos
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Consulta del estado de los códigos cargados en un teléfono (RF-B-15, RF-M-30). Como el canje,
 * no lleva token: el paciente no tiene cuenta. La aplicación envía solo los jti, que son
 * aleatorios, y recibe por cada uno vigente, corregido (con la instrucción), revocado o
 * desconocido. Nada identifica al paciente ni revela el tratamiento o la comuna.
 *
 * Se usa POST y no GET para que los jti no queden en las URL de los registros.
 */
@RestController
@RequestMapping("/api/estado")
class EstadoControlador(private val correccion: CorreccionDeCodigos) {

    data class Consulta(val ids: List<String>?)

    data class Respuesta(val codigos: List<CorreccionDeCodigos.Estado>)

    @PostMapping
    fun consultar(@RequestBody consulta: Consulta): ResponseEntity<Any> {
        val ids = consulta.ids.orEmpty()
        if (ids.isEmpty() || ids.size > CorreccionDeCodigos.MAXIMO_POR_CONSULTA || !ids.all(CorreccionDeCodigos.ULID::matches)) {
            return ResponseEntity.badRequest().body(
                mapOf("error" to "Envía entre 1 y ${CorreccionDeCodigos.MAXIMO_POR_CONSULTA} identificadores de código válidos."),
            )
        }
        return ResponseEntity.ok(Respuesta(correccion.estados(ids)))
    }
}
