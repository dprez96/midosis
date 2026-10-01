package cl.midosis.core.infraestructura.seguridad

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain

/**
 * API sin sesión: cada petición trae su token Bearer firmado.
 *
 * La protección CSRF queda activa, con su configuración por defecto. El servidor de
 * recursos de Spring ya la omite en las peticiones con token Bearer: el ataque CSRF
 * aprovecha credenciales que el navegador envía solo, como las cookies, y un token en la
 * cabecera Authorization solo lo pone quien lo tiene. No se deshabilita del todo, para
 * que un acceso por cookies que se agregue mañana nazca protegido.
 */
@Configuration
class ConfiguracionSeguridad {

    @Bean
    fun cadenaDeSeguridad(http: HttpSecurity, registro: RegistroEventosSeguridad): SecurityFilterChain =
        http
            .authorizeHttpRequests {
                it.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                // Las plantillas son contenido clínico de la comuna: solo el químico
                // farmacéutico las crea (HU-03). Consultarlas puede cualquier perfil.
                it.requestMatchers(HttpMethod.POST, "/api/catalogo/productos/*/plantillas").hasAuthority(AUTORIDAD_QF)
                it.anyRequest().authenticated()
            }
            .oauth2ResourceServer { servidor -> servidor.jwt { it.jwtAuthenticationConverter(rolesDelToken()) } }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // El filtro se crea aquí y no como componente: Spring Boot registraría un
            // componente también como filtro del servidor, y correría dos veces.
            .addFilterAfter(FiltroManipulacionComuna(registro), BearerTokenAuthenticationFilter::class.java)
            .build()

    /**
     * El rol sale únicamente del token firmado, del claim «rol», y se convierte en la
     * autoridad ROL_<rol>: ROL_auxiliar, ROL_qf o ROL_administrador. Un token sin rol no
     * tiene ninguna.
     */
    private fun rolesDelToken(): JwtAuthenticationConverter {
        val roles = JwtGrantedAuthoritiesConverter().apply {
            setAuthoritiesClaimName(CLAIM_ROL)
            setAuthorityPrefix(PREFIJO_ROL)
        }
        return JwtAuthenticationConverter().apply { setJwtGrantedAuthoritiesConverter(roles) }
    }

    companion object {
        const val CLAIM_ROL = "rol"
        const val PREFIJO_ROL = "ROL_"
        const val AUTORIDAD_QF = "${PREFIJO_ROL}qf"
    }
}
