package cl.midosis.paciente.alarmas

import cl.midosis.credencial.Carga
import cl.midosis.credencial.Observacion
import cl.midosis.credencial.Producto
import cl.midosis.credencial.Via
import cl.midosis.paciente.tratamiento.TratamientoGuardado
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TextoDeLocucionTest {

    private fun producto(etiqueta: String, vararg obs: Observacion) = Producto(
        gtin = "7802250012344", etiqueta = etiqueta, principioActivo = etiqueta, dosis = "1 comprimido", intervalo = "PT24H",
        duracionIndicadaDias = 30, dosisEntregadas = 30, coberturaDias = 30, via = Via.ORAL, observaciones = obs.toList(),
    )

    private fun tratamiento(id: String, vararg productos: Producto) = TratamientoGuardado(
        carga = Carga("13123", "CL-FP-13123-01", id, 1_786_982_400, 1_787_241_600, productos.toList()),
        cargadoEn = 0,
        kid = "prueba-1",
    )

    @Test
    fun `lee el medicamento, la dosis y la indicacion de administracion`() {
        val texto = TextoDeLocucion.armar(
            listOf("Losartán 50 mg · 1 comprimido"),
            mapOf("Losartán 50 mg" to listOf(Observacion.CON_ALIMENTOS)),
        )
        assertEquals("Hora de tu medicamento. Losartán 50 miligramos, 1 comprimido, con alimentos.", texto)
    }

    @Test
    fun `con varios medicamentos los lee todos, cada uno con su indicacion`() {
        val texto = TextoDeLocucion.armar(
            listOf("Levotiroxina 100 mcg · 1 comprimido", "Metformina 850 mg · 1 comprimido"),
            mapOf("Levotiroxina 100 mcg" to listOf(Observacion.EN_AYUNAS)),
        )
        assertEquals(
            "Hora de tus medicamentos. Levotiroxina 100 microgramos, 1 comprimido, en ayunas. " +
                "Metformina 850 miligramos, 1 comprimido.",
            texto,
        )
    }

    @Test
    fun `sin indicaciones conocidas lee igual el medicamento y la dosis`() {
        assertEquals(
            "Hora de tu medicamento. Salbutamol 100 microgramos, 2 inhalaciones.",
            TextoDeLocucion.armar(listOf("Salbutamol 100 mcg · 2 inhalaciones")),
        )
    }

    @Test
    fun `no deja abreviaturas que la voz pronuncie letra por letra`() {
        val texto = TextoDeLocucion.armar(listOf("Jarabe 5 ml · 1 comp."))
        assertFalse(Regex("""\b(mg|mcg|ml)\b""").containsMatchIn(texto), texto)
        assertEquals("Hora de tu medicamento. Jarabe 5 mililitros, 1 comprimido.", texto)
    }

    @Test
    fun `junta las indicaciones de cada producto desde los tratamientos guardados`() {
        val indicaciones = TextoDeLocucion.indicacionesDe(
            listOf(
                tratamiento("01M0877000WQNC8FHDYAA6Y83C", producto("Losartán 50 mg", Observacion.CON_ALIMENTOS)),
                tratamiento("01M0877000WQNC8FHDYAA6Y83D", producto("Losartán 50 mg", Observacion.CON_ALIMENTOS, Observacion.NO_PARTIR_NI_MASTICAR)),
            ),
        )
        assertEquals(mapOf("Losartán 50 mg" to listOf(Observacion.CON_ALIMENTOS, Observacion.NO_PARTIR_NI_MASTICAR)), indicaciones)
    }

    @Test
    fun `cada observacion tiene una frase para leer`() {
        Observacion.entries.forEach { assertFalse(TextoDeLocucion.frase(it).isBlank(), it.name) }
    }
}
