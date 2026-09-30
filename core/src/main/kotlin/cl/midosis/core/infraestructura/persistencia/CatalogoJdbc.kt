package cl.midosis.core.infraestructura.persistencia

import cl.midosis.core.dominio.catalogo.CatalogoDeProductos
import cl.midosis.core.dominio.catalogo.Gtin
import cl.midosis.core.dominio.catalogo.Producto
import cl.midosis.core.dominio.comuna.CodigoComuna
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * Catálogo sobre PostgreSQL. Ninguna consulta filtra por comuna: lo hace la política de
 * seguridad a nivel de fila. Filtrar también aquí daría una falsa sensación de
 * seguridad, y un olvido en una consulta nueva pasaría desapercibido en las pruebas.
 */
@Repository
class CatalogoJdbc(private val jdbc: JdbcClient) : CatalogoDeProductos {

    private val fila = RowMapper { rs, _ ->
        Producto(
            comuna = CodigoComuna.de(rs.getString("comuna")),
            gtin = Gtin.de(rs.getString("gtin")),
            nombre = rs.getString("nombre"),
            principioActivo = rs.getString("principio_activo"),
            forma = rs.getString("forma"),
            concentracion = rs.getString("concentracion"),
        )
    }

    override fun listar(): List<Producto> =
        jdbc.sql("SELECT * FROM producto ORDER BY nombre, gtin").query(fila).list()

    override fun buscar(gtin: Gtin): Producto? =
        jdbc.sql("SELECT * FROM producto WHERE gtin = :gtin")
            .param("gtin", gtin.valor)
            .query(fila)
            .optional()
            .orElse(null)

    override fun guardar(producto: Producto) {
        jdbc.sql(
            """
            INSERT INTO producto (comuna, gtin, nombre, principio_activo, forma, concentracion)
            VALUES (:comuna, :gtin, :nombre, :principio, :forma, :concentracion)
            ON CONFLICT (comuna, gtin) DO UPDATE SET
                nombre = EXCLUDED.nombre,
                principio_activo = EXCLUDED.principio_activo,
                forma = EXCLUDED.forma,
                concentracion = EXCLUDED.concentracion
            """.trimIndent()
        )
            .param("comuna", producto.comuna.valor)
            .param("gtin", producto.gtin.valor)
            .param("nombre", producto.nombre)
            .param("principio", producto.principioActivo)
            .param("forma", producto.forma)
            .param("concentracion", producto.concentracion)
            .update()
    }
}
