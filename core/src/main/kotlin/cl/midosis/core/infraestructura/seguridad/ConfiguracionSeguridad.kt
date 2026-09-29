package cl.midosis.core.infraestructura.seguridad

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
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
                it.anyRequest().authenticated()
            }
            .oauth2ResourceServer { it.jwt(Customizer.withDefaults()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // El filtro se crea aquí y no como componente: Spring Boot registraría un
            // componente también como filtro del servidor, y correría dos veces.
            .addFilterAfter(FiltroManipulacionComuna(registro), BearerTokenAuthenticationFilter::class.java)
            .build()
}
