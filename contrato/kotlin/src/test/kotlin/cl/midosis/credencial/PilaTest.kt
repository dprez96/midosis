package cl.midosis.credencial

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class PilaTest {

    private val claves = Vectores.claves()

    @TestFactory
    fun `cada vector del código completo`(): List<DynamicTest> {
        val casos = Vectores.deCodigo()
        assertTrue(casos.size >= 20, "se encontraron ${casos.size} vectores de código")
        return casos.map { (nombre, vector) ->
            DynamicTest.dynamicTest(nombre) {
                val lector = LectorDeCodigos(claves) { vector.get("ahora").asLong() }
                val lectura = lector.leer(vector.get("codigo").asString())
                val esperado = vector.get("resultado")
                if (esperado.get("valido").asBoolean()) {
                    val aceptado = assertIs<Lectura.Aceptado>(lectura, "se esperaba aceptado")
                    assertEquals(vector.get("capas").get("carga").asString(), CodecDeCarga.codificar(aceptado.carga).hex())
                } else {
                    val rechazado = assertIs<Lectura.Rechazado>(lectura, "se esperaba $esperado")
                    assertEquals(esperado.get("motivo").asString(), rechazado.motivo.codigo, "motivo")
                    assertEquals(esperado.get("regla").asString(), rechazado.regla, "regla")
                }
            }
        }
    }

    @TestFactory
    fun `la firma coincide byte a byte con la referencia`(): List<DynamicTest> =
        listOf(
            "codigo-valido-losartan" to "prueba-1",
            "codigo-valido-varios-productos" to "prueba-1",
            "codigo-valido-segunda-clave" to "prueba-2",
        ).map { (nombre, kid) ->
            DynamicTest.dynamicTest(nombre) {
                // Ed25519 es determinista (RFC 8032): misma clave y mismos bytes, misma firma.
                val capas = Vectores.leer("$nombre.json").get("capas")
                val emisor = EmisorDeCodigos(Vectores.firmante(kid), Vectores.contenido("prueba-c1"))
                assertEquals(capas.get("firmado").asString(), emisor.firmar(hex(capas.get("carga").asString())).hex())
            }
        }

    @Test
    fun `el cifrado coincide byte a byte con la referencia`() {
        // Con el mismo IV, AES-GCM es determinista.
        val capas = Vectores.leer("codigo-valido-losartan.json").get("capas")
        val emisor = EmisorDeCodigos(
            Vectores.firmante("prueba-1"),
            Vectores.contenido("prueba-c1"),
            AleatorioFijo(hex(capas.get("iv").asString())),
        )
        assertEquals(capas.get("cifrado").asString(), emisor.cifrar(hex(capas.get("comprimido").asString())).hex())
    }

    @Test
    fun `lo que se emite se vuelve a leer igual`() {
        val carga = CodecDeCarga.leer(hex(Vectores.leer("valido-varios-productos.json").get("cbor").asString()))
        val codigo = EmisorDeCodigos(Vectores.firmante("prueba-1"), Vectores.contenido("prueba-c1")).emitir(carga)

        assertTrue(codigo.startsWith("MD1:"))
        val lectura = LectorDeCodigos(claves) { carga.emitidoEn + 60 }.leer(codigo)
        assertEquals(Lectura.Aceptado(carga, "prueba-1"), lectura)
    }

    @Test
    fun `dos emisiones de la misma carga no repiten el IV`() {
        val carga = CodecDeCarga.leer(hex(Vectores.leer("valido-minimo.json").get("cbor").asString()))
        val emisor = EmisorDeCodigos(Vectores.firmante("prueba-1"), Vectores.contenido("prueba-c1"))
        assertTrue(emisor.emitir(carga) != emisor.emitir(carga))
    }

    @Test
    fun `cabe en el QR del comprobante`() {
        // 25 mm o más a 0,40 mm por módulo (tabla 87). Con corrección M, la versión 15
        // admite 567 caracteres alfanuméricos y mide 77 módulos: unos 31 mm.
        val codigo = Vectores.leer("codigo-valido-losartan.json").get("codigo").asString()
        assertTrue(codigo.length <= 567, "el código de un producto ocupa ${codigo.length} caracteres")
    }
}
