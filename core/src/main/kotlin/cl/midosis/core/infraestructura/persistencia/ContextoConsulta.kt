package cl.midosis.core.infraestructura.persistencia

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/**
 * Contexto de la consulta de estado desde la aplicación del paciente (RF-B-15). Como en el
 * canje ([ContextoCanje]), quien pregunta no tiene cuenta ni comuna: la transacción declara el
 * jti en midosis.consulta y las políticas exponen solo esa emisión y su corrección.
 *
 * La variable es local a la transacción y desaparece al terminarla.
 */
@Component
class ContextoConsulta(
    private val jdbc: JdbcClient,
    private val transaccion: TransactionTemplate,
) {
    fun <T> en(id: String, bloque: () -> T): T {
        @Suppress("UNCHECKED_CAST")
        return transaccion.execute {
            jdbc.sql("SELECT set_config('midosis.consulta', :id, true)")
                .param("id", id)
                .query(String::class.java)
                .single()
            bloque()
        } as T
    }
}
