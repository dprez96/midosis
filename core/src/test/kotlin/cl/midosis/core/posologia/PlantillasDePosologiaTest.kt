package cl.midosis.core.posologia

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
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals

/**
 * Plantillas de posología frecuente (HU-03): el químico farmacéutico las crea en campos
 * estructurados y el auxiliar las usa en el mesón sin escribir la posología.
 */
@AutoConfigureMockMvc
class PlantillasDePosologiaTest : PruebaIntegracion() {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var contexto: ContextoComuna
    @Autowired lateinit var catalogo: CatalogoDeProductos

    private val providencia = CodigoComuna.de("13123")
    private val losartan = Gtin.de("7802250012344")
    private val fueraDelCatalogo = Gtin.de("7802250099901")

    private val cadaDoceHoras =
        """{"cantidad": 1, "unidad": "comprimido", "frecuencia": "PT12H", "duracionDias": 30, "indicaciones": "Con alimentos"}"""

    @BeforeEach
    fun datos() {
        limpiarDatos()
        contexto.en(providencia) {
            catalogo.guardar(Producto(providencia, losartan, "Losartán 50 mg", "Losartán potásico", "Comprimido", "50 mg"))
        }
    }

    @Test
    fun `el quimico farmaceutico crea una plantilla y el auxiliar la usa`() {
        crear(cadaDoceHoras).andExpect {
            status { isCreated() }
            jsonPath("$.id") { isNotEmpty() }
            jsonPath("$.cantidad") { value(1) }
            jsonPath("$.unidad") { value("comprimido") }
            jsonPath("$.frecuencia") { value("PT12H") }
            jsonPath("$.duracionDias") { value(30) }
            jsonPath("$.indicaciones") { value("Con alimentos") }
        }

        mvc.get(ruta(losartan)) {
            header("Authorization", "Bearer ${token(providencia.valor, rol = "auxiliar")}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].frecuencia") { value("PT12H") }
            jsonPath("$[0].indicaciones") { value("Con alimentos") }
        }
    }

    @Test
    fun `queda registrado quien la creo, por su identificador y no por su nombre o correo`() {
        crear(cadaDoceHoras, token(providencia.valor, rol = "qf", sujeto = "uid-del-qf")).andExpect { status { isCreated() } }

        assertEquals("uid-del-qf", administrador.queryForObject("SELECT creada_por FROM plantilla_posologia", String::class.java))
    }

    @Test
    fun `solo el quimico farmaceutico puede crear plantillas`() {
        for (rol in listOf("auxiliar", "administrador", null)) {
            crear(cadaDoceHoras, token(providencia.valor, rol = rol)).andExpect { status { isForbidden() } }
        }

        assertEquals(0, plantillasGuardadas())
    }

    @Test
    fun `una posologia incompleta o fuera de rango se rechaza con el motivo`() {
        val casos = mapOf(
            """{"cantidad": 0, "unidad": "comprimido", "frecuencia": "PT12H"}""" to
                "la cantidad por toma debe ser mayor que cero",
            """{"cantidad": 1, "unidad": "cucharada", "frecuencia": "PT12H"}""" to
                "unidad de dosis desconocida «cucharada»",
            """{"cantidad": 1, "unidad": "comprimido"}""" to
                "falta la frecuencia",
            """{"cantidad": 1, "unidad": "comprimido", "frecuencia": "PT12H", "duracionDias": 0}""" to
                "la duración va de 1 a 365 días",
        )
        for ((cuerpo, motivo) in casos) {
            crear(cuerpo).andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value(motivo) }
            }
        }

        assertEquals(0, plantillasGuardadas())
    }

    @Test
    fun `una plantilla identica a otra del mismo producto se rechaza`() {
        crear(cadaDoceHoras).andExpect { status { isCreated() } }
        crear(cadaDoceHoras).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("ya existe una plantilla con esa misma posología para este producto") }
        }

        // Con otra duración ya es otra plantilla.
        crear(cadaDoceHoras.replace("30", "90")).andExpect { status { isCreated() } }
        assertEquals(2, plantillasGuardadas())
    }

    @Test
    fun `sin duracion ni indicaciones tambien es una plantilla, y tampoco se repite`() {
        val inhalador = """{"cantidad": 2, "unidad": "inhalacion", "frecuencia": "PT6H"}"""

        crear(inhalador).andExpect {
            status { isCreated() }
            jsonPath("$.duracionDias") { value(null) }
            jsonPath("$.indicaciones") { value(null) }
        }
        crear(inhalador).andExpect { status { isConflict() } }
    }

    @Test
    fun `un producto que no esta en el catalogo responde 404`() {
        mvc.get(ruta(fueraDelCatalogo)) {
            header("Authorization", "Bearer ${token(providencia.valor)}")
        }.andExpect { status { isNotFound() } }

        crear(cadaDoceHoras, gtin = fueraDelCatalogo).andExpect { status { isNotFound() } }
    }

    private fun ruta(gtin: Gtin) = "/api/catalogo/productos/${gtin.valor}/plantillas"

    private fun crear(cuerpo: String, tokenDeQuien: String = token(providencia.valor, rol = "qf"), gtin: Gtin = losartan) =
        mvc.post(ruta(gtin)) {
            header("Authorization", "Bearer $tokenDeQuien")
            contentType = MediaType.APPLICATION_JSON
            content = cuerpo
        }

    private fun plantillasGuardadas(): Int =
        administrador.queryForObject("SELECT count(*) FROM plantilla_posologia", Int::class.java)!!
}
