package cl.midosis.paciente.tratamiento

import cl.midosis.credencial.Producto
import cl.midosis.credencial.Via
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class ResumenTest {

    private val santiago = ZoneId.of("America/Santiago")
    private val retiro = LocalDate.of(2026, 10, 15)
    private val emitidoEn = ZonedDateTime.of(retiro.atTime(11, 30), santiago).toEpochSecond()

    /** El caso de la historia: 180 días indicados y 60 cubiertos. */
    private fun producto(du: Int = 180, e: Int = 60, f: String = "PT24H") = Producto(
        gtin = "7802250012344",
        etiqueta = "Losartán 50 mg comp.",
        principioActivo = "Losartán",
        dosis = "1 comprimido",
        intervalo = f,
        duracionIndicadaDias = du,
        dosisEntregadas = e,
        coberturaDias = 0,
        via = Via.ORAL,
    )

    private fun resumen(hoy: LocalDate, p: Producto = producto()) = Resumen.de(p, emitidoEn, hoy, santiago)

    @Test
    fun `muestra las dos cifras distintas del criterio de aceptación`() {
        val r = resumen(retiro)
        assertEquals(180, r.indicadoDias)
        assertEquals(60, r.cubiertoDias)
        assertEquals(LocalDate.of(2026, 12, 14), r.alcanzaHasta)
        assertEquals(60, r.quedanDias)
        assertEquals(Resumen.Estado.AL_DIA, r.estado)
    }

    @Test
    fun `avisa cuándo volver con la anticipación del motor`() {
        // El aviso es 5 días antes del agotamiento (RN-07, regla clínica del motor).
        assertEquals(Resumen.Estado.AL_DIA, resumen(LocalDate.of(2026, 12, 8)).estado)
        assertEquals(Resumen.Estado.VOLVER_PRONTO, resumen(LocalDate.of(2026, 12, 9)).estado)
    }

    @Test
    fun `cuando se acaba lo retirado no da el tratamiento por terminado`() {
        val r = resumen(LocalDate.of(2026, 12, 20))
        assertEquals(Resumen.Estado.SE_ACABO, r.estado)
        assertEquals(0, r.quedanDias)
    }

    @Test
    fun `si lo retirado completa lo indicado no pide volver`() {
        assertEquals(Resumen.Estado.COMPLETO, resumen(retiro, producto(du = 30, e = 30)).estado)
    }

    @Test
    fun `el día de retiro es el de la emisión en la hora del teléfono`() {
        // 23:30 en Santiago ya es el día siguiente en UTC: cuenta la fecha local.
        val tarde = ZonedDateTime.of(retiro.atTime(23, 30), santiago).toEpochSecond()
        assertEquals(retiro, Resumen.de(producto(), tarde, retiro, santiago).retiro)
    }
}
