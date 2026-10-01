package cl.midosis.core.aislamiento

import cl.midosis.core.dominio.catalogo.CatalogoDeProductos
import cl.midosis.core.dominio.catalogo.Gtin
import cl.midosis.core.dominio.catalogo.Producto
import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.core.infraestructura.persistencia.ContextoComuna
import cl.midosis.core.soporte.PruebaIntegracion
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Batería de aislamiento entre comunas (HU-28, Tabla 99 del informe). Su fallo bloquea
 * la integración continua: ninguna comuna puede ver, escribir ni inferir datos de otra.
 *
 * Las pruebas 6 a 8 de la Tabla 99 esperan lo que todavía no existe: el canje de códigos
 * (S2), los usuarios del proveedor y la restauración de respaldos en la infraestructura.
 */
@AutoConfigureMockMvc
class AislamientoEntreComunasTest : PruebaIntegracion() {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var contexto: ContextoComuna
    @Autowired lateinit var catalogo: CatalogoDeProductos
    @Autowired lateinit var jdbc: JdbcClient

    private val providencia = CodigoComuna.de("13123")
    private val santiago = CodigoComuna.de("13101")

    private val losartan = producto(providencia, "780225001234", "Losartán 50 mg")
    private val metformina = producto(providencia, "780225001240", "Metformina 850 mg")
    private val soloEnSantiago = producto(santiago, "780225001250", "Atorvastatina 20 mg")

    @BeforeEach
    fun datos() {
        administrador.execute("TRUNCATE producto, evento_seguridad")
        contexto.en(providencia) {
            catalogo.guardar(losartan)
            catalogo.guardar(metformina)
        }
        contexto.en(santiago) { catalogo.guardar(soloEnSantiago) }
    }

    // ------------------------------------------------------------ guardia de la batería

    @Test
    fun `la aplicacion se conecta con un usuario que no puede saltarse el aislamiento`() {
        val (usuario, superusuario, saltaPoliticas) = jdbc.sql(
            "SELECT current_user, rolsuper, rolbypassrls FROM pg_roles WHERE rolname = current_user"
        ).query { rs, _ -> Triple(rs.getString(1), rs.getBoolean(2), rs.getBoolean(3)) }.single()
        val dueno = administrador.queryForObject(
            "SELECT tableowner FROM pg_tables WHERE tablename = 'producto'", String::class.java
        )

        assertEquals("midosis_app", usuario)
        assertFalse(superusuario, "la aplicación no puede ser superusuario")
        assertFalse(saltaPoliticas, "la aplicación no puede tener BYPASSRLS")
        assertNotEquals(usuario, dueno, "la aplicación no puede ser dueña de las tablas")
    }

    // ------------------------------------------------------------ Tabla 99, prueba 1

