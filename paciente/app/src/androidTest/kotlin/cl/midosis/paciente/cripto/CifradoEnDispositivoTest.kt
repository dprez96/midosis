package cl.midosis.paciente.cripto

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cl.midosis.paciente.alarmas.AlmacenDelPlan
import cl.midosis.paciente.alarmas.SolicitudDeAlarma
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Pruebas en un teléfono real, contra el almacén de claves de Android.
 *
 * Existen porque las pruebas en la computadora usan una clave común y no pueden detectar
 * las restricciones del almacén: así se escapó el cierre de la aplicación por
 * "Caller-provided IV not permitted". Se ejecutan con un teléfono conectado:
 *   ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class CifradoEnDispositivoTest {

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private val almacen = AlmacenDelPlan(contexto)

    @After
    fun limpiar() {
        almacen.vaciar()
    }

    @Test
    fun cifraYDescifraConLaClaveDelAlmacenDeAndroid() {
        val cifrador = Cifrador(ClaveDelDispositivo::obtener)
        val datos = "Losartán 50 mg · 1 comprimido".toByteArray(Charsets.UTF_8)

        val cifrado = cifrador.cifrar(datos)

        assertContentEquals(datos, cifrador.descifrar(cifrado))
        assertFalse(String(cifrado, Charsets.ISO_8859_1).contains("Losart"))
    }

    @Test
    fun elPlanGuardadoSeRecuperaDelArchivoCifrado() {
        val ahora = LocalDateTime.now()
        val plan = listOf(
            SolicitudDeAlarma(1, ahora.plusHours(1), listOf("Losartán 50 mg · 1 comprimido")),
            SolicitudDeAlarma(2, ahora.plusHours(2), listOf("Metformina 850 mg · 1 comprimido")),
        )

        almacen.agregar(plan, ahora)

        assertEquals(plan, AlmacenDelPlan(contexto).leer())
    }

    @Test
    fun lasAlarmasPasadasNoSeGuardan() {
        val ahora = LocalDateTime.now()
        almacen.agregar(
            listOf(
                SolicitudDeAlarma(1, ahora.minusMinutes(5), listOf("Ya pasó")),
                SolicitudDeAlarma(2, ahora.plusMinutes(5), listOf("Todavía no")),
            ),
            ahora,
        )

        assertEquals(listOf(2), almacen.leer().map { it.id })
    }
}
