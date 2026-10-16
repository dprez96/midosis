package cl.midosis.core.infraestructura.persistencia

import cl.midosis.core.dominio.emision.Consentimiento
import cl.midosis.credencial.Carga
import java.sql.Timestamp
import java.time.Instant
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

    fun registrar(carga: Carga, autor: String, kid: String, consentimiento: Consentimiento) {
        // Por producto, solo lo que sirve para auditar y proyectar la demanda: nada del paciente.
        val productos = json.writeValueAsString(
            carga.productos.map {
                mapOf("gtin" to it.gtin, "du" to it.duracionIndicadaDias, "e" to it.dosisEntregadas, "co" to it.coberturaDias)
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
}
