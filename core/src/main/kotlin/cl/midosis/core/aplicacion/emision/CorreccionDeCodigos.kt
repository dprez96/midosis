package cl.midosis.core.aplicacion.emision

import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.core.dominio.emision.CodigoEmitido
import cl.midosis.core.dominio.emision.EmisionInvalida
import cl.midosis.core.dominio.emision.SolicitudDeEmision
import cl.midosis.core.infraestructura.persistencia.ContextoComuna
import cl.midosis.core.infraestructura.persistencia.ContextoConsulta
import java.sql.Timestamp
import java.time.Instant
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service

/**
 * Corrección de un tratamiento ya cargado (HU-33, RN-25) y su consulta desde el teléfono
 * (RF-B-15).
 *
 * El QR impreso se verifica sin conexión y no se puede retirar del teléfono: por eso la
 * corrección no cambia el código original, sino que emite uno de reemplazo que trae en «rp» el
 * jti del original. La aplicación, al consultar el estado, se entera de la corrección y de la
 * instrucción del químico farmacéutico, y al cargar el reemplazo sustituye el tratamiento.
 */
@Service
class CorreccionDeCodigos(
    private val contexto: ContextoComuna,
    private val consulta: ContextoConsulta,
    private val jdbc: JdbcClient,
    private val emision: EmisionDeCodigos,
) {
    /** Qué hace el paciente con el tratamiento anterior hasta cargar el reemplazo. */
    enum class Instruccion(val codigo: String) {
        CONTINUAR("continuar"),
        SUSPENDER("suspender"),
        ;

        companion object {
            fun de(codigo: String?): Instruccion =
                entries.firstOrNull { it.codigo == codigo } ?: throw EmisionInvalida("indique si el paciente debe continuar o suspender")
        }
    }

    sealed interface Resultado {
        data class Corregida(val reemplazo: CodigoEmitido) : Resultado
        data object NoEncontrada : Resultado
        data object YaCorregida : Resultado
    }

    fun corregir(
        comuna: CodigoComuna,
        original: String,
        autor: String,
        motivo: String,
        instruccion: Instruccion,
        solicitud: SolicitudDeEmision,
        ahora: Instant = Instant.now(),
    ): Resultado {
        val limpio = motivo.filterNot { Character.getType(it) == Character.CONTROL.toInt() }.trim()
        if (limpio.length !in 3..200) throw EmisionInvalida("indique el motivo de la corrección, de 3 a 200 caracteres")

        // Una emisión de otra comuna no se ve: responde como inexistente.
        val existente = contexto.en(comuna) {
            jdbc.sql("SELECT (SELECT count(*) FROM correccion WHERE original = :id) FROM emision WHERE id = :id")
                .param("id", original)
                .query(Long::class.java)
                .optional()
                .orElse(null)
        } ?: return Resultado.NoEncontrada
        if (existente > 0) return Resultado.YaCorregida

        val reemplazo = emision.emitir(comuna, autor, solicitud, ahora, reemplazaA = original)
        return try {
            contexto.en(comuna) {
                jdbc.sql(
                    """
                    INSERT INTO correccion (original, reemplazo, comuna, motivo, instruccion, corregida_por, corregida_en)
                    VALUES (:original, :reemplazo, :comuna, :motivo, :instruccion, :autor, :ahora)
                    """.trimIndent(),
                )
                    .param("original", original)
                    .param("reemplazo", reemplazo.id)
                    .param("comuna", comuna.valor)
                    .param("motivo", limpio)
                    .param("instruccion", instruccion.codigo)
                    .param("autor", autor)
                    .param("ahora", Timestamp.from(ahora))
                    .update()
            }
            Resultado.Corregida(reemplazo)
        } catch (e: DuplicateKeyException) {
            // Otro químico farmacéutico corrigió el mismo código al mismo tiempo.
            Resultado.YaCorregida
        }
    }

    /** Lo que la aplicación sabe de un código suyo. No dice nada del tratamiento ni de la comuna. */
    data class Estado(val id: String, val estado: String, val instruccion: String? = null)

    /**
     * El estado de cada jti: vigente, corregido (con la instrucción), revocado o desconocido.
     * La corrección prevalece: un código corregido y después revocado sigue informando la
     * instrucción del químico farmacéutico.
     */
    fun estados(ids: List<String>): List<Estado> = ids.distinct().map { id ->
        consulta.en(id) {
            jdbc.sql(
                """
                SELECT e.revocada_en IS NOT NULL AS revocada, c.instruccion
                FROM emision e LEFT JOIN correccion c ON c.original = e.id
                WHERE e.id = :id
                """.trimIndent(),
            )
                .param("id", id)
                .query { rs, _ ->
                    val instruccion = rs.getString("instruccion")
                    when {
                        instruccion != null -> Estado(id, "corregido", instruccion)
                        rs.getBoolean("revocada") -> Estado(id, "revocado")
                        else -> Estado(id, "vigente")
                    }
                }
                .optional()
                .orElse(Estado(id, "desconocido"))
        }
    }

    companion object {
        val ULID = Regex("^[0-9A-HJKMNP-TV-Z]{26}$")
        const val MAXIMO_POR_CONSULTA = 20
    }
}
