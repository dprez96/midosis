package cl.midosis.core.emision

import cl.midosis.core.dominio.catalogo.CatalogoDeProductos
import cl.midosis.core.dominio.catalogo.ControlLegal
import cl.midosis.core.dominio.catalogo.Gtin
import cl.midosis.core.dominio.catalogo.Producto
import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.core.dominio.emision.Consentimiento
import cl.midosis.core.infraestructura.persistencia.ContextoComuna
import cl.midosis.core.soporte.PruebaIntegracion
import cl.midosis.credencial.ClaveDeContenido
import cl.midosis.credencial.ClaveDeEmisor
import cl.midosis.credencial.ClavesDeConfianza
import cl.midosis.credencial.LectorDeCodigos
import cl.midosis.credencial.Lectura
import cl.midosis.credencial.Via
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * Emisión del código al cerrar la atención (HU-01) con el consentimiento del paciente (HU-05).
 * Lo emitido se lee con el mismo lector que usa la aplicación del paciente.
 */
@AutoConfigureMockMvc
class EmisionTest : PruebaIntegracion() {

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
            catalogo.guardar(
                Producto(providencia, Gtin.de("7802250013303"), "Clonazepam 0,5 mg", "Clonazepam", "Comprimido", "0,5 mg", ControlLegal.PSICOTROPICO),
            )
        }
    }

    private fun peticion(
        unidades: String = "60",
        version: String = Consentimiento.VERSION_VIGENTE,
        gtin: String = "7802250012344",
    ) = """
        {"consentimiento": {"version": "$version", "aceptadoEn": "${Instant.now()}"},
         "lineas": [{"gtin": "$gtin", "cantidad": 1, "unidad": "comprimido", "frecuencia": "PT24H",
                     "duracionIndicadaDias": 180, "unidadesEntregadas": $unidades, "via": "VO",
                     "indicaciones": "Con alimentos"}]}
    """.trimIndent()

    private fun emitir(cuerpo: String, comuna: String = "13123") =
        mvc.post("/api/emisiones") {
            header("Authorization", "Bearer ${token(comuna)}")
            contentType = MediaType.APPLICATION_JSON
            content = cuerpo
        }

    /** El conjunto de confianza de la aplicación de prueba. */
    private fun lector(): LectorDeCodigos {
        val c = json.readTree(File("../contrato/vectores/claves-de-prueba.json"))
        fun hex(t: String) = ByteArray(t.length / 2) { t.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
        val emisores = c.get("firma").toList().filter { it.get("enElConjunto").asBoolean() }.map {
            ClaveDeEmisor(it.get("kid").asString(), hex(it.get("publica").asString()), it.get("comuna").asString(),
                it.get("vigenteDesde").asLong(), null, it.get("revocada").asBoolean())
        }
        val contenido = c.get("contenido").toList().filter { it.get("enElConjunto").asBoolean() }
            .map { ClaveDeContenido(it.get("kid").asString(), hex(it.get("clave").asString())) }
        return LectorDeCodigos(ClavesDeConfianza(emisores, contenido, admiteClavesDePrueba = true)) { Instant.now().epochSecond }
    }

    private fun respuesta(cuerpo: String): JsonNode {
        val r = emitir(cuerpo).andExpect { status { isCreated() } }.andReturn()
        return json.readTree(r.response.contentAsString)
    }

    @Test
    fun `el código emitido es auténtico para la aplicación del paciente`() {
        val r = respuesta(peticion())
        val lectura = assertIs<Lectura.Aceptado>(lector().leer(r.get("codigo").asString()))
        val losartan = lectura.carga.productos.single()
        assertEquals("13123", lectura.carga.comuna)
        assertEquals("CL-FP-13123-01", lectura.carga.emisor)
        assertEquals("Losartán 50 mg", losartan.etiqueta)
        assertEquals("1 comprimido", losartan.dosis)
        assertEquals(180, losartan.duracionIndicadaDias)
        assertEquals(60, losartan.dosisEntregadas)
        assertEquals(60, losartan.coberturaDias)
        assertEquals(Via.ORAL, losartan.via)
        assertEquals("Con alimentos", losartan.nota)
        assertEquals(r.get("id").asString(), lectura.carga.id)
        assertEquals(72 * 3600, lectura.carga.expiraEn - lectura.carga.emitidoEn)
    }

    @Test
    fun `el código corto y el PIN impresos canjean el mismo código`() {
        val r = respuesta(peticion())
        mvc.post("/api/canje") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"codigo":"${r.get("codigoCorto").asString()}","pin":"${r.get("pin").asString()}"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.codigo") { value(r.get("codigo").asString()) }
        }
    }

    @Test
    fun `un producto con control legal conserva su nombre dentro del código`() {
        val r = respuesta(peticion(gtin = "7802250013303"))
        val lectura = assertIs<Lectura.Aceptado>(lector().leer(r.get("codigo").asString()))
        assertEquals("Clonazepam 0,5 mg", lectura.carga.productos.single().etiqueta)
    }

    @Test
    fun `el registro guarda el consentimiento y nada del paciente`() {
        val r = respuesta(peticion())
        val fila = administrador.queryForMap("SELECT * FROM emision WHERE id = ?", r.get("id").asString())
        assertEquals(Consentimiento.VERSION_VIGENTE, fila["consentimiento_version"])
        assertEquals("usuario-de-prueba", fila["emitida_por"])
        assertEquals("prueba-1", fila["kid"])
        val productos = json.readTree(fila["productos"].toString())
        // Solo lo necesario para auditar y proyectar la demanda (HU-27): nada del paciente.
        assertEquals(setOf("gtin", "du", "e", "co", "u"), productos.get(0).propertyNames().asSequence().toSet())
    }

    @Test
    fun `sin el consentimiento vigente no se emite`() {
        emitir(peticion(version = "texto-viejo")).andExpect { status { isBadRequest() } }
        emitir(peticion().replace(Regex(""""consentimiento": \{[^}]*},"""), "")).andExpect { status { isBadRequest() } }
        assertEquals(0, administrador.queryForObject("SELECT count(*) FROM emision", Int::class.java))
    }

    @Test
    fun `lo entregado tiene que alcanzar para una toma`() {
        emitir(peticion(unidades = "0.5")).andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("lo entregado debe alcanzar para al menos una toma y como máximo 9999") }
        }
    }

    @Test
    fun `un producto de otra comuna responde como inexistente`() {
        emitir(peticion(), comuna = "13101").andExpect { status { isNotFound() } }
    }

    @Test
    fun `la emisión tarda menos de 3 segundos`() {
        val inicio = System.nanoTime()
        respuesta(peticion())
        val ms = (System.nanoTime() - inicio) / 1_000_000
        assertTrue(ms < 3000, "la emisión tardó $ms ms (RNF-01 pide menos de 3 s con la impresión)")
    }
}
