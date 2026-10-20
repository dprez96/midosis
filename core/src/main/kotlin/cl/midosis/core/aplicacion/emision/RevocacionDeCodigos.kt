package cl.midosis.core.aplicacion.emision

import cl.midosis.core.dominio.canje.RepositorioDeSobres
import cl.midosis.core.dominio.catalogo.CatalogoDeProductos
import cl.midosis.core.dominio.catalogo.Gtin
import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.core.dominio.emision.EmisionInvalida
import cl.midosis.core.infraestructura.persistencia.ContextoComuna
import java.sql.Timestamp
import java.time.Instant
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper

/**
 * Revocación de un código emitido por error (HU-07): queda invalidado para el canje en línea y
 * la revocación queda registrada con quién, cuándo y por qué.
 *
 * El QR impreso se verifica sin conexión y no se puede «desimprimir»: si el paciente ya lo
 * escaneó, lo corrige el código de reemplazo de la HU-33. Este es el alcance declarado.
 */
@Service
class RevocacionDeCodigos(
    private val contexto: ContextoComuna,
    private val jdbc: JdbcClient,
    private val sobres: RepositorioDeSobres,
    private val catalogo: CatalogoDeProductos,
) {
    private val json = JsonMapper.builder().build()

    data class Reciente(
        val id: String,
        val emitidaEn: Instant,
        val expiraEn: Instant,
        val productos: List<String>,
        val revocadaEn: Instant?,
    )

    sealed interface Resultado {
        data object Revocada : Resultado
        data object NoEncontrada : Resultado
        data object YaRevocada : Resultado
        data object Vencida : Resultado
    }

    /** Las emisiones de la comuna que todavía se pueden canjear o que se revocaron hace poco. */
    fun recientes(comuna: CodigoComuna, ahora: Instant = Instant.now()): List<Reciente> = contexto.en(comuna) {
        jdbc.sql(
            """
            SELECT id, emitida_en, expira_en, revocada_en, productos FROM emision
            WHERE expira_en > :ahora ORDER BY emitida_en DESC LIMIT 50
            """.trimIndent(),
        )
            .param("ahora", Timestamp.from(ahora))
            .query { rs, _ ->
                val gtins = json.readTree(rs.getString("productos")).toList().map { it.get("gtin").asString() }
                Reciente(
                    id = rs.getString("id"),
                    emitidaEn = rs.getTimestamp("emitida_en").toInstant(),
                    expiraEn = rs.getTimestamp("expira_en").toInstant(),
                    productos = gtins,
                    revocadaEn = rs.getTimestamp("revocada_en")?.toInstant(),
                )
            }
            .list()
            .map { r -> r.copy(productos = r.productos.map { g -> catalogo.buscar(Gtin.de(g))?.nombre ?: g }) }
    }

    fun revocar(comuna: CodigoComuna, id: String, autor: String, motivo: String, ahora: Instant = Instant.now()): Resultado {
        val limpio = motivo.filterNot { Character.getType(it) == Character.CONTROL.toInt() }.trim()
        if (limpio.length !in 3..200) throw EmisionInvalida("indique el motivo de la revocación, de 3 a 200 caracteres")
        return contexto.en(comuna) {
            val actualizadas = jdbc.sql(
                """
                UPDATE emision SET revocada_en = :ahora, revocada_por = :autor, motivo_revocacion = :motivo
                WHERE id = :id AND revocada_en IS NULL AND expira_en > :ahora
                """.trimIndent(),
            )
                .param("ahora", Timestamp.from(ahora))
                .param("autor", autor)
                .param("motivo", limpio)
                .param("id", id)
                .update()
            if (actualizadas == 1) {
                sobres.destruirDeEmision(id)
                Resultado.Revocada
            } else {
                estadoDe(id, ahora)
            }
        }
    }

    /** Por qué no se pudo revocar. Una emisión de otra comuna no se ve: responde como inexistente. */
    private fun estadoDe(id: String, ahora: Instant): Resultado {
        val fila = jdbc.sql("SELECT revocada_en, expira_en FROM emision WHERE id = :id")
            .param("id", id)
            .query { rs, _ -> rs.getTimestamp("revocada_en") to rs.getTimestamp("expira_en").toInstant() }
            .optional()
            .orElse(null) ?: return Resultado.NoEncontrada
        return when {
            fila.first != null -> Resultado.YaRevocada
            !ahora.isBefore(fila.second) -> Resultado.Vencida
            else -> Resultado.NoEncontrada
        }
    }
}
