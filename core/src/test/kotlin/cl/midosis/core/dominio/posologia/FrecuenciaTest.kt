package cl.midosis.core.dominio.posologia

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FrecuenciaTest {

    @Test
    fun `acepta las duraciones ISO 8601 de uso corriente`() {
        assertEquals(Duration.ofHours(12), Frecuencia.de("PT12H").intervalo)
        assertEquals(Duration.ofHours(8), Frecuencia.de("PT8H").intervalo)
        assertEquals(Duration.ofDays(1), Frecuencia.de("P1D").intervalo)
    }

    @Test
    fun `rechaza lo que no es una duracion`() {
        assertFailsWith<FrecuenciaInvalida> { Frecuencia.de("cada 12 horas") }
        assertFailsWith<FrecuenciaInvalida> { Frecuencia.de("") }
        assertFailsWith<FrecuenciaInvalida> { Frecuencia.de("12H") }
    }

    @Test
    fun `rechaza intervalos fuera del rango admitido`() {
        assertFailsWith<FrecuenciaInvalida> { Frecuencia.de("PT1M") }
        assertFailsWith<FrecuenciaInvalida> { Frecuencia.de("P365D") }
    }

    @Test
    fun `una semana es P7D, porque el formato no admite semanas`() {
        assertEquals(Duration.ofDays(7), Frecuencia.de("P7D").intervalo)
        assertFailsWith<FrecuenciaInvalida> { Frecuencia.de("P1W") }
    }

    @Test
    fun `calcula cuantas dosis caben en un lapso`() {
        assertEquals(2, Frecuencia.cadaHoras(12).dosisEn(Duration.ofDays(1)))
        assertEquals(3, Frecuencia.cadaHoras(8).dosisEn(Duration.ofDays(1)))
        assertEquals(30, Frecuencia.de("P1D").dosisEn(Duration.ofDays(30)))
    }
}
