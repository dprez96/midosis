package cl.midosis.paciente.tratamiento

import cl.midosis.credencial.Carga
import cl.midosis.credencial.Observacion
import cl.midosis.credencial.Producto
import cl.midosis.credencial.Via
import cl.midosis.motor.planificacion.Restriccion
import cl.midosis.motor.rutina.Rutina
import cl.midosis.paciente.alarmas.PlanDeAlarmas
import cl.midosis.paciente.alarmas.ProgramadorDeAlarmas
import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlanificacionTest {

    private val ahora = LocalDateTime.of(2026, 10, 14, 10, 7)

    private fun producto(etiqueta: String, f: String, e: Int, o: List<Observacion> = emptyList()) = Producto(
        gtin = "7802250012344", etiqueta = etiqueta, principioActivo = etiqueta, dosis = "1 comprimido",
        intervalo = f, duracionIndicadaDias = 180, dosisEntregadas = e, coberturaDias = 0, via = Via.ORAL, observaciones = o,
    )

    private fun tratamiento(vararg productos: Producto) = TratamientoGuardado(
        carga = Carga("13123", "CL-FP-13123-01", "01M0877000WQNC8FHDYAA6Y83C", 0, 1, productos.toList()),
        cargadoEn = 0,
        kid = "prueba-1",
    )

    @Test
    fun `cada dosis entregada es una toma del tramo cubierto`() {
        val tomas = Planificacion.tomasDe(
            tratamiento(producto("Losartán", "PT12H", 60, listOf(Observacion.CON_ALIMENTOS)), producto("Atorvastatina", "PT24H", 30)),
            Rutina.POR_DEFECTO,
            ahora,
        )
        assertEquals(60, tomas.count { it.producto == "Losartán" })
        assertEquals(30, tomas.count { it.producto == "Atorvastatina" })
        assertTrue(tomas.all { it.tratamiento == "01M0877000WQNC8FHDYAA6Y83C" && it.dosis == "1 comprimido" })
        // Con alimentos cada 12 horas: cena y desayuno.
        assertEquals(LocalDateTime.of(2026, 10, 14, 20, 0), tomas.first { it.producto == "Losartán" }.momento)
    }

    @Test
    fun `solo las observaciones de horario mueven la toma`() {
        assertEquals(
            setOf(Restriccion.CON_ALIMENTOS, Restriccion.AL_ACOSTARSE),
            Planificacion.restricciones(listOf(Observacion.CON_ALIMENTOS, Observacion.AL_ACOSTARSE, Observacion.NO_PARTIR_NI_MASTICAR)),
        )
    }

    @Test
    fun `un tramo largo no supera el límite de alarmas de Android`() {
        // 180 días cada 8 horas son 540 tomas: más que las 500 alarmas que admite Android.
        val tomas = Planificacion.tomasDe(tratamiento(producto("Amoxicilina", "PT8H", 540)), Rutina.POR_DEFECTO, ahora)
        val plan = PlanDeAlarmas.construir(tomas, ahora)
        assertEquals(540, plan.size)
        val ventana = ProgramadorDeAlarmas.dentroDeLaVentana(plan, ahora)
        assertEquals(21, ventana.size)
        assertTrue(ventana.all { !it.momento.isAfter(ahora.plus(Duration.ofDays(7))) })
    }
}
