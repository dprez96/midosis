package cl.midosis.core.infraestructura.web

import cl.midosis.core.aplicacion.catalogo.ConsultaCatalogo
import cl.midosis.core.dominio.catalogo.Gtin
import cl.midosis.core.dominio.catalogo.GtinInvalido
import cl.midosis.core.dominio.catalogo.Producto
import cl.midosis.core.dominio.catalogo.TerminoDeBusqueda
import cl.midosis.core.dominio.catalogo.TerminoInvalido
import cl.midosis.core.dominio.posologia.PlantillaDuplicada
import cl.midosis.core.dominio.posologia.PosologiaInvalida
import cl.midosis.core.dominio.posologia.ProductoNoEncontrado
import cl.midosis.core.infraestructura.seguridad.ComunaNoResuelta
import cl.midosis.core.infraestructura.seguridad.ResolutorDeComuna
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Catálogo de la comuna de quien consulta. La comuna nunca aparece en la ruta. */
@RestController
@RequestMapping("/api/catalogo/productos")
class CatalogoControlador(private val catalogo: ConsultaCatalogo) {

    @GetMapping
    fun listar(@AuthenticationPrincipal jwt: Jwt): List<ProductoRespuesta> =
        catalogo.listar(ResolutorDeComuna.desde(jwt)).map(ProductoRespuesta::de)

    /** Búsqueda manual asistida por nombre, principio activo o comienzo del código (HU-02). */
    @GetMapping(params = ["texto"])
    fun buscarPorTexto(@AuthenticationPrincipal jwt: Jwt, @RequestParam texto: String): List<ProductoRespuesta> =
        catalogo.buscarPorTexto(ResolutorDeComuna.desde(jwt), TerminoDeBusqueda.de(texto)).map(ProductoRespuesta::de)

    /**
     * Un producto que existe en otra comuna responde exactamente igual que uno que no
     * existe: 404. Así no se puede usar esta consulta para averiguar qué tienen las demás.
     */
    @GetMapping("/{gtin}")
    fun buscar(@AuthenticationPrincipal jwt: Jwt, @PathVariable gtin: String): ResponseEntity<ProductoRespuesta> {
        val producto = catalogo.buscar(ResolutorDeComuna.desde(jwt), Gtin.de(gtin))
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(ProductoRespuesta.de(producto))
    }
}

/** Lo que ve el cliente. No incluye la comuna: ya la conoce, y no hace falta repetirla. */
data class ProductoRespuesta(
    val gtin: String,
    val nombre: String,
    val principioActivo: String,
    val forma: String,
    val concentracion: String,
) {
    companion object {
        fun de(p: Producto) = ProductoRespuesta(p.gtin.valor, p.nombre, p.principioActivo, p.forma, p.concentracion)
    }
}

@RestControllerAdvice
class ManejoDeErrores {

    @ExceptionHandler(ComunaNoResuelta::class)
    fun comunaNoResuelta(): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "acceso denegado"))

    @ExceptionHandler(GtinInvalido::class)
    fun gtinInvalido(e: GtinInvalido): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "código de producto inválido")))

    @ExceptionHandler(TerminoInvalido::class)
    fun terminoInvalido(e: TerminoInvalido): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "búsqueda inválida")))

    @ExceptionHandler(PosologiaInvalida::class)
    fun posologiaInvalida(e: PosologiaInvalida): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "posología inválida")))

    /** Igual que la consulta de un producto: sin cuerpo, exista o no en otra comuna. */
    @ExceptionHandler(ProductoNoEncontrado::class)
    fun productoNoEncontrado(): ResponseEntity<Unit> = ResponseEntity.notFound().build()

    @ExceptionHandler(PlantillaDuplicada::class)
    fun plantillaDuplicada(e: PlantillaDuplicada): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to (e.message ?: "plantilla duplicada")))
}