    @Test
    fun `un producto de otra comuna responde igual que uno que no existe`() {
        val deOtraComuna = mvc.get("/api/catalogo/productos/${soloEnSantiago.gtin.valor}") {
            header("Authorization", "Bearer ${token(providencia.valor)}")
        }.andExpect { status { isNotFound() } }.andReturn().response.contentAsString

        val inexistente = mvc.get("/api/catalogo/productos/${gtin("780225009990").valor}") {
            header("Authorization", "Bearer ${token(providencia.valor)}")
        }.andExpect { status { isNotFound() } }.andReturn().response.contentAsString

        assertEquals(inexistente, deOtraComuna, "la respuesta no debe revelar que el producto existe")

        // Control: el producto existe, y su comuna sí lo ve.
        mvc.get("/api/catalogo/productos/${soloEnSantiago.gtin.valor}") {
            header("Authorization", "Bearer ${token(santiago.valor)}")
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `el listado solo trae el catalogo de la comuna del token`() {
        mvc.get("/api/catalogo/productos") {
            header("Authorization", "Bearer ${token(providencia.valor)}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[*].nombre") { value(org.hamcrest.Matchers.containsInAnyOrder("Losartán 50 mg", "Metformina 850 mg")) }
        }
    }

    @Test
    fun `la busqueda manual no encuentra productos de otra comuna, ni por nombre ni por codigo`() {
        // Los once primeros dígitos solo los tiene el producto de Santiago.
        for (texto in listOf("atorvastatina", soloEnSantiago.gtin.valor.take(11))) {
            mvc.get("/api/catalogo/productos") {
                param("texto", texto)
                header("Authorization", "Bearer ${token(providencia.valor)}")
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(0) }
            }

            // Control: el producto existe, y su comuna sí lo encuentra.
            mvc.get("/api/catalogo/productos") {
                param("texto", texto)
                header("Authorization", "Bearer ${token(santiago.valor)}")
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
            }
        }
    }

    // ------------------------------------------------------------ Tabla 99, prueba 2 y criterio de la HU-28

    @Test
    fun `indicar otra comuna en un parametro se rechaza y queda registrado`() {
        mvc.get("/api/catalogo/productos?comuna=${santiago.valor}") {
            header("Authorization", "Bearer ${token(providencia.valor)}")
        }.andExpect { status { isForbidden() } }

        assertEquals(1, eventos(providencia, "COMUNA_MANIPULADA"))
    }

    @Test
    fun `indicar otra comuna en una cabecera se rechaza y queda registrado`() {
        mvc.get("/api/catalogo/productos") {
            header("Authorization", "Bearer ${token(providencia.valor)}")
            header("X-Comuna", santiago.valor)
        }.andExpect { status { isForbidden() } }

        assertEquals(1, eventos(providencia, "COMUNA_MANIPULADA"))
    }

    @Test
    fun `indicar la propia comuna no es manipulacion`() {
        mvc.get("/api/catalogo/productos?comuna=${providencia.valor}") {
            header("Authorization", "Bearer ${token(providencia.valor)}")
        }.andExpect { status { isOk() } }

        assertEquals(0, eventos(providencia, "COMUNA_MANIPULADA"))
    }

    // ------------------------------------------------------------ Tabla 99, prueba 3

    @Test
    fun `sin la variable de sesion el repositorio no ve ninguna fila`() {
        assertTrue(catalogo.listar().isEmpty())
        assertEquals(null, catalogo.buscar(losartan.gtin))
    }

    @Test
    fun `sin la variable de sesion el repositorio no puede escribir`() {
        assertFailsWith<DataAccessException> {
            catalogo.guardar(producto(providencia, "780225001260", "Enalapril 10 mg"))
        }
    }

    // ------------------------------------------------------------ Tabla 99, prueba 4

    @Test
    fun `una conexion que vuelve al pool no conserva la comuna anterior`() {
        // El pool tiene una sola conexión: la consulta de afuera usa la misma que acaba
        // de trabajar dentro del contexto de Providencia.
        assertEquals(2, contexto.en(providencia) { catalogo.listar() }.size)

        val variable = jdbc.sql("SELECT NULLIF(current_setting('midosis.comuna', true), '')")
            .query(String::class.java).optional().orElse(null)
        assertEquals(null, variable, "la variable de sesión debe desaparecer al terminar la transacción")
        assertTrue(catalogo.listar().isEmpty())
    }

    // ------------------------------------------------------------ Tabla 99, prueba 5

    @Test
    fun `un agregado solo puede contar la comuna del contexto`() {
        val porComuna = contexto.en(providencia) {
            jdbc.sql("SELECT comuna, count(*) FROM producto GROUP BY comuna")
                .query { rs, _ -> rs.getString(1) to rs.getInt(2) }.list()
        }

        assertEquals(listOf(providencia.valor to 2), porComuna)
    }

    // ------------------------------------------------------------ escritura cruzada

    @Test
    fun `desde el contexto de una comuna no se puede escribir en otra`() {
        assertFailsWith<DataAccessException> {
            contexto.en(providencia) {
                catalogo.guardar(producto(santiago, "780225001270", "Omeprazol 20 mg"))
            }
        }
    }

    // ------------------------------------------------------------ el token

    @Test
    fun `un token sin comuna es rechazado`() {
        mvc.get("/api/catalogo/productos") {
            header("Authorization", "Bearer ${token(null)}")
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `un token con una comuna mal formada es rechazado`() {
        mvc.get("/api/catalogo/productos") {
            header("Authorization", "Bearer ${token("Providencia")}")
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `un token firmado con otra clave es rechazado`() {
        mvc.get("/api/catalogo/productos") {
            header("Authorization", "Bearer ${token(providencia.valor, firma = claveAjena)}")
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `sin token no hay acceso`() {
        mvc.get("/api/catalogo/productos").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `una peticion con token Bearer no queda bloqueada por la proteccion CSRF`() {
        // El catálogo no acepta POST: si CSRF la bloqueara la respuesta sería 403, y
        // llegar a 405 prueba que la petición pasó.
        mvc.post("/api/catalogo/productos") {
            header("Authorization", "Bearer ${token(providencia.valor)}")
        }.andExpect { status { isMethodNotAllowed() } }
    }

    @Test
    fun `una peticion sin token Bearer sigue protegida por CSRF`() {
        // Con CSRF activo, el filtro la rechaza con 403 antes de pedir autenticación. Si
        // alguien lo deshabilitara, la respuesta pasaría a ser 401 y esta prueba fallaría.
        mvc.post("/api/catalogo/productos").andExpect { status { isForbidden() } }
    }

    // ------------------------------------------------------------ utilidades

    private fun eventos(comuna: CodigoComuna, tipo: String): Int =
        administrador.queryForObject(
            "SELECT count(*) FROM evento_seguridad WHERE comuna = ? AND tipo = ?",
            Int::class.java, comuna.valor, tipo,
        )!!

    private fun producto(comuna: CodigoComuna, cuerpo: String, nombre: String) = Producto(
        comuna = comuna,
        gtin = gtin(cuerpo),
        nombre = nombre,
        principioActivo = nombre.substringBefore(" "),
        forma = "Comprimido",
        concentracion = nombre.substringAfter(" "),
    )

    /** GTIN-13 sintético con su dígito verificador calculado. */
    private fun gtin(cuerpo: String): Gtin {
        val suma = cuerpo.reversed().withIndex().sumOf { (i, c) -> c.digitToInt() * if (i % 2 == 0) 3 else 1 }
        return Gtin.de(cuerpo + (10 - suma % 10) % 10)
    }
}
