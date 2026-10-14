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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** Revocación de un código emitido por error (HU-07). */
@AutoConfigureMockMvc
class RevocacionTest : PruebaIntegracion() {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var contexto: ContextoComuna
    @Autowired lateinit var catalogo: CatalogoDeProductos

    private val providencia = CodigoComuna.de("13123")
    private val json = JsonMapper.builder().build()

    @BeforeEach
    fun datos() {
        limpiarDatos()
        contexto.en(providencia) {
            catalogo.guardar(Producto(providencia, Gtin.de("7802250012344"), "Losartán 50 mg", "Losartán potásico", "Comprimido", "50 mg"))
        }
    }

    private fun emitir(): JsonNode {
        val r = mvc.post("/api/emisiones") {
            header("Authorization", "Bearer ${token("13123")}")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"consentimiento": {"version": "${Consentimiento.VERSION_VIGENTE}", "aceptadoEn": "${Instant.now()}"},
                 "lineas": [{"gtin": "7802250012344", "cantidad": 1, "unidad": "comprimido", "frecuencia": "PT24H",
                             "duracionIndicadaDias": 30, "unidadesEntregadas": 30, "via": "VO"}]}
            """.trimIndent()
        }.andExpect { status { isCreated() } }.andReturn()
        return json.readTree(r.response.contentAsString)
    }

    private fun revocar(id: String, motivo: String = "Posología mal transcrita", comuna: String = "13123") =
        mvc.post("/api/emisiones/$id/revocacion") {
            header("Authorization", "Bearer ${token(comuna, sujeto = "qf-que-revoca")}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"motivo":"$motivo"}"""
        }

    private fun canjear(emitido: JsonNode) = mvc.post("/api/canje") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"codigo":"${emitido.get("codigoCorto").asString()}","pin":"${emitido.get("pin").asString()}"}"""
    }

    @Test
    fun `un código revocado ya no se canjea en línea`() {
        val emitido = emitir()
        revocar(emitido.get("id").asString()).andExpect { status { isOk() } }
        canjear(emitido).andExpect { status { isNotFound() } }
    }

    @Test
    fun `la revocación queda registrada con quién, cuándo y por qué`() {
        val id = emitir().get("id").asString()
        revocar(id).andExpect { status { isOk() } }
        val fila = administrador.queryForMap("SELECT revocada_en, revocada_por, motivo_revocacion FROM emision WHERE id = ?", id)
        assertEquals("qf-que-revoca", fila["revocada_por"])
        assertEquals("Posología mal transcrita", fila["motivo_revocacion"])
        assert(fila["revocada_en"] != null)
    }

    @Test
    fun `revocar dos veces avisa que ya estaba revocado`() {
        val id = emitir().get("id").asString()
        revocar(id).andExpect { status { isOk() } }
        revocar(id).andExpect { status { isConflict() } }
    }

    @Test
    fun `sin motivo no se revoca`() {
        val emitido = emitir()
        revocar(emitido.get("id").asString(), motivo = " ").andExpect { status { isBadRequest() } }
        canjear(emitido).andExpect { status { isOk() } }
    }

    @Test
    fun `otra comuna no puede revocar ni ver el código`() {
        val emitido = emitir()
        revocar(emitido.get("id").asString(), comuna = "13101").andExpect { status { isNotFound() } }
        mvc.get("/api/emisiones/recientes") { header("Authorization", "Bearer ${token("13101")}") }
            .andExpect { jsonPath("$.length()") { value(0) } }
        canjear(emitido).andExpect { status { isOk() } }
    }

    @Test
    fun `las emisiones recientes muestran los productos por su nombre`() {
        val id = emitir().get("id").asString()
        mvc.get("/api/emisiones/recientes") { header("Authorization", "Bearer ${token("13123")}") }.andExpect {
            status { isOk() }
            jsonPath("$[0].id") { value(id) }
            jsonPath("$[0].productos[0]") { value("Losartán 50 mg") }
        }
    }
}
