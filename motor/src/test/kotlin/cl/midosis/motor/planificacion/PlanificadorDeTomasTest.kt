package cl.midosis.motor.planificacion

import cl.midosis.motor.posologia.Frecuencia
import cl.midosis.motor.rutina.Rutina
import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlanificadorDeTomasTest {

    /** Rutina por defecto: despierta 07:00, comidas 08:00, 13:00 y 20:00, se acuesta 23:00. */
    private val planificador = PlanificadorDeTomas(Rutina.POR_DEFECTO)
    private val miercoles = LocalDateTime.of(2026, 10, 14, 0, 0)

    private fun a(hora: String, dias: Long = 0) = LocalDateTime.parse("2026-10-14T$hora").plusDays(dias)

    private fun plan(cada: String, dosis: Int, desde: String, vararg r: Restriccion) =
        planificador.planificar(Frecuencia.de(cada), dosis, r.toSet(), a(desde))

    @Test
    fun `la primera toma va en el siguiente cuarto de hora`() {
        assertEquals(a("10:00"), PlanificadorDeTomas.siguienteCuartoDeHora(a("10:00")))
        assertEquals(a("10:15"), PlanificadorDeTomas.siguienteCuartoDeHora(a("10:00:01")))
        assertEquals(a("10:15"), PlanificadorDeTomas.siguienteCuartoDeHora(a("10:07")))
        assertEquals(a("11:00"), PlanificadorDeTomas.siguienteCuartoDeHora(a("10:46")))
        assertEquals(a("00:00", 1), PlanificadorDeTomas.siguienteCuartoDeHora(a("23:50")))
    }

    @Test
    fun `cada 8 horas o menos se reparte en el día aunque caiga de noche`() {
        assertEquals(listOf(a("10:15"), a("18:15"), a("02:15", 1)), plan("PT8H", 3, "10:07"))
    }

    @Test
    fun `cada 12 horas sin restricción parte de inmediato si ambas tomas caen despierto`() {
        assertEquals(listOf(a("10:15"), a("22:15")), plan("PT12H", 2, "10:07"))
    }

    @Test
    fun `cada 12 horas corre la fase para no despertar al paciente`() {
        // A las 14:00 la segunda toma caería a las 02:00: se atrasa a las 19:00, que deja la
        // siguiente a las 07:00, ya despierto.
        assertEquals(listOf(a("19:00"), a("07:00", 1)), plan("PT12H", 2, "14:00"))
    }

    @Test
    fun `con alimentos cada 12 horas ancla en una comida`() {
        // 13:00 dejaría la segunda a la 01:00; la cena de las 20:00 deja la siguiente en el desayuno.
        assertEquals(listOf(a("20:00"), a("08:00", 1)), plan("PT12H", 2, "10:07", Restriccion.CON_ALIMENTOS))
    }

    @Test
    fun `al acostarse va a la hora de acostarse`() {
        assertEquals(listOf(a("23:00"), a("23:00", 1)), plan("PT24H", 2, "10:07", Restriccion.AL_ACOSTARSE))
    }

    @Test
    fun `en ayunas va al despertar`() {
        assertEquals(a("07:00", 1), plan("PT24H", 1, "10:07", Restriccion.EN_AYUNAS).single())
    }

    @Test
    fun `nunca acorta el intervalo y planifica exactamente las dosis entregadas`() {
        for (cada in listOf("PT4H", "PT6H", "PT8H", "PT12H", "PT24H", "PT168H")) {
            for (desde in listOf("06:10", "13:40", "22:55")) {
                val tomas = plan(cada, 30, desde, Restriccion.CON_ALIMENTOS)
                assertEquals(30, tomas.size)
                val intervalo = Frecuencia.de(cada).intervalo
                assertTrue(tomas.zipWithNext().all { (x, y) -> Duration.between(x, y) == intervalo }, "$cada desde $desde")
                assertTrue(!tomas.first().isBefore(a(desde)), "no se planifica en el pasado")
            }
        }
    }

    @Test
    fun `una rutina sin hueco para dos tomas no posterga la primera más de dos días`() {
        // Despierto solo 10 horas: dos tomas cada 12 horas nunca caen ambas despierto.
        val corta = Rutina(
            java.time.LocalTime.of(9, 0), java.time.LocalTime.of(9, 30), java.time.LocalTime.of(13, 0),
            java.time.LocalTime.of(18, 0), java.time.LocalTime.of(19, 0),
        )
        val tomas = PlanificadorDeTomas(corta).planificar(Frecuencia.de("PT12H"), 2, emptySet(), miercoles.withHour(10))
        assertEquals(miercoles.withHour(10), tomas.first())
    }
}
