package cl.midosis.paciente.privacidad

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cl.midosis.motor.rutina.Rutina
import cl.midosis.paciente.alarmas.AlmacenDelPlan
import cl.midosis.paciente.alarmas.SolicitudDeAlarma
import cl.midosis.paciente.rutina.AlmacenDeRutina
import cl.midosis.paciente.rutina.RutinaGuardada
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La supresión en un teléfono real, con el almacén de claves de Android (HU-25):
 *   ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class SupresionEnDispositivoTest {

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun borraElPlanLaRutinaYTodoLoGuardado() {
        AlmacenDelPlan(contexto).agregar(
            listOf(SolicitudDeAlarma(1, LocalDateTime.now().plusDays(1), listOf("Losartán 50 mg · 1 comprimido"))),
            LocalDateTime.now(),
        )
        AlmacenDeRutina(contexto).guardar(RutinaGuardada(Rutina.POR_DEFECTO, porDefecto = true))

        Supresion.ejecutar(contexto)

        assertEquals(emptyList(), AlmacenDelPlan(contexto).leer())
        assertNull(AlmacenDeRutina(contexto).leer())
        assertTrue(contexto.noBackupFilesDir.listFiles().orEmpty().isEmpty())
        assertTrue(contexto.filesDir.listFiles().orEmpty().isEmpty())
    }
}
