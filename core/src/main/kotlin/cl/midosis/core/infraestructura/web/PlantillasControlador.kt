package cl.midosis.core.infraestructura.web

import cl.midosis.core.aplicacion.posologia.GestionDePlantillas
import cl.midosis.core.dominio.catalogo.Gtin
import cl.midosis.core.dominio.posologia.NuevaPlantilla
import cl.midosis.core.dominio.posologia.PlantillaDePosologia
import cl.midosis.core.dominio.posologia.PosologiaInvalida
import cl.midosis.core.infraestructura.seguridad.ComunaNoResuelta
import cl.midosis.core.infraestructura.seguridad.ResolutorDeComuna
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

/** Plantillas de posología frecuente de un producto de la comuna de quien consulta (HU-03). */
@RestController
@RequestMapping("/api/catalogo/productos/{gtin}/plantillas")
class PlantillasControlador(private val plantillas: GestionDePlantillas) {

    @GetMapping
    fun listar(@AuthenticationPrincipal jwt: Jwt, @PathVariable gtin: String): List<PlantillaRespuesta> =
        plantillas.listar(ResolutorDeComuna.desde(jwt), Gtin.de(gtin)).map(PlantillaRespuesta::de)

    /** Solo el químico farmacéutico: la regla está en ConfiguracionSeguridad. */
    @PostMapping
    fun crear(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable gtin: String,
        @RequestBody peticion: NuevaPlantillaPeticion,
    ): ResponseEntity<PlantillaRespuesta> {
        // Queda registrado quién la creó por su identificador en Firebase, no por su nombre.
        val autor = jwt.subject?.takeIf { it.isNotBlank() }
            ?: throw ComunaNoResuelta("el token no identifica a su usuario")
        val creada = plantillas.crear(ResolutorDeComuna.desde(jwt), peticion.aNueva(Gtin.de(gtin)), autor)
        return ResponseEntity.status(HttpStatus.CREATED).body(PlantillaRespuesta.de(creada))
    }
}

/** Los campos llegan anulables para responder qué falta, en vez de un error de formato. */
data class NuevaPlantillaPeticion(
    val cantidad: BigDecimal?,
    val unidad: String?,
    val frecuencia: String?,
    val duracionDias: Int?,
    val indicaciones: String?,
) {
    fun aNueva(gtin: Gtin) = NuevaPlantilla.de(
        gtin = gtin,
        cantidad = cantidad ?: throw PosologiaInvalida("falta la cantidad por toma"),
        unidad = unidad ?: throw PosologiaInvalida("falta la unidad de la dosis"),
        frecuencia = frecuencia ?: throw PosologiaInvalida("falta la frecuencia"),
        duracionDias = duracionDias,
        indicaciones = indicaciones,
    )
}

data class PlantillaRespuesta(
    val id: String,
    val cantidad: BigDecimal,
    val unidad: String,
    val frecuencia: String,
    val duracionDias: Int?,
    val indicaciones: String?,
) {
    companion object {
        fun de(p: PlantillaDePosologia) = PlantillaRespuesta(
            id = p.id.toString(),
            cantidad = p.dosis.cantidad,
            unidad = p.dosis.unidad.codigo,
            frecuencia = p.frecuencia.toString(),
            duracionDias = p.duracionDias,
            indicaciones = p.indicaciones,
        )
    }
}
