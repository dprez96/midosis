package cl.midosis.core.emision

import cl.midosis.core.dominio.catalogo.CatalogoDeProductos
import cl.midosis.core.dominio.catalogo.Gtin
import cl.midosis.core.dominio.catalogo.Producto
import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.core.dominio.emision.Consentimiento
import cl.midosis.core.infraestructura.persistencia.ContextoComuna
import cl.midosis.core.soporte.PruebaIntegracion
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper

/** Corrección de un tratamiento ya cargado (HU-33) y consulta de su estado (RF-B-15). */
@AutoConfigureMockMvc
class CorreccionTest : PruebaIntegracion() {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var contexto: ContextoComuna
    @Autowired lateinit var catalogo: CatalogoDeProductos

    private val providencia = CodigoComuna.de("13123")
    private val json = JsonMapper.builder().build()

    private val consentimiento =
        """{"version": "${Consentimiento.VERSION_VIGENTE}", "aceptadoEn": "${Instant.now()}"}"""
    private val lineas =
        """[{"gtin": "7802250012344", "cantidad": 1, "unidad": "comprimido", "frecuencia": "PT24H",
             "duracionIndicadaDias": 30, "unidadesEntregadas": 30, "via": "VO"}]"""

    @BeforeEach
    fun datos() {
        limpiarDatos()
        contexto.en(providencia) {
            catalogo.guardar(Producto(providencia, Gtin.de("7802250012344"), "Losartán 50 mg", "Losartán potásico", "Comprimido", "50 mg"))
        }
    }

    private fun emitir(): String {
        val r = mvc.post("/api/emisiones") {
            header("Authorization", "Bearer ${token("13123")}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"consentimiento": $consentimiento, "lineas": $lineas}"""
        }.andExpect { status { isCreated() } }.andReturn()
        return json.readTree(r.response.contentAsString).get("id").asString()
    }

    private fun corregir(id: String, rol: String = "qf", comuna: String = "13123", instruccion: String = "suspender", motivo: String = "Dosis mal transcrita") =
        mvc.post("/api/emisiones/$id/correccion") {
            header("Authorization", "Bearer ${token(comuna, rol = rol, sujeto = "qf-que-corrige")}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"motivo": "$motivo", "instruccion": "$instruccion", "consentimiento": $consentimiento, "lineas": $lineas}"""
        }

    private fun estado(vararg ids: String) = mvc.post("/api/estado") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"ids": [${ids.joinToString(",") { "\"$it\"" }}]}"""
    }

    @Test
    fun `el químico farmacéutico emite un reemplazo ligado al original y queda en auditoría`() {
        val original = emitir()
        val r = corregir(original).andExpect { status { isCreated() } }.andReturn()
        val reemplazo = json.readTree(r.response.contentAsString).get("id").asString()

        val fila = administrador.queryForMap("SELECT * FROM correccion WHERE original = ?", original)
        assertEquals(reemplazo, (fila["reemplazo"] as String).trim())
        assertEquals("suspender", fila["instruccion"])
        assertEquals("Dosis mal transcrita", fila["motivo"])
        assertEquals("qf-que-corrige", fila["corregida_por"])
        assertEquals(1, administrador.queryForObject("SELECT count(*) FROM emision WHERE id = ?", Int::class.java, reemplazo))
    }

    @Test
    fun `un auxiliar no puede corregir`() {
        corregir(emitir(), rol = "auxiliar").andExpect { status { isForbidden() } }
    }

    @Test
    fun `un código se corrige una sola vez`() {
        val original = emitir()
        corregir(original).andExpect { status { isCreated() } }
        corregir(original).andExpect { status { isConflict() } }
    }

    @Test
    fun `sin motivo o con una instrucción desconocida no se corrige`() {
        val original = emitir()
        corregir(original, motivo = " ").andExpect { status { isBadRequest() } }
        corregir(original, instruccion = "olvidar").andExpect { status { isBadRequest() } }
        assertEquals(0, administrador.queryForObject("SELECT count(*) FROM correccion", Int::class.java))
    }

    @Test
    fun `la aplicación consulta el estado sin cuenta y conoce la instrucción`() {
        val corregido = emitir()
        val vigente = emitir()
        val revocado = emitir()
        corregir(corregido, instruccion = "continuar").andExpect { status { isCreated() } }
        mvc.post("/api/emisiones/$revocado/revocacion") {
            header("Authorization", "Bearer ${token("13123")}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"motivo":"Emitido por error"}"""
        }.andExpect { status { isOk() } }

        estado(corregido, vigente, revocado, "01M0000000000000000000000Z").andExpect {
            status { isOk() }
            jsonPath("$.codigos[0].estado") { value("corregido") }
            jsonPath("$.codigos[0].instruccion") { value("continuar") }
            jsonPath("$.codigos[1].estado") { value("vigente") }
            jsonPath("$.codigos[2].estado") { value("revocado") }
            jsonPath("$.codigos[3].estado") { value("desconocido") }
        }
    }

    @Test
    fun `la consulta rechaza identificadores inválidos o demasiados`() {
        estado("no-es-un-ulid").andExpect { status { isBadRequest() } }
        estado(*Array(21) { "01M0000000000000000000000Z" }).andExpect { status { isBadRequest() } }
        mvc.post("/api/estado") { contentType = MediaType.APPLICATION_JSON; content = """{"ids": []}""" }
            .andExpect { status { isBadRequest() } }
    }
}
