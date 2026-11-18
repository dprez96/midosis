package cl.midosis.core.emision

import cl.midosis.core.dominio.catalogo.CatalogoDeProductos
import cl.midosis.core.dominio.catalogo.ControlLegal
import cl.midosis.core.dominio.catalogo.Gtin
import cl.midosis.core.dominio.catalogo.Producto
import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.core.dominio.emision.Consentimiento
import cl.midosis.core.infraestructura.persistencia.ContextoComuna
import cl.midosis.core.soporte.PruebaIntegracion
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper

/** Validación farmacéutica de la posología antes de emitir (HU-32, RN-24). */
@AutoConfigureMockMvc
class ValidacionFarmaceuticaTest : PruebaIntegracion() {

    @Autowired lateinit var mvc: org.springframework.test.web.servlet.MockMvc
    @Autowired lateinit var contexto: ContextoComuna
    @Autowired lateinit var catalogo: CatalogoDeProductos

    private val providencia = CodigoComuna.de("13123")
    private val json = JsonMapper.builder().build()
    private val losartan = "7802250012344"
    private val clonazepam = "7802250013303"

    @BeforeEach
    fun datos() {
        limpiarDatos()
        contexto.en(providencia) {
            catalogo.guardar(Producto(providencia, Gtin.de(losartan), "Losartán 50 mg", "Losartán potásico", "Comprimido", "50 mg"))
            catalogo.guardar(Producto(providencia, Gtin.de(clonazepam), "Clonazepam 0,5 mg", "Clonazepam", "Comprimido", "0,5 mg", ControlLegal.PSICOTROPICO))
        }
        plantillaDiaria("13123", losartan)
    }

    private fun cuerpo(gtin: String = losartan, frecuencia: String = "PT24H", indicado: Int = 180, unidades: Int = 30, validacion: String? = null) = """
        {"consentimiento": {"version": "${Consentimiento.VERSION_VIGENTE}", "aceptadoEn": "${Instant.now()}"},
         "lineas": [{"gtin": "$gtin", "cantidad": 1, "unidad": "comprimido", "frecuencia": "$frecuencia",
                     "duracionIndicadaDias": $indicado, "unidadesEntregadas": $unidades, "via": "VO"}]
         ${validacion?.let { ""","validacion": {"token": "$it"}""" } ?: ""}}
    """.trimIndent()

    private fun emitir(cuerpo: String, rol: String = "auxiliar") = mvc.post("/api/emisiones") {
        header("Authorization", "Bearer ${token("13123", rol = rol, sujeto = "auxiliar-1")}")
        contentType = MediaType.APPLICATION_JSON
        content = cuerpo
    }

    private fun fila(r: org.springframework.test.web.servlet.ResultActionsDsl) =
        administrador.queryForMap("SELECT * FROM emision WHERE id = ?", json.readTree(r.andReturn().response.contentAsString).get("id").asString())

    @Test
    fun `una posología de plantilla la emite el auxiliar sin validación`() {
        val r = emitir(cuerpo()).andExpect { status { isCreated() } }
        assertNull(fila(r)["validada_por"])
    }

    @Test
    fun `sin plantilla, con control legal o con lo entregado sobre lo indicado, no se emite y se dice por qué`() {
        emitir(cuerpo(frecuencia = "PT12H")).andExpect {
            status { isConflict() }
            jsonPath("$.codigo") { value("validacion-requerida") }
            jsonPath("$.motivos[0]") { value("Losartán 50 mg: la posología no proviene de una plantilla.") }
        }
        emitir(cuerpo(gtin = clonazepam)).andExpect {
            status { isConflict() }
            jsonPath("$.motivos[1]") { value("Clonazepam 0,5 mg: producto sujeto a control legal (DS405).") }
        }
        emitir(cuerpo(indicado = 10, unidades = 30)).andExpect {
            status { isConflict() }
            jsonPath("$.motivos[0]") { value("Losartán 50 mg: lo entregado cubre más días que el tratamiento indicado.") }
        }
        assertEquals(0, administrador.queryForObject("SELECT count(*) FROM emision", Int::class.java))
    }

    @Test
    fun `con la credencial de un químico farmacéutico de la comuna se emite y queda en auditoría`() {
        val qf = token("13123", rol = "qf", sujeto = "qf-que-valida")
        val r = emitir(cuerpo(frecuencia = "PT12H", validacion = qf)).andExpect { status { isCreated() } }
        val f = fila(r)
        assertEquals("auxiliar-1", f["emitida_por"])
        assertEquals("qf-que-valida", f["validada_por"])
        assertEquals("""["Losartán 50 mg: la posología no proviene de una plantilla."]""", json.readTree(f["motivos_validacion"].toString()).toString())
    }

    @Test
    fun `el químico farmacéutico que emite valida con su propia sesión`() {
        val r = emitir(cuerpo(gtin = clonazepam), rol = "qf").andExpect { status { isCreated() } }
        assertEquals("auxiliar-1", fila(r)["validada_por"])
    }

    @Test
    fun `no valida un auxiliar, un químico farmacéutico de otra comuna ni un token falso`() {
        emitir(cuerpo(frecuencia = "PT12H", validacion = token("13123", rol = "auxiliar"))).andExpect { status { isForbidden() } }
        emitir(cuerpo(frecuencia = "PT12H", validacion = token("13101", rol = "qf"))).andExpect { status { isForbidden() } }
        emitir(cuerpo(frecuencia = "PT12H", validacion = "no-es-un-token")).andExpect { status { isForbidden() } }
        assertEquals(0, administrador.queryForObject("SELECT count(*) FROM emision", Int::class.java))
    }
}
