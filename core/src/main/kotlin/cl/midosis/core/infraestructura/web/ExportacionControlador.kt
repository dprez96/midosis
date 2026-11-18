package cl.midosis.core.infraestructura.web

import cl.midosis.core.aplicacion.exportacion.ExportacionDeComuna
import cl.midosis.core.infraestructura.seguridad.ResolutorDeComuna
import java.time.LocalDate
import java.time.ZoneId
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Exportación de los datos de la comuna (HU-29): solo el administrador comunal y solo su comuna,
 * que sale del token. Si la petición declara otra comuna, el filtro de manipulación la rechaza y
 * la registra antes de llegar aquí (HU-28).
 */
@RestController
class ExportacionControlador(private val exportacion: ExportacionDeComuna) {

    @GetMapping("/api/exportacion")
    fun exportar(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<ByteArray> {
        val comuna = ResolutorDeComuna.desde(jwt)
        val zip = exportacion.exportar(comuna, jwt.subject ?: "desconocido")
        val nombre = "midosis-${comuna.valor}-${LocalDate.now(ZoneId.of("America/Santiago"))}.zip"
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/zip"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$nombre\"")
            .body(zip)
    }
}
