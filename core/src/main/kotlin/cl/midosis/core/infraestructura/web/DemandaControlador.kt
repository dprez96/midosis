package cl.midosis.core.infraestructura.web

import cl.midosis.core.aplicacion.demanda.DemandaDeRetiros
import cl.midosis.core.infraestructura.seguridad.ResolutorDeComuna
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** La proyección de la demanda de retiros (HU-27). Solo el químico farmacéutico. */
@RestController
class DemandaControlador(private val demanda: DemandaDeRetiros) {

    @GetMapping("/api/demanda")
    fun demanda(@AuthenticationPrincipal jwt: Jwt): DemandaDeRetiros.Demanda = demanda.calcular(ResolutorDeComuna.desde(jwt))
}
