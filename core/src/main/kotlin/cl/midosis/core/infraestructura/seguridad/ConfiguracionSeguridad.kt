package cl.midosis.core.infraestructura.seguridad

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain

/**
 * API sin sesión: cada petición trae su token Bearer firmado. Por eso no hay protección
 * CSRF, que solo tiene sentido cuando el navegador envía credenciales por su cuenta.
 */
@Configuration
class ConfiguracionSeguridad {

    @Bean
    fun cadenaDeSeguridad(http: HttpSecurity, registro: RegistroEventosSeguridad): SecurityFilterChain =
        http
            .authorizeHttpRequests {
                it.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                it.anyRequest().authenticated()
            }
            .oauth2ResourceServer { it.jwt(Customizer.withDefaults()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .csrf { it.disable() }
            // El filtro se crea aquí y no como componente: Spring Boot registraría un
            // componente también como filtro del servidor, y correría dos veces.
            .addFilterAfter(FiltroManipulacionComuna(registro), BearerTokenAuthenticationFilter::class.java)
            .build()
}
