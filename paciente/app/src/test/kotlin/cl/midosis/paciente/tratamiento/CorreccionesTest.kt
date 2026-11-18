package cl.midosis.paciente.tratamiento

import cl.midosis.credencial.Carga
import cl.midosis.credencial.Producto
import cl.midosis.credencial.Via
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.json.JSONObject

class CorreccionesTest {

    private val zona = ZoneOffset.UTC
    private val cargado = LocalDateTime.of(2026, 11, 1, 10, 0)

    private fun producto(etiqueta: String, entregadas: Int, intervalo: String = "PT12H") = Producto(
        gtin = "7802250012344", etiqueta = etiqueta, principioActivo = etiqueta, dosis = "1 comprimido", intervalo = intervalo,
        duracionIndicadaDias = 30, dosisEntregadas = entregadas, coberturaDias = 0, via = Via.ORAL, observaciones = emptyList(),
    )

    private val tratamiento = TratamientoGuardado(
        carga = Carga("13123", "CL-FP-13123-01", "01M0877000WQNC8FHDYAA6Y83C", 0, 72 * 3600, listOf(producto("Losartán 50 mg", 20))),
        cargadoEn = cargado.toEpochSecond(zona),
        kid = "prueba-1",
    )

    @Test
    fun `la referencia son los últimos 8 caracteres en dos grupos`() {
        assertEquals("YAA6-Y83C", Correcciones.referencia(tratamiento.carga.id))
    }

    @Test
    fun `la consulta envía solo los jti, hasta 20`() {
        val cuerpo = JSONObject(Correcciones.consulta(List(25) { "01M0877000WQNC8FHDYAA6Y8%02d".format(it) }))
        assertEquals(listOf("ids"), cuerpo.names().let { n -> List(n.length()) { n.getString(it) } })
        assertEquals(20, cuerpo.getJSONArray("ids").length())
    }

    @Test
    fun `interpreta la respuesta de core`() {
        val estados = Correcciones.interpretar(
            """{"codigos": [
                {"id": "A", "estado": "vigente"},
                {"id": "B", "estado": "corregido", "instruccion": "suspender"},
                {"id": "C", "estado": "corregido", "instruccion": "continuar"},
                {"id": "D", "estado": "revocado"},
                {"id": "E", "estado": "desconocido"}
            ]}""",
        )
        assertEquals(EstadoDeCodigo.Vigente, estados["A"])
        assertEquals(EstadoDeCodigo.Corregido(suspender = true), estados["B"])
        assertEquals(EstadoDeCodigo.Corregido(suspender = false), estados["C"])
        assertEquals(EstadoDeCodigo.Revocado, estados["D"])
        assertEquals(EstadoDeCodigo.Desconocido, estados["E"])
        assertEquals(emptyMap(), Correcciones.interpretar("no es json"))
    }

    @Test
    fun `las tomas de un tratamiento se reconocen por su texto y dentro de lo entregado`() {
        val esDe = Correcciones.esDe(tratamiento, zona)
        val linea = Correcciones.linea("Losartán 50 mg", "1 comprimido")
        // 20 tomas cada 12 horas: cubre 10 días, hasta el 11/11 a las 10:00, más un día de margen.
        assertTrue(esDe(linea, cargado.plusDays(3)))
        assertTrue(esDe(linea, cargado.plusDays(10).plusHours(20)))
        assertFalse(esDe(linea, cargado.plusDays(11).plusHours(1)), "una toma de un tramo posterior no se quita")
        assertFalse(esDe(Correcciones.linea("Metformina 850 mg", "1 comprimido"), cargado.plusDays(3)))
    }

    @Test
    fun `el almacén de estados guarda solo correcciones y revocaciones`() {
        val archivo = File(Files.createTempDirectory("estados").toFile(), "estados.txt")
        val almacen = AlmacenDeEstados(archivo)
        almacen.actualizar(
            mapOf(
                "A" to EstadoDeCodigo.Vigente,
                "B" to EstadoDeCodigo.Corregido(suspender = true),
                "C" to EstadoDeCodigo.Revocado,
            ),
        )
        assertEquals(mapOf("B" to EstadoDeCodigo.Corregido(suspender = true), "C" to EstadoDeCodigo.Revocado), almacen.leer())
        almacen.quitar("B")
        assertEquals(mapOf("C" to EstadoDeCodigo.Revocado), almacen.leer())
    }

    @Test
    fun `las alarmas desactivadas por el paciente se recuerdan aunque core vuelva a decir suspender`() {
        val almacen = AlmacenDeEstados(File(Files.createTempDirectory("estados").toFile(), "estados.txt"))
        almacen.actualizar(mapOf("B" to EstadoDeCodigo.Corregido(suspender = true)))
        assertEquals(EstadoDeCodigo.Corregido(suspender = true), almacen.leer()["B"])
        almacen.marcarAlarmasDesactivadas("B")
        almacen.actualizar(mapOf("B" to EstadoDeCodigo.Corregido(suspender = true)))
        assertEquals(EstadoDeCodigo.Corregido(suspender = true, alarmasDesactivadas = true), almacen.leer()["B"])
    }
}
