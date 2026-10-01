package cl.midosis.core.infraestructura.persistencia

import cl.midosis.core.dominio.catalogo.CatalogoDeProductos
import cl.midosis.core.dominio.catalogo.Gtin
import cl.midosis.core.dominio.catalogo.Producto
import cl.midosis.core.dominio.catalogo.TerminoDeBusqueda
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

    /**
     * La comparación sin tildes se hace con translate y no con la extensión unaccent, que
     * habría que instalar en cada base. El término llega ya normalizado de la misma forma
     * (ver [TerminoDeBusqueda.normalizar]), y los comodines de LIKE que traiga se escapan:
     * buscar «%» no puede traer el catálogo completo.
     */
    override fun buscarPorTexto(termino: TerminoDeBusqueda, limite: Int): List<Producto> {
        val literal = escaparComodines(termino.normalizado)
        return jdbc.sql(
            """
            SELECT * FROM producto
            WHERE translate(lower(nombre || ' ' || principio_activo), :conTilde, :sinTilde)
                  LIKE :contiene ESCAPE '\'
               OR gtin LIKE :comienza ESCAPE '\'
            ORDER BY nombre, gtin
            LIMIT :limite
            """.trimIndent()
        )
            .param("conTilde", CON_TILDE)
            .param("sinTilde", SIN_TILDE)
            .param("contiene", "%$literal%")
            .param("comienza", "$literal%")
            .param("limite", limite)
            .query(fila)
            .list()
    }

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

    private companion object {
        /** Letras que translate reemplaza; incluye las mayúsculas por si lower no las cubre. */
        const val CON_TILDE = "áéíóúüñàèìòùâêîôûäëïöÁÉÍÓÚÜÑ"

        /** Su equivalente normalizado, letra por letra, con la misma regla que el término. */
        val SIN_TILDE: String = CON_TILDE.map { TerminoDeBusqueda.normalizar(it.toString()) }.joinToString("")

        fun escaparComodines(texto: String): String =
            texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
}
