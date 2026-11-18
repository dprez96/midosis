package cl.midosis.paciente.tratamiento

import cl.midosis.credencial.Carga
import cl.midosis.credencial.Producto
import cl.midosis.credencial.Via
import cl.midosis.motor.rutina.Rutina
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContinuidadTest {

    private val zona = ZoneOffset.UTC
    private fun s(texto: String) = LocalDateTime.parse(texto).toEpochSecond(zona)

    private val losartan = "7802250012344"
    private val metformina = "7802250012405"

    /** 180 días indicados, 60 comprimidos cada 12 horas: cubre 30 días. */
    private fun producto(gtin: String = losartan, etiqueta: String = "Losartán 50 mg", intervalo: String = "PT12H", dosis: String = "1 comprimido", entregadas: Int = 60) =
        Producto(
            gtin = gtin, etiqueta = etiqueta, principioActivo = etiqueta, dosis = dosis, intervalo = intervalo,
            duracionIndicadaDias = 180, dosisEntregadas = entregadas, coberturaDias = 30, via = Via.ORAL, observaciones = emptyList(),
        )

    private fun tratamiento(id: String, emitido: String, vararg productos: Producto, reemplazaA: String? = null) = TratamientoGuardado(
        carga = Carga("13123", "CL-FP-13123-01", id, s(emitido), s(emitido) + 72 * 3600, productos.toList(), reemplazaA),
        cargadoEn = s(emitido) + 600,
        kid = "prueba-1",
    )

    private val primero = tratamiento("01M0000000000000000000000A", "2026-11-01T10:00", producto())

    @Test
    fun `el tramo siguiente del mismo producto se detecta como continuación`() {
        val segundo = tratamiento("01M0000000000000000000000B", "2026-11-27T10:00", producto(), producto(metformina, "Metformina 850 mg"))
        val c = Continuidad.detectar(segundo.carga, listOf(primero), zona)
        assertEquals(1, c.size)
        assertEquals(primero, c.single().anterior)
        assertTrue(c.single().mismaPosologia)
    }

    @Test
    fun `una posología distinta se distingue`() {
        val segundo = tratamiento("01M0000000000000000000000B", "2026-11-27T10:00", producto(intervalo = "PT8H"))
        assertFalse(Continuidad.detectar(segundo.carga, listOf(primero), zona).single().mismaPosologia)
    }

    @Test
    fun `no continúa un tratamiento cuyo período indicado ya terminó ni el que un reemplazo corrige`() {
        val tarde = tratamiento("01M0000000000000000000000B", "2027-06-01T10:00", producto())
        assertTrue(Continuidad.detectar(tarde.carga, listOf(primero), zona).isEmpty())
        val reemplazo = tratamiento("01M0000000000000000000000C", "2026-11-02T10:00", producto(), reemplazaA = primero.carga.id)
        assertTrue(Continuidad.detectar(reemplazo.carga, listOf(primero), zona).isEmpty())
    }

    @Test
    fun `retirar antes de agotarse encadena la cobertura`() {
        // El primero alcanza hasta el 01-12; el segundo, retirado el 27-11, empieza a correr ahí.
        val segundo = tratamiento("01M0000000000000000000000B", "2026-11-27T10:00", producto())
        val todos = listOf(primero, segundo)
        assertEquals(LocalDate.of(2026, 12, 1), Continuidad.inicioDeCobertura(segundo, segundo.carga.productos[0], todos, zona))
        assertEquals(2, Continuidad.numeroDeTramo(segundo, segundo.carga.productos[0], todos, zona))
        val r = Resumen.de(segundo.carga.productos[0], segundo.carga.emitidoEn, LocalDate.of(2026, 11, 28), zona, inicio = LocalDate.of(2026, 12, 1))
        assertEquals(LocalDate.of(2026, 12, 31), r.alcanzaHasta)
    }

    @Test
    fun `con otra posología la cobertura nueva corre desde el retiro`() {
        val segundo = tratamiento("01M0000000000000000000000B", "2026-11-27T10:00", producto(intervalo = "PT8H", entregadas = 90))
        assertEquals(LocalDate.of(2026, 11, 27), Continuidad.inicioDeCobertura(segundo, segundo.carga.productos[0], listOf(primero, segundo), zona))
    }

    @Test
    fun `la pantalla principal muestra solo el último tramo de cada producto`() {
        val segundo = tratamiento("01M0000000000000000000000B", "2026-11-27T10:00", producto())
        val otro = tratamiento("01M0000000000000000000000C", "2026-11-05T10:00", producto(metformina, "Metformina 850 mg"))
        val vigentes = Continuidad.vigentes(listOf(primero, otro, segundo))
        assertEquals(listOf(otro.carga.id, segundo.carga.id), vigentes.map { it.first.carga.id })
    }

    @Test
    fun `las tomas siguen un intervalo después de la última pendiente del tramo anterior`() {
        val c = Continuacion(primero, producto(), producto())
        val ahora = LocalDateTime.parse("2026-11-27T10:00")
        assertEquals(LocalDateTime.parse("2026-12-01T09:00"), Continuidad.inicioDeTomas(c, LocalDateTime.parse("2026-11-30T21:00"), ahora))
        assertNull(Continuidad.inicioDeTomas(c, LocalDateTime.parse("2026-11-26T21:00"), ahora), "sin tomas pendientes empieza como un tramo nuevo")
        assertNull(Continuidad.inicioDeTomas(Continuacion(primero, producto(), producto(intervalo = "PT8H")), LocalDateTime.parse("2026-11-30T21:00"), ahora))
    }

    @Test
    fun `al continuar, el plan propuesto sigue la cadencia sin correr la fase`() {
        val segundo = tratamiento("01M0000000000000000000000B", "2026-11-27T10:00", producto(entregadas = 4))
        val plan = Planificacion.proponer(
            segundo, Rutina.POR_DEFECTO, LocalDateTime.parse("2026-11-27T10:15"),
            continuaDesde = mapOf(losartan to LocalDateTime.parse("2026-12-01T09:00")),
        )
        assertEquals(
            listOf("2026-12-01T09:00", "2026-12-01T21:00", "2026-12-02T09:00", "2026-12-02T21:00").map(LocalDateTime::parse),
            plan.single().plan.tomas,
        )
    }
}
