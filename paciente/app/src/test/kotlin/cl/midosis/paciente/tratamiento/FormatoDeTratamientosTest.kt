package cl.midosis.paciente.tratamiento

import cl.midosis.credencial.CodecDeCarga
import cl.midosis.paciente.ui.describirIntervalo
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.json.JSONObject

class FormatoDeTratamientosTest {

    private val vectores = File(System.getProperty("midosis.vectores") ?: error("falta midosis.vectores"))

    private fun carga(nombre: String) =
        CodecDeCarga.leer(hex(JSONObject(File(vectores, "$nombre.json").readText()).getString("cbor")))

    private fun hex(t: String) = ByteArray(t.length / 2) { t.substring(2 * it, 2 * it + 2).toInt(16).toByte() }

    @Test
    fun `los tratamientos guardados van y vuelven sin cambios`() {
        val guardados = listOf(
            TratamientoGuardado(carga("valido-losartan-fraccionado"), 1786986000, "prueba-1"),
            TratamientoGuardado(carga("valido-varios-productos"), 1786990000, "prueba-2"),
        )
        assertEquals(guardados, FormatoDeTratamientos.leer(FormatoDeTratamientos.escribir(guardados)))
    }

    @Test
    fun `lo guardado no trae ningún dato del paciente`() {
        // Solo la carga del contrato, cuándo se cargó y la clave: nada que identifique a nadie.
        val linea = FormatoDeTratamientos.escribir(listOf(TratamientoGuardado(carga("valido-minimo"), 1, "prueba-1")))
        assertEquals(3, linea.split(" ").size)
    }

    @Test
    fun `una línea dañada no se acepta en silencio`() {
        assertFailsWith<IllegalArgumentException> { FormatoDeTratamientos.leer("abc 1") }
    }

    @Test
    fun `el intervalo se lee en palabras`() {
        assertEquals("cada 12 horas", describirIntervalo("PT12H"))
        assertEquals("una vez al día", describirIntervalo("PT24H"))
        assertEquals("cada 7 días", describirIntervalo("PT168H"))
        assertEquals("cada 1 h 30 min", describirIntervalo("PT1H30M"))
    }
}
