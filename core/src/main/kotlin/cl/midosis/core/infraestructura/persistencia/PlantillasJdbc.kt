package cl.midosis.core.infraestructura.persistencia

import cl.midosis.core.dominio.catalogo.Gtin
import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.core.dominio.posologia.Dosis
import cl.midosis.core.dominio.posologia.NuevaPlantilla
import cl.midosis.core.dominio.posologia.PlantillaDePosologia
import cl.midosis.core.dominio.posologia.PlantillasDePosologia
import cl.midosis.core.dominio.posologia.UnidadDeDosis
import cl.midosis.motor.posologia.Frecuencia
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Plantillas sobre PostgreSQL. Igual que el catálogo, ninguna consulta filtra por comuna:
 * lo hace la política de seguridad a nivel de fila.
 */
@Repository
class PlantillasJdbc(private val jdbc: JdbcClient) : PlantillasDePosologia {

    private val fila = RowMapper { rs, _ ->
        PlantillaDePosologia(
            id = rs.getObject("id", UUID::class.java),
            comuna = CodigoComuna.de(rs.getString("comuna")),
            gtin = Gtin.de(rs.getString("gtin")),
            dosis = Dosis(Dosis.normalizar(rs.getBigDecimal("cantidad")), UnidadDeDosis.de(rs.getString("unidad"))),
            frecuencia = Frecuencia.de(rs.getString("frecuencia")),
            duracionDias = rs.getInt("duracion_dias").takeUnless { rs.wasNull() },
            indicaciones = rs.getString("indicaciones"),
            creadaEn = rs.getTimestamp("creada_en").toInstant(),
        )
    }

    override fun listar(gtin: Gtin): List<PlantillaDePosologia> =
        jdbc.sql("SELECT * FROM plantilla_posologia WHERE gtin = :gtin ORDER BY creada_en, id")
            .param("gtin", gtin.valor)
            .query(fila)
            .list()

    override fun crear(comuna: CodigoComuna, nueva: NuevaPlantilla, autor: String): PlantillaDePosologia =
        jdbc.sql(
            """
            INSERT INTO plantilla_posologia
                (comuna, gtin, cantidad, unidad, frecuencia, duracion_dias, indicaciones, creada_por)
            VALUES (:comuna, :gtin, :cantidad, :unidad, :frecuencia, :duracion, :indicaciones, :autor)
            RETURNING *
            """.trimIndent()
        )
            .param("comuna", comuna.valor)
            .param("gtin", nueva.gtin.valor)
            .param("cantidad", nueva.dosis.cantidad)
            .param("unidad", nueva.dosis.unidad.codigo)
            .param("frecuencia", nueva.frecuencia.toString())
            .param("duracion", nueva.duracionDias)
            .param("indicaciones", nueva.indicaciones)
            .param("autor", autor)
            .query(fila)
            .single()
}
