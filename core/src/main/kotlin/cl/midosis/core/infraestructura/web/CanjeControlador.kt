package cl.midosis.core.infraestructura.web

import cl.midosis.core.aplicacion.canje.CanjeDeCodigos
import cl.midosis.core.dominio.canje.Pin
import cl.midosis.core.dominio.canje.ResultadoDeCanje
import cl.midosis.credencial.CodigoCorto
import java.time.Duration
import java.time.Instant
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Canje con código corto y PIN (HU-10). Es la única ruta sin token: la usa el paciente, que
 * no tiene cuenta (RF-M-01). Todo intento cuenta para la limitación de tasa, y las respuestas
 * no dicen nada del tratamiento ni de la comuna.
 */
@RestController
@RequestMapping("/api/canje")
class CanjeControlador(private val canje: CanjeDeCodigos) {

    data class Solicitud(val codigo: String?, val pin: String?)

    @PostMapping
    fun canjear(@RequestBody solicitud: Solicitud): ResponseEntity<Map<String, Any>> {
        val codigo = solicitud.codigo?.let(CodigoCorto::de)
        val pin = solicitud.pin?.let(Pin::de)
        if (codigo == null || pin == null) {
            return error(HttpStatus.BAD_REQUEST, "Revisa el código de 10 caracteres y el PIN de 4 dígitos.")
        }
        return when (val r = canje.canjear(codigo, pin)) {
            is ResultadoDeCanje.Canjeado -> ResponseEntity.ok(mapOf("codigo" to r.codigo))
            ResultadoDeCanje.NoEncontrado ->
                error(HttpStatus.NOT_FOUND, "Ese código no existe, ya se usó o venció. Pide uno nuevo en la farmacia.")
            is ResultadoDeCanje.PinIncorrecto -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(
                mapOf("error" to "El PIN no corresponde a ese código.", "intentosRestantes" to r.intentosRestantes),
            )
            is ResultadoDeCanje.DemasiadosIntentos -> {
                val segundos = Duration.between(Instant.now(), r.reintentarEn).seconds.coerceAtLeast(1)
                ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, segundos.toString())
                    .body(mapOf("error" to "Demasiados intentos. Espera unos minutos y vuelve a probar."))
            }
            ResultadoDeCanje.Bloqueado ->
                error(HttpStatus.LOCKED, "Ese código se bloqueó tras 10 intentos. Pide en la farmacia que te lo emitan de nuevo.")
        }
    }

    private fun error(estado: HttpStatus, mensaje: String): ResponseEntity<Map<String, Any>> =
        ResponseEntity.status(estado).body(mapOf("error" to mensaje))
}
