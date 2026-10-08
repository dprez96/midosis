package cl.midosis.paciente.codigo

import cl.midosis.credencial.Lectura
import cl.midosis.credencial.LectorDeCodigos
import cl.midosis.credencial.Motivo
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.json.JSONObject

class VerificacionDeCodigosTest {

    // Las pruebas de la computadora corren desde la carpeta del módulo app.
    private fun asset(conjunto: String, nombre: String) = File("src/$conjunto/assets/$nombre").readText()

    private val vectores = File(System.getProperty("midosis.vectores") ?: error("falta midosis.vectores"))
    private val clavesDePrueba = ClavesEmpaquetadas.desdeJson(asset("debug", ClavesEmpaquetadas.ARCHIVO), admiteClavesDePrueba = true)

    @Test
    fun `la compilación de producción no admite las claves de prueba`() {
        assertFailsWith<IllegalArgumentException> {
            ClavesEmpaquetadas.desdeJson(asset("debug", ClavesEmpaquetadas.ARCHIVO), admiteClavesDePrueba = false)
        }
    }

    @Test
    fun `el archivo de producción se carga sin claves de prueba`() {
        ClavesEmpaquetadas.desdeJson(asset("main", ClavesEmpaquetadas.ARCHIVO), admiteClavesDePrueba = false)
    }

    @Test
    fun `las claves de depuración son las de los vectores del contrato`() {
        val contrato = JSONObject(File(vectores, "claves-de-prueba.json").readText())
        val app = JSONObject(asset("debug", ClavesEmpaquetadas.ARCHIVO))
        val publicasContrato = contrato.getJSONArray("firma").objetos()
            .filter { it.getBoolean("enElConjunto") }.associate { it.getString("kid") to it.getString("publica") }
        val publicasApp = app.getJSONArray("emisores").objetos().associate { it.getString("kid") to it.getString("publica") }
        assertEquals(publicasContrato, publicasApp)
    }

    @Test
    fun `cada vector del código da el mismo resultado con las claves de la app`() {
        val archivos = vectores.listFiles { f -> f.name.startsWith("codigo-") }!!.sortedBy { it.name }
        assertTrue(archivos.size >= 20)
        for (archivo in archivos) {
            val vector = JSONObject(archivo.readText())
            val lectura = LectorDeCodigos(clavesDePrueba) { vector.getLong("ahora") }.leer(vector.getString("codigo"))
            val esperado = vector.getJSONObject("resultado")
            if (esperado.getBoolean("valido")) {
                assertIs<Lectura.Aceptado>(lectura, archivo.name)
            } else {
                val rechazado = assertIs<Lectura.Rechazado>(lectura, archivo.name)
                assertEquals(esperado.getString("motivo"), rechazado.motivo.codigo, archivo.name)
            }
        }
    }

    @Test
    fun `los rechazos que piden cosas distintas tienen mensajes distintos`() {
        val mensajes = Motivo.entries.map { MensajeDeRechazo.de(it) }
        // Los dos errores de la carga firmada piden lo mismo: volver a la farmacia.
        assertEquals(Motivo.entries.size - 1, mensajes.toSet().size)
        assertEquals(mensajes.toSet().size, mensajes.map { it.titulo }.toSet().size)
    }

    @Test
    fun `cada caso de la pantalla de prueba da el rechazo que anuncia`() {
        val ejemplos = CodigosDeEjemplo.desdeJson(asset("debug", "firmantes-de-prueba.json"), clavesDePrueba.contenido("prueba-c1")!!)
        val ahora = 1792022400L
        val lector = LectorDeCodigos(clavesDePrueba) { ahora }
        val esperado = mapOf(
            CodigosDeEjemplo.Caso.VALIDO to null,
            CodigosDeEjemplo.Caso.ALTERADO to Motivo.ALTERADO,
            CodigosDeEjemplo.Caso.EMISOR_DESCONOCIDO to Motivo.EMISOR_DESCONOCIDO,
            CodigosDeEjemplo.Caso.VENCIDO to Motivo.EXPIRADO,
            CodigosDeEjemplo.Caso.OTRO_QR to Motivo.NO_ES_DE_MIDOSIS,
            CodigosDeEjemplo.Caso.VERSION_FUTURA to Motivo.VERSION_NO_SOPORTADA,
        )
        assertEquals(CodigosDeEjemplo.Caso.entries.toSet(), esperado.keys)
        for ((caso, motivo) in esperado) {
            val lectura = lector.leer(ejemplos.generar(caso, ahora))
            if (motivo == null) {
                assertIs<Lectura.Aceptado>(lectura, caso.name)
            } else {
                assertEquals(motivo, assertIs<Lectura.Rechazado>(lectura, caso.name).motivo, caso.name)
            }
        }
    }

    @Test
    fun `el ULID de ejemplo tiene la forma que exige el contrato`() {
        repeat(50) {
            assertTrue(Regex("^[0-7][0-9A-HJKMNP-TV-Z]{25}$").matches(CodigosDeEjemplo.ulid(1792022400L)))
        }
    }
}
