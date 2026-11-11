package cl.midosis.core.infraestructura.persistencia

import cl.midosis.core.dominio.demanda.Retiro
import cl.midosis.core.dominio.emision.Consentimiento
import cl.midosis.credencial.Carga
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.time.ZoneId
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper

/**
 * El registro seudonimizado de cada emisión (sección 7.7). Solo dentro del contexto de la comuna:
 * la política de la tabla hace el resto.
 */
@Repository
class EmisionesJdbc(private val jdbc: JdbcClient) {

    private val json = JsonMapper.builder().build()

    /**
     * [unidades] son las unidades entregadas de cada GTIN (comprimidos, ml): lo que la farmacia
     * compra, y con lo que se proyecta la demanda (HU-27). Las emisiones anteriores no las
     * guardaban; para ellas se usan las dosis entregadas.
     */
    fun registrar(carga: Carga, autor: String, kid: String, consentimiento: Consentimiento, unidades: Map<String, BigDecimal> = emptyMap()) {
        // Por producto, solo lo que sirve para auditar y proyectar la demanda: nada del paciente.
        val productos = json.writeValueAsString(
            carga.productos.map {
                mapOf("gtin" to it.gtin, "du" to it.duracionIndicadaDias, "e" to it.dosisEntregadas, "co" to it.coberturaDias) +
                    (unidades[it.gtin]?.let { u -> mapOf("u" to u) } ?: emptyMap())
            },
        )
        jdbc.sql(
            """
            INSERT INTO emision (id, comuna, punto, emitida_en, expira_en, emitida_por, kid,
                                 consentimiento_version, consentimiento_en, productos)
            VALUES (:id, :comuna, :punto, :emitida, :expira, :autor, :kid, :version, :aceptado, CAST(:productos AS jsonb))
            """.trimIndent(),
        )
            .param("id", carga.id)
            .param("comuna", carga.comuna)
            .param("punto", carga.emisor)
            .param("emitida", Timestamp.from(Instant.ofEpochSecond(carga.emitidoEn)))
            .param("expira", Timestamp.from(Instant.ofEpochSecond(carga.expiraEn)))
            .param("autor", autor)
            .param("kid", kid)
            .param("version", consentimiento.version)
            .param("aceptado", Timestamp.from(consentimiento.aceptadoEn))
            .param("productos", productos)
            .update()
    }

    /**
     * Los retiros de la comuna desde [desde], sin los revocados: un código revocado no fue un
     * retiro (HU-07). Para la proyección de la demanda (HU-27).
     */
    fun retiros(desde: Instant, zona: ZoneId): List<Retiro> =
        jdbc.sql("SELECT emitida_en, productos FROM emision WHERE emitida_en >= :desde AND revocada_en IS NULL")
            .param("desde", Timestamp.from(desde))
            .query { rs, _ ->
                val dia = rs.getTimestamp("emitida_en").toInstant().atZone(zona).toLocalDate()
                json.readTree(rs.getString("productos")).toList().map { p ->
                    Retiro(
                        gtin = p.get("gtin").asString(),
                        dia = dia,
                        duracionIndicadaDias = p.get("du").asInt(),
                        coberturaDias = p.get("co").asInt(),
                        unidades = (p.get("u") ?: p.get("e")).asDouble(),
                    )
                }
            }
            .list()
            .flatten()
}
