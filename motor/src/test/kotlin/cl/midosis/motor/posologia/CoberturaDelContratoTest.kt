package cl.midosis.motor.posologia

import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * El motor calcula la cobertura igual que dicen los vectores del contrato
 * (contrato/vectores/cobertura.json). La web y la aplicación móvil corren los
 * mismos casos: si alguno redondea distinto, el paciente y el mesón verían
 * fechas de agotamiento diferentes para el mismo retiro.
 */
class CoberturaDelContratoTest {

    private val casos: List<JsonNode> by lazy {
        val directorio = System.getProperty("midosis.vectores")
            ?: error("falta la propiedad midosis.vectores; la fija build.gradle.kts")
        JsonMapper.builder().build()
            .readTree(File(directorio, "cobertura.json"))
            .get("casos")
            .toList()
    }

    @Test
    fun `el archivo de vectores trae casos`() {
        assertTrue(casos.size >= 5, "cobertura.json tiene ${casos.size} casos")
    }

    @TestFactory
    fun `cada caso del contrato`(): List<DynamicTest> = casos.map { caso ->
        DynamicTest.dynamicTest(caso.get("nombre").asString()) {
            val tramo = TramoDispensado(
                duracionIndicadaDias = caso.get("du").asInt(),
                dosisEntregadas = caso.get("e").asInt(),
                frecuencia = Frecuencia.de(caso.get("f").asString()),
            )
            val retiro = LocalDate.parse(caso.get("retiro").asString())
            val esperado = caso.get("esperado")

            assertEquals(esperado.get("co").asInt(), tramo.coberturaDias, "co")
            assertEquals(LocalDate.parse(esperado.get("agotamiento").asString()), tramo.seAgotaEl(retiro), "agotamiento")
            assertEquals(esperado.get("completaLoIndicado").asBoolean(), tramo.completaLoIndicado, "completaLoIndicado")
            assertEquals(esperado.get("diasPendientes").asInt(), tramo.diasPendientes, "diasPendientes")
            assertEquals(esperado.get("excedeLoIndicado").asBoolean(), tramo.excedeLoIndicado, "excedeLoIndicado")
        }
    }
}
