package cl.midosis.core.exportacion

import cl.midosis.core.aplicacion.exportacion.ExportacionDeComuna
import cl.midosis.core.soporte.PruebaIntegracion
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/** Exportación de los datos de la comuna en formato abierto (HU-29, RF-B-12, RN-20). */
@AutoConfigureMockMvc
class ExportacionTest : PruebaIntegracion() {

    @Autowired lateinit var mvc: MockMvc

    @BeforeEach
    fun datos() {
        limpiarDatos()
        for ((comuna, gtin, nombre) in listOf(Triple("13123", "7802250012344", "Losartán 50 mg"), Triple("13101", "7802250020011", "Ibuprofeno 400 mg"))) {
            administrador.update(
                "INSERT INTO producto (comuna, gtin, nombre, principio_activo, forma, concentracion) VALUES (?, ?, ?, 'x', 'Comprimido', 'x')",
                comuna, gtin, nombre,
            )
        }
        administrador.update(
            "INSERT INTO emision (id, comuna, punto, expira_en, emitida_por, kid, consentimiento_version, consentimiento_en, productos) " +
                "VALUES ('01M0000000000000000000000A', '13123', 'CL-FP-13123-01', now(), 'u', 'k', 'v', now(), " +
                "'[{\"gtin\": \"7802250012344\", \"du\": 180, \"e\": 60, \"co\": 30, \"u\": 60}]'::jsonb)",
        )
        administrador.update(
            "INSERT INTO emision (id, comuna, punto, expira_en, emitida_por, kid, consentimiento_version, consentimiento_en, productos) " +
                "VALUES ('01M0000000000000000000000B', '13101', 'CL-FP-13101-01', now(), 'u', 'k', 'v', now(), '[]'::jsonb)",
        )
    }

    private fun exportar(rol: String = "administrador", url: String = "/api/exportacion") =
        mvc.get(url) { header("Authorization", "Bearer ${token("13123", rol = rol, sujeto = "admin-1")}") }

    private fun archivos(zip: ByteArray): Map<String, String> {
        val r = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(zip), Charsets.UTF_8).use { z ->
            generateSequence { z.nextEntry }.forEach { r[it.name] = z.readBytes().toString(Charsets.UTF_8) }
        }
        return r
    }

    @Test
    fun `el administrador descarga un ZIP con los datos de su comuna y el diccionario`() {
        val r = exportar().andExpect {
            status { isOk() }
            header { string("Content-Type", "application/zip") }
        }.andReturn()
        val a = archivos(r.response.contentAsByteArray)
        assertEquals(
            setOf("catalogo.csv", "plantillas.csv", "tratamientos.csv", "productos_por_tratamiento.csv", "correcciones.csv",
                "auditoria.csv", "metricas_mensuales.csv", "diccionario.csv", "LEEME.txt"),
            a.keys,
        )
        assertTrue("Losartán 50 mg" in a.getValue("catalogo.csv"))
        assertTrue("01M0000000000000000000000A,7802250012344,180,60,30,60" in a.getValue("productos_por_tratamiento.csv"))
        assertTrue(a.getValue("diccionario.csv").contains("tratamientos.csv,emitida_por,"))
        assertTrue(a.getValue("metricas_mensuales.csv").lines()[1].contains(",1,0,0,0,1"))
    }

    @Test
    fun `no trae nada de otra comuna`() {
        val a = archivos(exportar().andReturn().response.contentAsByteArray)
        assertFalse(a.values.any { "Ibuprofeno" in it || "01M0000000000000000000000B" in it || "13101" in it })
    }

    @Test
    fun `cada exportación queda en la auditoría`() {
        exportar().andExpect { status { isOk() } }
        assertEquals(1, administrador.queryForObject("SELECT count(*) FROM evento_seguridad WHERE tipo = 'EXPORTACION' AND comuna = '13123'", Int::class.java))
    }

    @Test
    fun `solo exporta el administrador comunal`() {
        exportar(rol = "qf").andExpect { status { isForbidden() } }
        exportar(rol = "auxiliar").andExpect { status { isForbidden() } }
    }

    @Test
    fun `pedir los datos de otra comuna se rechaza y se registra`() {
        exportar(url = "/api/exportacion?comuna=13101").andExpect { status { isForbidden() } }
        assertEquals(1, administrador.queryForObject("SELECT count(*) FROM evento_seguridad WHERE tipo = 'COMUNA_MANIPULADA'", Int::class.java))
        assertEquals(0, administrador.queryForObject("SELECT count(*) FROM evento_seguridad WHERE tipo = 'EXPORTACION'", Int::class.java))
    }

    @Test
    fun `el CSV escapa comas, comillas y saltos de línea`() {
        assertEquals("a,b\r\n\"x,1\",\"di \"\"hola\"\"\"\r\n", ExportacionDeComuna.csv(listOf("a", "b"), listOf(listOf("x,1", "di \"hola\""))))
    }
}
