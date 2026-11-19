package cl.midosis.paciente.perfiles

import cl.midosis.credencial.Carga
import cl.midosis.credencial.Producto
import cl.midosis.credencial.Via
import cl.midosis.paciente.alarmas.AlmacenDelPlan
import cl.midosis.paciente.alarmas.FormatoDelPlan
import cl.midosis.paciente.alarmas.PlanDeAlarmas
import cl.midosis.paciente.alarmas.SolicitudDeAlarma
import cl.midosis.paciente.tratamiento.Continuidad
import cl.midosis.paciente.tratamiento.FormatoDeTratamientos
import cl.midosis.paciente.tratamiento.TratamientoGuardado
import cl.midosis.motor.planificacion.TomaProgramada
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PerfilesTest {

    private val mama = Perfil("MAMA00000001", "Mamá", ColorDePerfil.VERDE)
    private val papa = Perfil("PAPA00000001", "Papá", ColorDePerfil.NARANJA)

    @Test
    fun `el apodo es corto, con letras y sin repetirse`() {
        assertNull(Perfiles.problemaDelApodo("Mamá", emptyList()))
        assertEquals("Escribe un apodo, por ejemplo «Mamá» o «Juan».", Perfiles.problemaDelApodo("  ", emptyList()))
        assertEquals("El apodo puede tener hasta 12 letras.", Perfiles.problemaDelApodo("Abuelita Rosa", emptyList()))
        assertEquals("Usa solo letras: no escribas el RUT ni números.", Perfiles.problemaDelApodo("Juan 12345", emptyList()))
        assertEquals("Ya hay un perfil con ese apodo.", Perfiles.problemaDelApodo("mamá", listOf(mama)))
        assertEquals('M', mama.inicial)
    }

    @Test
    fun `cada perfil nuevo toma el primer color libre`() {
        assertEquals(ColorDePerfil.AZUL, Perfiles.colorLibre(listOf(mama, papa)))
        assertEquals(ColorDePerfil.MORADO, Perfiles.colorLibre(listOf(Perfiles.principal(), mama, papa)))
    }

    @Test
    fun `los perfiles se guardan y se leen con cualquier apodo`() {
        val perfiles = listOf(Perfiles.principal(), mama, papa.copy(apodo = "Papá; tío"))
        assertEquals(perfiles, FormatoDePerfiles.leer(FormatoDePerfiles.escribir(perfiles)))
    }

    @Test
    fun `dos perfiles a la misma hora tienen avisos distintos y el principal conserva su número`() {
        val ocho = LocalDateTime.of(2026, 11, 20, 8, 0)
        assertEquals(PlanDeAlarmas.identificador(ocho), PlanDeAlarmas.identificador(ocho, 0))
        val ids = (0..3).map { PlanDeAlarmas.identificador(ocho, it) }
        assertEquals(4, ids.distinct().size)
        assertNotEquals(PlanDeAlarmas.identificador(ocho.plusMinutes(1), 0), PlanDeAlarmas.identificador(ocho, 1))
    }

    @Test
    fun `el aviso lleva el perfil y el plan anterior a los perfiles se sigue leyendo`() {
        val ocho = LocalDateTime.of(2026, 11, 20, 8, 0)
        val tomas = listOf(TomaProgramada("01M0000000000000000000000A", "Losartán 50 mg", "1 comprimido", ocho))
        val plan = PlanDeAlarmas.construir(tomas, ocho.minusHours(1), mama.id, 1)
        assertEquals(mama.id, plan.single().perfil)
        assertEquals(plan, FormatoDelPlan.leer(FormatoDelPlan.escribir(plan)))
        val antiguo = "1\n${PlanDeAlarmas.identificador(ocho)} ${b64(ocho.toString())} ${b64("Losartán 50 mg · 1 comprimido")}\n"
        assertEquals("", FormatoDelPlan.leer(antiguo).single().perfil)
    }

    @Test
    fun `un tratamiento guardado antes de los perfiles es del principal`() {
        val t = tratamiento("01M0000000000000000000000A", "Losartán", perfil = mama.id)
        val texto = FormatoDeTratamientos.escribir(listOf(t))
        assertEquals(listOf(t), FormatoDeTratamientos.leer(texto))
        val sinPerfil = texto.substringBeforeLast(" ")
        assertEquals("", FormatoDeTratamientos.leer(sinPerfil).single().perfil)
    }

    @Test
    fun `advierte si el mismo principio activo está en otro perfil`() {
        val dePapa = tratamiento("01M0000000000000000000000A", "Losartán", perfil = papa.id)
        val nuevo = tratamiento("01M0000000000000000000000B", "Losartán").carga
        val advertencias = Perfiles.principiosEnOtroPerfil(nuevo, mama.id, listOf(dePapa), listOf(mama, papa))
        assertEquals(listOf("Losartán" to papa), advertencias)
        assertTrue(Perfiles.principiosEnOtroPerfil(nuevo, papa.id, listOf(dePapa), listOf(mama, papa)).isEmpty())
    }

    @Test
    fun `el mismo medicamento en dos perfiles son dos tarjetas`() {
        val deMama = tratamiento("01M0000000000000000000000A", "Losartán", perfil = mama.id)
        val dePapa = tratamiento("01M0000000000000000000000B", "Losartán", perfil = papa.id)
        assertEquals(2, Continuidad.vigentes(listOf(deMama, dePapa)).size)
    }

    private fun b64(t: String) = java.util.Base64.getEncoder().encodeToString(t.toByteArray(Charsets.UTF_8))

    private fun tratamiento(id: String, principio: String, perfil: String = "") = TratamientoGuardado(
        carga = Carga(
            "13123", "CL-FP-13123-01", id, 1_790_000_000, 1_790_259_200,
            listOf(
                Producto(
                    gtin = "7802250012344", etiqueta = "$principio 50 mg", principioActivo = principio, dosis = "1 comprimido",
                    intervalo = "PT24H", duracionIndicadaDias = 180, dosisEntregadas = 30, coberturaDias = 30, via = Via.ORAL,
                    observaciones = emptyList(),
                ),
            ),
        ),
        cargadoEn = 1_790_000_100,
        kid = "prueba-1",
        perfil = perfil,
    )
}
