package cl.midosis.core.infraestructura.seguridad

import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.core.dominio.comuna.ComunaInvalida
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

/**
 * La comuna de quien hace la petición sale únicamente del token firmado, del claim
 * «comuna». Nunca de un parámetro, una cabecera o la ruta: esos los controla el cliente.
 */
object ResolutorDeComuna {

    const val CLAIM = "comuna"

    fun desde(jwt: Jwt): CodigoComuna {
        val valor = jwt.getClaimAsString(CLAIM)
            ?: throw ComunaNoResuelta("el token no declara una comuna")
        return try {
            CodigoComuna.de(valor)
        } catch (e: ComunaInvalida) {
            throw ComunaNoResuelta("el token declara una comuna con formato inválido")
        }
    }

    fun desde(autenticacion: Authentication?): CodigoComuna {
        val token = autenticacion as? JwtAuthenticationToken
            ?: throw ComunaNoResuelta("la petición no está autenticada con un token")
        return desde(token.token)
    }
}

class ComunaNoResuelta(mensaje: String) : RuntimeException(mensaje)
