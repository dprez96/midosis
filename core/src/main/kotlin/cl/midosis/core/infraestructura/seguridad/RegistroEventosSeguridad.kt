package cl.midosis.core.infraestructura.seguridad

import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.core.infraestructura.persistencia.ContextoComuna
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component

/**
 * Registra los intentos de cruzar comunas y otros hechos de seguridad, en la bitácora
 * y en la tabla evento_seguridad, que la aplicación puede escribir pero no leer.
 */
@Component
class RegistroEventosSeguridad(
    private val contexto: ContextoComuna,
    private val jdbc: JdbcClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun registrar(comuna: CodigoComuna, tipo: TipoEventoSeguridad, detalle: String) {
        val limpio = sanear(detalle)
        log.warn("evento de seguridad: comuna={} tipo={} detalle={}", comuna.valor, tipo.name, limpio)
        contexto.en(comuna) {
            jdbc.sql("INSERT INTO evento_seguridad (comuna, tipo, detalle) VALUES (:comuna, :tipo, :detalle)")
                .param("comuna", comuna.valor)
                .param("tipo", tipo.name)
                .param("detalle", limpio)
                .update()
        }
    }

    /**
     * El detalle incluye valores que manda el cliente. Se acota y se le quitan los
     * caracteres de control, para que nadie pueda falsificar líneas en la bitácora.
     */
    private fun sanear(texto: String): String =
        texto.filter { !it.isISOControl() }.take(300)
}

enum class TipoEventoSeguridad {
    /** La petición indicaba una comuna distinta a la del token. */
    COMUNA_MANIPULADA,
}
