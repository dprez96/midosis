package cl.midosis.core.infraestructura.persistencia

import cl.midosis.credencial.CodigoCorto
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/**
 * Contexto del canje con código corto (HU-10). Quien canjea no tiene cuenta ni comuna, así que
 * no puede usar [ContextoComuna]. La transacción declara el código corto en midosis.canje y la
 * política de la tabla sobre expone solo esa fila: nada más es visible.
 *
 * Igual que la comuna, la variable es local a la transacción y desaparece al terminarla.
 */
@Component
class ContextoCanje(
    private val jdbc: JdbcClient,
    private val transaccion: TransactionTemplate,
) {
    fun <T> en(codigo: CodigoCorto, bloque: () -> T): T {
        @Suppress("UNCHECKED_CAST")
        return transaccion.execute {
            jdbc.sql("SELECT set_config('midosis.canje', :codigo, true)")
                .param("codigo", codigo.valor)
                .query(String::class.java)
                .single()
            bloque()
        } as T
    }
}
