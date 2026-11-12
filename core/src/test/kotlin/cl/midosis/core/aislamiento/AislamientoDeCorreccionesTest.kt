package cl.midosis.core.aislamiento

import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.core.infraestructura.persistencia.ContextoComuna
import cl.midosis.core.infraestructura.persistencia.ContextoConsulta
import cl.midosis.core.soporte.PruebaIntegracion
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.simple.JdbcClient

/** Aislamiento de las correcciones (ADR-011, HU-33) y de la consulta de estado sin cuenta (RF-B-15). */
class AislamientoDeCorreccionesTest : PruebaIntegracion() {

    @Autowired lateinit var comunas: ContextoComuna
    @Autowired lateinit var consulta: ContextoConsulta
    @Autowired lateinit var jdbc: JdbcClient

    private val providencia = CodigoComuna.de("13123")
    private val santiago = CodigoComuna.de("13101")

    private val origProvidencia = "01M0000000000000000000000A"
    private val reemProvidencia = "01M0000000000000000000000B"
    private val origSantiago = "01M0000000000000000000000C"
    private val reemSantiago = "01M0000000000000000000000D"

    @BeforeEach
    fun datos() {
        limpiarDatos()
        for ((id, comuna) in listOf(origProvidencia to "13123", reemProvidencia to "13123", origSantiago to "13101", reemSantiago to "13101")) {
            administrador.update(
                "INSERT INTO emision (id, comuna, punto, expira_en, emitida_por, kid, consentimiento_version, consentimiento_en, productos) " +
                    "VALUES (?, ?, 'CL-FP-' || ? || '-01', now(), 'u', 'k', 'v', now(), '[]'::jsonb)",
                id, comuna, comuna,
            )
        }
        administrador.update(
            "INSERT INTO correccion (original, reemplazo, comuna, motivo, instruccion, corregida_por) VALUES (?, ?, '13123', 'm', 'continuar', 'u')",
            origProvidencia, reemProvidencia,
        )
        administrador.update(
            "INSERT INTO correccion (original, reemplazo, comuna, motivo, instruccion, corregida_por) VALUES (?, ?, '13101', 'm', 'suspender', 'u')",
            origSantiago, reemSantiago,
        )
    }

    private fun contar(): Int = jdbc.sql("SELECT count(*) FROM correccion").query(Int::class.java).single()

    @Test
    fun `cada comuna ve solo sus correcciones`() {
        assertEquals(1, comunas.en(providencia) { contar() })
        assertEquals(1, comunas.en(santiago) { contar() })
        assertEquals(0, contar())
    }

    @Test
    fun `una comuna no puede registrar correcciones a nombre de otra`() {
        administrador.update("DELETE FROM correccion WHERE original = ?", origSantiago)
        assertFailsWith<DataAccessException> {
            comunas.en(providencia) {
                jdbc.sql(
                    "INSERT INTO correccion (original, reemplazo, comuna, motivo, instruccion, corregida_por) " +
                        "VALUES ('$origSantiago', '$reemSantiago', '13101', 'm', 'continuar', 'u')",
                ).update()
            }
        }
    }

    @Test
    fun `una corrección no se puede editar ni borrar desde la aplicación`() {
        assertFailsWith<DataAccessException> {
            comunas.en(providencia) { jdbc.sql("UPDATE correccion SET instruccion = 'suspender'").update() }
        }
        assertFailsWith<DataAccessException> {
            comunas.en(providencia) { jdbc.sql("DELETE FROM correccion").update() }
        }
    }

    @Test
    fun `la consulta de estado ve solo el código que declara`() {
        val visibles = consulta.en(origProvidencia) {
            jdbc.sql("SELECT count(*) FROM emision").query(Int::class.java).single() to contar()
        }
        assertEquals(1 to 1, visibles)
    }

    @Test
    fun `la consulta de estado no puede escribir`() {
        val cambiadas = consulta.en(origProvidencia) {
            jdbc.sql("UPDATE emision SET revocada_en = now() WHERE id = '$origProvidencia'").update()
        }
        assertEquals(0, cambiadas)
        assertEquals(0, administrador.queryForObject("SELECT count(*) FROM emision WHERE revocada_en IS NOT NULL", Int::class.java))
    }
}
