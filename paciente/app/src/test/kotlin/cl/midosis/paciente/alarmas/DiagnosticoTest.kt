package cl.midosis.paciente.alarmas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiagnosticoTest {

    private fun d(notificaciones: Boolean = true, exactas: Boolean = true, bateria: Boolean = true, fabricante: String = "Google") =
        Diagnostico(notificaciones, exactas, bateria, fabricante)

    @Test
    fun `sin notificaciones o sin alarmas exactas las alarmas no están garantizadas`() {
        assertFalse(d(notificaciones = false).alarmasGarantizadas)
        assertFalse(d(exactas = false).alarmasGarantizadas)
        assertTrue(d().alarmasGarantizadas)
    }

    @Test
    fun `el ahorro de batería no quita la garantía pero sí pide revisar`() {
        val conAhorro = d(bateria = false)
        assertTrue(conAhorro.alarmasGarantizadas)
        assertFalse(conAhorro.todoEnOrden)
    }

    @Test
    fun `da el consejo del fabricante que cierra aplicaciones`() {
        assertEquals(true, d(fabricante = "samsung").consejoDelFabricante?.contains("suspensión profunda"))
        assertEquals(true, d(fabricante = "Xiaomi").consejoDelFabricante?.contains("Sin restricciones"))
        assertNull(d(fabricante = "Google").consejoDelFabricante)
    }
}
