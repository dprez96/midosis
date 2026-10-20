package cl.midosis.core.infraestructura.persistencia

import cl.midosis.core.dominio.canje.RepositorioDeSobres
import cl.midosis.core.dominio.canje.Sobre
import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.credencial.CodigoCorto
import java.sql.Timestamp
import java.time.Instant
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * Sobres sobre PostgreSQL. Como en el catálogo, ninguna consulta filtra por comuna: lo que se
 * ve lo decide la política de la tabla, según el contexto de la transacción.
 */
@Repository
class SobresJdbc(private val jdbc: JdbcClient) : RepositorioDeSobres {

    private val fila = RowMapper { rs, _ ->
        Sobre(
            codigoCorto = CodigoCorto.de(rs.getString("codigo_corto")) ?: error("código corto inválido en la base"),
            comuna = CodigoComuna.de(rs.getString("comuna")),
            codigo = rs.getString("codigo"),
            pinDerivado = rs.getBytes("pin_derivado"),
            pinSal = rs.getBytes("pin_sal"),
            expiraEn = rs.getTimestamp("expira_en").toInstant(),
            intentosVentana = rs.getInt("intentos_ventana"),
            ventanaDesde = rs.getTimestamp("ventana_desde")?.toInstant(),
            fallidos = rs.getInt("fallidos"),
            emision = rs.getString("emision"),
        )
    }

    override fun guardar(sobre: Sobre) {
        jdbc.sql(
            """
            INSERT INTO sobre (codigo_corto, comuna, codigo, pin_derivado, pin_sal, expira_en, emision)
            VALUES (:codigoCorto, :comuna, :codigo, :pinDerivado, :pinSal, :expiraEn, :emision)
            """.trimIndent(),
        )
            .param("codigoCorto", sobre.codigoCorto.valor)
            .param("comuna", sobre.comuna.valor)
            .param("codigo", sobre.codigo)
            .param("pinDerivado", sobre.pinDerivado)
            .param("pinSal", sobre.pinSal)
            .param("expiraEn", Timestamp.from(sobre.expiraEn))
            .param("emision", sobre.emision)
            .update()
    }

    override fun buscarParaCanje(codigo: CodigoCorto): Sobre? =
        jdbc.sql("SELECT * FROM sobre WHERE codigo_corto = :codigo FOR UPDATE")
            .param("codigo", codigo.valor)
            .query(fila)
            .optional()
            .orElse(null)

    override fun registrarIntento(codigo: CodigoCorto, intentosVentana: Int, ventanaDesde: Instant, fallidos: Int) {
        jdbc.sql(
            """
            UPDATE sobre SET intentos_ventana = :intentos, ventana_desde = :desde, fallidos = :fallidos
            WHERE codigo_corto = :codigo
            """.trimIndent(),
        )
            .param("intentos", intentosVentana)
            .param("desde", Timestamp.from(ventanaDesde))
            .param("fallidos", fallidos)
            .param("codigo", codigo.valor)
            .update()
    }

    override fun destruirDeEmision(emision: String): Int =
        jdbc.sql("DELETE FROM sobre WHERE emision = :emision").param("emision", emision).update()

    override fun destruir(codigo: CodigoCorto) {
        jdbc.sql("DELETE FROM sobre WHERE codigo_corto = :codigo").param("codigo", codigo.valor).update()
    }
}
