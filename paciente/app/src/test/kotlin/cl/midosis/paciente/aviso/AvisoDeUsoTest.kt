package cl.midosis.paciente.aviso

import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AvisoDeUsoTest {

    private fun almacen(): Pair<AlmacenDeAviso, File> {
        val archivo = File(Files.createTempDirectory("aviso").toFile(), "aviso-de-uso.txt")
        return AlmacenDeAviso(archivo) to archivo
    }

    @Test
    fun `la primera vez el aviso se muestra`() {
        val (a, _) = almacen()
        assertNull(a.versionLeida())
        assertTrue(a.debeMostrarse())
    }

    @Test
    fun `leído en la versión vigente ya no se muestra y queda el momento`() {
        val (a, archivo) = almacen()
        a.registrarLectura(Instant.parse("2026-11-18T13:00:00Z"))
        assertEquals(AvisoDeUso.VERSION, a.versionLeida())
        assertFalse(a.debeMostrarse())
        assertEquals("${AvisoDeUso.VERSION};2026-11-18T13:00:00Z", archivo.readText())
    }

    @Test
    fun `leído en una versión anterior del texto se vuelve a mostrar`() {
        val (a, archivo) = almacen()
        archivo.writeText("${AvisoDeUso.VERSION - 1};2026-01-01T00:00:00Z")
        assertTrue(a.debeMostrarse())
    }

    @Test
    fun `un archivo que no se entiende cuenta como no leído`() {
        val (a, archivo) = almacen()
        archivo.writeText("basura")
        assertTrue(a.debeMostrarse())
    }

    @Test
    fun `el aviso cubre usos, límites, farmacia, médico, urgencia y responsabilidad`() {
        val titulos = AvisoDeUso.SECCIONES.map { it.titulo }
        assertEquals(
            listOf(
                "Para qué sirve MiDosis", "Lo que MiDosis no hace", "Consulta en tu farmacia si",
                "Consulta a tu médico si", "Ante una urgencia", "Responsabilidad",
            ),
            titulos,
        )
        assertEquals(listOf("Ante una urgencia"), AvisoDeUso.SECCIONES.filter { it.urgente }.map { it.titulo })
        assertTrue(AvisoDeUso.SECCIONES.single { it.urgente }.puntos.any { "131" in it })
    }

    @Test
    fun `el texto no usa guion largo`() {
        val todo = AvisoDeUso.SECCIONES.flatMap { it.puntos + it.titulo } + AvisoDeUso.INTRODUCCION
        assertTrue(todo.none { "—" in it })
    }
}
