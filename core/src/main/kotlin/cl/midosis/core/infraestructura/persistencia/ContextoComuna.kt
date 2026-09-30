package cl.midosis.core.infraestructura.persistencia

import cl.midosis.core.dominio.comuna.CodigoComuna
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/**
 * Único camino para leer o escribir datos de una comuna.
 *
 * Abre una transacción y fija en ella la variable de sesión midosis.comuna, que usan
 * las políticas de seguridad a nivel de fila (ADR-011). La variable es local a la
 * transacción: PostgreSQL la descarta al terminarla, así que una conexión que vuelve
 * al pool no conserva la comuna anterior, aunque nadie la restablezca.
 *
 * Fuera de este contexto las políticas no dejan ver ninguna fila ni escribir ninguna.
 */
@Component
class ContextoComuna(
    private val jdbc: JdbcClient,
    private val transaccion: TransactionTemplate,
) {

    fun <T> en(comuna: CodigoComuna, bloque: () -> T): T {
        @Suppress("UNCHECKED_CAST")
        return transaccion.execute {
            jdbc.sql("SELECT set_config('midosis.comuna', :comuna, true)")
                .param("comuna", comuna.valor)
                .query(String::class.java)
                .single()
            bloque()
        } as T
    }
}
