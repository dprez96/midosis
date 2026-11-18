package cl.midosis.core.aplicacion.emision

import cl.midosis.core.dominio.catalogo.CatalogoDeProductos
import cl.midosis.core.dominio.catalogo.Gtin
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
import tools.jackson.databind.json.JsonMapper

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
    private val catalogo: CatalogoDeProductos,
) {
    private val json = JsonMapper.builder().build()

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

        // Corrige solo el químico farmacéutico: su sesión valida la posología (HU-32).
        val reemplazo = emision.emitir(comuna, autor, solicitud, ahora, reemplazaA = original, autorEsQf = true)
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

    /** Una emisión encontrada por su referencia, para que el químico farmacéutico la reconozca. */
    data class Encontrada(
        val id: String,
        val emitidaEn: Instant,
        val productos: List<String>,
        val revocadaEn: Instant?,
        /** El jti del código que ya la corrigió, si la corrigieron. */
        val corregidaPor: String?,
    )

    /**
     * Las emisiones de la comuna cuya referencia es [referencia]: los últimos 8 caracteres del
     * jti, que van impresos en el comprobante y a la vista en la aplicación. Son 40 bits
     * aleatorios del ULID: en una comuna casi nunca se repiten, y si se repiten se muestran todas
     * con sus productos y su fecha para elegir. Sirve para corregir un tratamiento cuyo código
     * ya venció, que no aparece entre las emisiones recientes.
     */
    fun buscar(comuna: CodigoComuna, referencia: String): List<Encontrada> {
        val ref = normalizarReferencia(referencia) ?: throw EmisionInvalida("la referencia son 8 letras o números del comprobante")
        return contexto.en(comuna) {
            jdbc.sql(
                """
                SELECT e.id, e.emitida_en, e.revocada_en, e.productos, c.reemplazo
                FROM emision e LEFT JOIN correccion c ON c.original = e.id
                WHERE right(e.id, 8) = :ref ORDER BY e.emitida_en DESC LIMIT 10
                """.trimIndent(),
            )
                .param("ref", ref)
                .query { rs, _ ->
                    Encontrada(
                        id = rs.getString("id"),
                        emitidaEn = rs.getTimestamp("emitida_en").toInstant(),
                        productos = json.readTree(rs.getString("productos")).toList().map { it.get("gtin").asString() },
                        revocadaEn = rs.getTimestamp("revocada_en")?.toInstant(),
                        corregidaPor = rs.getString("reemplazo")?.trim(),
                    )
                }
                .list()
                .map { e -> e.copy(productos = e.productos.map { g -> catalogo.buscar(Gtin.de(g))?.nombre ?: g }) }
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

        /**
         * La referencia escrita por una persona, en mayúsculas y sin guiones ni espacios. Como
         * el alfabeto del ULID (Crockford), lee la I y la L como 1 y la O como 0. Null si no son
         * 8 caracteres válidos.
         */
        fun normalizarReferencia(texto: String): String? =
            texto.uppercase().filterNot { it == '-' || it.isWhitespace() }
                .map { when (it) { 'I', 'L' -> '1'; 'O' -> '0'; else -> it } }
                .joinToString("")
                .takeIf { it.length == 8 && it.all { c -> c in ALFABETO } }

        private const val ALFABETO = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

        const val MAXIMO_POR_CONSULTA = 20
    }
}
