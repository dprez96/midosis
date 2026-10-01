package cl.midosis.core.aislamiento

import cl.midosis.core.dominio.catalogo.CatalogoDeProductos
import cl.midosis.core.dominio.catalogo.Gtin
import cl.midosis.core.dominio.catalogo.Producto
import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.core.dominio.posologia.NuevaPlantilla
import cl.midosis.core.dominio.posologia.PlantillasDePosologia
import cl.midosis.core.infraestructura.persistencia.ContextoComuna
import cl.midosis.core.soporte.PruebaIntegracion
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.dao.DataAccessException
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Batería de aislamiento entre comunas aplicada a las plantillas de posología (HU-03).
 * Son contenido clínico de cada comuna: ninguna puede ver, crear ni inferir las de otra.
 */
@AutoConfigureMockMvc
class AislamientoDePlantillasTest : PruebaIntegracion() {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var contexto: ContextoComuna
    @Autowired lateinit var catalogo: CatalogoDeProductos
    @Autowired lateinit var plantillas: PlantillasDePosologia

    private val providencia = CodigoComuna.de("13123")
    private val santiago = CodigoComuna.de("13101")

    /** El mismo producto en las dos comunas. */
    private val losartan = Gtin.de("7802250012344")

    /** Un producto que solo tiene Santiago. */
    private val atorvastatina = Gtin.de("7802250012504")

    @BeforeEach
    fun datos() {
        limpiarDatos()
        contexto.en(providencia) { catalogo.guardar(producto(providencia, losartan, "Losartán 50 mg")) }
        contexto.en(santiago) {
            catalogo.guardar(producto(santiago, losartan, "Losartán 50 mg"))
            catalogo.guardar(producto(santiago, atorvastatina, "Atorvastatina 20 mg"))
            plantillas.crear(santiago, cadaDoceHoras(losartan), "uid-qf-santiago")
        }
    }

    @Test
    fun `las plantillas de otra comuna no se ven, aunque el producto sea el mismo`() {
        listar(providencia, losartan).andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }

        // Control: la plantilla existe, y su comuna sí la ve.
        listar(santiago, losartan).andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
        }
    }

    @Test
    fun `no se puede crear una plantilla para un producto que solo tiene otra comuna`() {
        mvc.post("/api/catalogo/productos/${atorvastatina.valor}/plantillas") {
            header("Authorization", "Bearer ${token(providencia.valor, rol = "qf")}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"cantidad": 1, "unidad": "comprimido", "frecuencia": "PT24H"}"""
        }.andExpect { status { isNotFound() } }

        assertEquals(listOf(santiago.valor), comunasConPlantillas())
    }

    @Test
    fun `sin la variable de sesion el repositorio no ve plantillas ni puede crearlas`() {
        assertTrue(plantillas.listar(losartan).isEmpty())
        assertFailsWith<DataAccessException> {
            plantillas.crear(providencia, cadaDoceHoras(losartan, duracion = 60), "uid")
        }
    }

    @Test
    fun `desde el contexto de una comuna no se puede crear una plantilla de otra`() {
        assertFailsWith<DataAccessException> {
            contexto.en(providencia) { plantillas.crear(santiago, cadaDoceHoras(losartan, duracion = 60), "uid") }
        }
        assertEquals(1, administrador.queryForObject("SELECT count(*) FROM plantilla_posologia", Int::class.java))
    }

    @Test
    fun `la aplicacion solo puede consultar y crear plantillas, no cambiarlas ni borrarlas`() {
        val privilegios = administrador.queryForList(
            """
            SELECT privilege_type FROM information_schema.role_table_grants
            WHERE grantee = 'midosis_app' AND table_name = 'plantilla_posologia'
            """.trimIndent(),
            String::class.java,
        )

        assertEquals(setOf("SELECT", "INSERT"), privilegios.toSet())
    }

    @Test
    fun `el identificador no deja inferir cuantas plantillas crean las demas comunas`() {
        val id = administrador.queryForObject("SELECT id FROM plantilla_posologia", UUID::class.java)!!

        assertEquals(4, id.version(), "un UUID aleatorio no revela el orden ni la cantidad")
    }

    private fun listar(comuna: CodigoComuna, gtin: Gtin) =
        mvc.get("/api/catalogo/productos/${gtin.valor}/plantillas") {
            header("Authorization", "Bearer ${token(comuna.valor)}")
        }

    private fun comunasConPlantillas(): List<String?> =
        administrador.queryForList("SELECT DISTINCT comuna FROM plantilla_posologia ORDER BY comuna", String::class.java)

    private fun cadaDoceHoras(gtin: Gtin, duracion: Int = 30) =
        NuevaPlantilla.de(gtin, BigDecimal.ONE, "comprimido", "PT12H", duracion, null)

    private fun producto(comuna: CodigoComuna, gtin: Gtin, nombre: String) =
        Producto(comuna, gtin, nombre, nombre.substringBefore(" "), "Comprimido", nombre.substringAfter(" "))
}
