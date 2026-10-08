package cl.midosis.credencial

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class CargaTest {

    @TestFactory
    fun `cada vector de la carga útil`(): List<DynamicTest> {
        val casos = Vectores.deCarga()
        assertTrue(casos.size >= 30, "se encontraron ${casos.size} vectores de carga")
        return casos.map { (nombre, vector) ->
            DynamicTest.dynamicTest(nombre) {
                val datos = hex(vector.get("cbor").asString())
                val esperado = vector.get("resultado")
                val obtenido = try {
                    CodecDeCarga.leer(datos)
                } catch (e: CodigoRechazado) {
                    if (esperado.get("valido").asBoolean()) fail("se rechazó con ${e.message}")
                    assertEquals(esperado.get("motivo").asString(), e.motivo.codigo, "motivo")
                    assertEquals(esperado.get("regla").asString(), e.regla, "regla")
                    return@dynamicTest
                }
                if (!esperado.get("valido").asBoolean()) fail("se aceptó; se esperaba $esperado")
                // Volver a codificar la carga da los mismos bytes: la codificación es determinista.
                assertEquals(datos.hex(), CodecDeCarga.codificar(obtenido).hex())
            }
        }
    }

    @TestFactory
    fun `la cobertura coincide con cobertura json`(): List<DynamicTest> =
        Vectores.leer("cobertura.json").get("casos").toList().map { caso ->
            DynamicTest.dynamicTest(caso.get("nombre").asString()) {
                assertEquals(
                    caso.get("esperado").get("co").asInt(),
                    CodecDeCarga.coberturaDias(caso.get("e").asInt(), caso.get("f").asString()),
                )
            }
        }

    @Test
    fun `lee los campos del ejemplo del informe`() {
        val carga = CodecDeCarga.leer(hex(Vectores.leer("valido-losartan-fraccionado.json").get("cbor").asString()))
        val losartan = carga.productos.single()
        assertEquals("13123", carga.comuna)
        assertEquals("CL-FP-13123-01", carga.emisor)
        assertEquals(180, losartan.duracionIndicadaDias)
        assertEquals(60, losartan.dosisEntregadas)
        assertEquals(30, losartan.coberturaDias)
        assertEquals(Via.ORAL, losartan.via)
        assertEquals(listOf(Observacion.CON_ALIMENTOS), losartan.observaciones)
    }
}
