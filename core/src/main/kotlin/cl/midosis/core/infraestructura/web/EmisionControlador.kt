package cl.midosis.core.infraestructura.web

import cl.midosis.core.aplicacion.emision.CorreccionDeCodigos
import cl.midosis.core.aplicacion.emision.EmisionDeCodigos
import cl.midosis.core.aplicacion.emision.RevocacionDeCodigos
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import cl.midosis.core.dominio.catalogo.Gtin
import cl.midosis.core.dominio.emision.CodigoEmitido
import cl.midosis.core.dominio.emision.Consentimiento
import cl.midosis.core.dominio.emision.EmisionInvalida
import cl.midosis.core.dominio.emision.EmisionNoDisponible
import cl.midosis.core.dominio.emision.LineaDeEmision
import cl.midosis.core.dominio.emision.SolicitudDeEmision
import cl.midosis.core.dominio.posologia.Dosis
import cl.midosis.core.dominio.posologia.PosologiaInvalida
import cl.midosis.core.dominio.posologia.UnidadDeDosis
import cl.midosis.core.infraestructura.seguridad.ComunaNoResuelta
import cl.midosis.core.infraestructura.seguridad.ResolutorDeComuna
import cl.midosis.credencial.Via
import cl.midosis.motor.posologia.Frecuencia
import cl.midosis.motor.posologia.FrecuenciaInvalida
import java.math.BigDecimal
import java.time.Instant
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Emisión del código al cerrar la atención en el mesón (HU-01, HU-05). */
@RestController
@RequestMapping("/api/emisiones")
class EmisionControlador(
    private val emision: EmisionDeCodigos,
    private val revocacion: RevocacionDeCodigos,
    private val correccion: CorreccionDeCodigos,
) {

    data class LineaPeticion(
        val gtin: String?,
        val cantidad: BigDecimal?,
        val unidad: String?,
        val frecuencia: String?,
        val duracionIndicadaDias: Int?,
        val unidadesEntregadas: BigDecimal?,
        val via: String?,
        val indicaciones: String?,
    )

    data class ConsentimientoPeticion(val version: String?, val aceptadoEn: Instant?)

    data class Peticion(val lineas: List<LineaPeticion>?, val consentimiento: ConsentimientoPeticion?)

    data class Respuesta(val id: String, val codigo: String, val codigoCorto: String, val pin: String, val expiraEn: Instant) {
        companion object {
            fun de(c: CodigoEmitido) = Respuesta(c.id, c.codigo, c.codigoCorto, c.pin, c.expiraEn)
        }
    }

    @PostMapping
    fun emitir(@AuthenticationPrincipal jwt: Jwt, @RequestBody peticion: Peticion): ResponseEntity<Respuesta> {
        val emitido = emision.emitir(ResolutorDeComuna.desde(jwt), autor(jwt), solicitud(peticion.lineas, peticion.consentimiento))
        return ResponseEntity.status(HttpStatus.CREATED).body(Respuesta.de(emitido))
    }

    data class CorreccionPeticion(
        val motivo: String?,
        val instruccion: String?,
        val lineas: List<LineaPeticion>?,
        val consentimiento: ConsentimientoPeticion?,
    )

    /**
     * Corrección de un tratamiento ya cargado (HU-33): emite el código de reemplazo, ligado al
     * original, con el motivo y la instrucción para el paciente. Solo el químico farmacéutico.
     */
    @PostMapping("/{id}/correccion")
    fun corregir(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: String,
        @RequestBody peticion: CorreccionPeticion,
    ): ResponseEntity<Any> {
        val motivo = peticion.motivo ?: throw EmisionInvalida("indique el motivo de la corrección")
        val instruccion = CorreccionDeCodigos.Instruccion.de(peticion.instruccion)
        val r = correccion.corregir(
            ResolutorDeComuna.desde(jwt), id, autor(jwt), motivo, instruccion, solicitud(peticion.lineas, peticion.consentimiento),
        )
        return when (r) {
            is CorreccionDeCodigos.Resultado.Corregida -> ResponseEntity.status(HttpStatus.CREATED).body(Respuesta.de(r.reemplazo))
            CorreccionDeCodigos.Resultado.NoEncontrada -> ResponseEntity.notFound().build()
            CorreccionDeCodigos.Resultado.YaCorregida -> ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("error" to "Ese código ya se corrigió. Para cambiarlo otra vez, corrige el código de reemplazo."))
        }
    }

    private fun autor(jwt: Jwt): String =
        jwt.subject?.takeIf { it.isNotBlank() } ?: throw ComunaNoResuelta("el token no identifica a su usuario")

    private fun solicitud(lineas: List<LineaPeticion>?, c: ConsentimientoPeticion?): SolicitudDeEmision {
        val consentimiento = c ?: throw EmisionInvalida("sin consentimiento del paciente no se emite el código")
        return SolicitudDeEmision(
            lineas = lineas.orEmpty().map(::linea),
            consentimiento = Consentimiento(
                version = consentimiento.version ?: throw EmisionInvalida("falta la versión del consentimiento"),
                aceptadoEn = consentimiento.aceptadoEn ?: throw EmisionInvalida("falta cuándo se aceptó el consentimiento"),
            ),
        )
    }

    /** Las emisiones recientes de la comuna, para revocar una emitida por error (HU-07). */
    @GetMapping("/recientes")
    fun recientes(@AuthenticationPrincipal jwt: Jwt): List<RevocacionDeCodigos.Reciente> =
        revocacion.recientes(ResolutorDeComuna.desde(jwt))

    data class RevocacionPeticion(val motivo: String?)

    @PostMapping("/{id}/revocacion")
    fun revocar(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: String,
        @RequestBody peticion: RevocacionPeticion,
    ): ResponseEntity<Map<String, String>> {
        val motivo = peticion.motivo ?: throw EmisionInvalida("indique el motivo de la revocación")
        return when (revocacion.revocar(ResolutorDeComuna.desde(jwt), id, autor(jwt), motivo)) {
            RevocacionDeCodigos.Resultado.Revocada -> ResponseEntity.ok(mapOf("estado" to "revocada"))
            RevocacionDeCodigos.Resultado.NoEncontrada -> ResponseEntity.notFound().build()
            RevocacionDeCodigos.Resultado.YaRevocada ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "Ese código ya estaba revocado."))
            RevocacionDeCodigos.Resultado.Vencida ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "Ese código ya venció: no se puede canjear."))
        }
    }

    private fun linea(l: LineaPeticion) = LineaDeEmision(
        gtin = Gtin.de(l.gtin ?: throw EmisionInvalida("falta el código del producto")),
        dosis = Dosis(
            Dosis.normalizar(l.cantidad ?: throw EmisionInvalida("falta la cantidad por toma")),
            UnidadDeDosis.de(l.unidad ?: throw EmisionInvalida("falta la unidad de la dosis")),
        ),
        frecuencia = try {
            Frecuencia.de(l.frecuencia ?: throw EmisionInvalida("falta la frecuencia"))
        } catch (e: FrecuenciaInvalida) {
            throw EmisionInvalida("la frecuencia no es válida")
        },
        duracionIndicadaDias = l.duracionIndicadaDias ?: throw EmisionInvalida("falta la duración indicada"),
        unidadesEntregadas = l.unidadesEntregadas ?: throw EmisionInvalida("falta la cantidad entregada"),
        via = l.via?.let(Via::deCodigo) ?: throw EmisionInvalida("falta la vía de administración o no es válida"),
        indicaciones = l.indicaciones?.takeIf { it.isNotBlank() },
    )

    @ExceptionHandler(EmisionInvalida::class)
    fun invalida(e: EmisionInvalida): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "emisión inválida")))

    @ExceptionHandler(PosologiaInvalida::class)
    fun posologia(e: PosologiaInvalida): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "posología inválida")))

    /** Sin claves no se emite: la web lo informa y la entrega del medicamento sigue igual. */
    @ExceptionHandler(EmisionNoDisponible::class)
    fun noDisponible(e: EmisionNoDisponible): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(mapOf("error" to "La emisión de códigos no está disponible en este momento."))
}
