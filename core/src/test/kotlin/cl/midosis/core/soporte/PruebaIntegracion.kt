package cl.midosis.core.soporte

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.Date

/**
 * Base de las pruebas de integración: un PostgreSQL real y tokens firmados de verdad.
 *
 * La aplicación se conecta como midosis_app, sin privilegios de dueño. Si se usara la
 * conexión automática de Testcontainers se conectaría como superusuario, que se salta
 * la seguridad a nivel de fila, y todas las pruebas de aislamiento pasarían sin probar
 * nada. Las migraciones sí corren con el superusuario, como dueño de las tablas.
 *
 * El pool queda en una sola conexión, para que las pruebas de reutilización de
 * conexiones prueben de verdad la misma conexión.
 */
@SpringBootTest
@Import(PruebaIntegracion.Claves::class)
abstract class PruebaIntegracion {

    companion object {
        val postgres: PostgreSQLContainer = PostgreSQLContainer("postgres:16-alpine")
            .withInitScript("sql/rol-aplicacion.sql")
            .also { it.start() }

        /** Par de claves solo para las pruebas: se genera en memoria y nunca se guarda. */
        val clave: RSAKey = RSAKeyGenerator(2048).keyID("pruebas").generate()

        /** Una clave distinta, para firmar tokens que la aplicación debe rechazar. */
        val claveAjena: RSAKey = RSAKeyGenerator(2048).keyID("ajena").generate()

        @JvmStatic
        @DynamicPropertySource
        fun propiedades(registro: DynamicPropertyRegistry) {
            registro.add("spring.datasource.url", postgres::getJdbcUrl)
            registro.add("spring.datasource.username") { "midosis_app" }
            registro.add("spring.datasource.password") { "app_pruebas" }
            registro.add("spring.datasource.hikari.maximum-pool-size") { 1 }
            registro.add("spring.flyway.url", postgres::getJdbcUrl)
            registro.add("spring.flyway.user", postgres::getUsername)
            registro.add("spring.flyway.password", postgres::getPassword)
        }

        /** Acceso como superusuario, solo para preparar datos y revisar lo registrado. */
        val administrador: JdbcTemplate by lazy {
            JdbcTemplate(DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password))
        }

        /** Vacía todas las tablas con datos de comunas. Una tabla nueva se agrega aquí. */
        fun limpiarDatos() {
            administrador.execute("TRUNCATE plantilla_posologia, producto, evento_seguridad")
        }

        /** Token firmado con el perfil indicado. Por defecto, un auxiliar de farmacia. */
        fun token(
            comuna: String?,
            firma: RSAKey = clave,
            rol: String? = "auxiliar",
            sujeto: String = "usuario-de-prueba",
        ): String {
            val claims = JWTClaimsSet.Builder()
                .subject(sujeto)
                .issueTime(Date())
                .expirationTime(Date(System.currentTimeMillis() + 600_000))
                .apply { if (comuna != null) claim("comuna", comuna) }
                .apply { if (rol != null) claim("rol", rol) }
                .build()
            val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(firma.keyID).build(), claims)
            jwt.sign(RSASSASigner(firma))
            return jwt.serialize()
        }
    }

    @TestConfiguration
    class Claves {
        @Bean
        fun jwtDecoder(): JwtDecoder = NimbusJwtDecoder.withPublicKey(clave.toRSAPublicKey()).build()
    }
}
