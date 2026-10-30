package cl.midosis.core.demanda

import cl.midosis.core.soporte.PruebaIntegracion
import java.time.LocalDate
import java.time.ZoneId
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/** La proyección de la demanda de retiros (HU-27): solo el químico farmacéutico la consulta. */
@AutoConfigureMockMvc
class DemandaTest : PruebaIntegracion() {

    @Autowired lateinit var mvc: MockMvc

    private val hace10Dias = LocalDate.now(ZoneId.of("America/Santiago")).minusDays(10)

    @BeforeEach
    fun datos() {
        limpiarDatos()
        // Losartán para 30 días de un tratamiento de 180: vuelve en unas 3 semanas.
        emision("01M0000000000000000000000A", "13123", """[{"gtin":"7802250012344","du":180,"e":30,"co":30,"u":30}]""")
        // Revocado: no fue un retiro y no anuncia demanda.
        emision("01M0000000000000000000000B", "13123", """[{"gtin":"7802250012405","du":180,"e":60,"co":30,"u":60}]""", revocada = true)
    }

    private fun emision(id: String, comuna: String, productos: String, revocada: Boolean = false) {
        administrador.update(
            "INSERT INTO emision (id, comuna, punto, emitida_en, expira_en, emitida_por, kid, consentimiento_version, " +
                "consentimiento_en, productos, revocada_en) VALUES (?, ?, 'CL-FP-' || ? || '-01', ?::date + time '12:00', now(), 'u', 'k', 'v', now(), ?::jsonb, " +
                (if (revocada) "now()" else "NULL") + ")",
            id, comuna, comuna, hace10Dias.toString(), productos,
        )
    }

    @Test
    fun `el quimico farmaceutico ve la demanda por producto y semana con el supuesto declarado`() {
        mvc.get("/api/demanda") { header("Authorization", "Bearer ${token("13123", rol = "qf")}") }.andExpect {
            status { isOk() }
            jsonPath("$.semanas.length()") { value(12) }
            jsonPath("$.productos.length()") { value(1) }
            jsonPath("$.productos[0].gtin") { value("7802250012344") }
            jsonPath("$.productos[0].unidades") { value(hasItem(30)) }
            jsonPath("$.productos[0].tasaDeRetorno") { value(1.0) }
            jsonPath("$.supuesto") { value(org.hamcrest.Matchers.containsString("tasa de retorno")) }
        }
    }

    @Test
    fun `un auxiliar no puede consultarla`() {
        mvc.get("/api/demanda") { header("Authorization", "Bearer ${token("13123", rol = "auxiliar")}") }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `otra comuna no ve la demanda de esta`() {
        mvc.get("/api/demanda") { header("Authorization", "Bearer ${token("13101", rol = "qf")}") }.andExpect {
            status { isOk() }
            jsonPath("$.productos.length()") { value(0) }
        }
    }
}
