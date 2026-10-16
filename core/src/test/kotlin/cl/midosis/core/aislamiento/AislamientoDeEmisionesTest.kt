package cl.midosis.core.aislamiento

import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.core.infraestructura.persistencia.ContextoComuna
import cl.midosis.core.soporte.PruebaIntegracion
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.simple.JdbcClient

/** Aislamiento del registro de emisiones (ADR-011, HU-01). */
class AislamientoDeEmisionesTest : PruebaIntegracion() {

    @Autowired lateinit var comunas: ContextoComuna
    @Autowired lateinit var jdbc: JdbcClient

    private val providencia = CodigoComuna.de("13123")
    private val santiago = CodigoComuna.de("13101")

    @BeforeEach
    fun datos() {
        limpiarDatos()
        for ((id, comuna) in listOf("01M0000000000000000000000A" to "13123", "01M0000000000000000000000B" to "13101")) {
            administrador.update(
                "INSERT INTO emision (id, comuna, punto, expira_en, emitida_por, kid, consentimiento_version, consentimiento_en, productos) " +
                    "VALUES (?, ?, 'CL-FP-' || ? || '-01', now(), 'u', 'k', 'v', now(), '[]'::jsonb)",
                id, comuna, comuna,
            )
        }
    }

    private fun contar(): Int = jdbc.sql("SELECT count(*) FROM emision").query(Int::class.java).single()

    @Test
    fun `cada comuna ve solo sus emisiones`() {
        assertEquals(1, comunas.en(providencia) { contar() })
        assertEquals(1, comunas.en(santiago) { contar() })
        assertEquals(0, contar())
    }

    @Test
    fun `una comuna no puede revocar emisiones de otra`() {
        comunas.en(providencia) { jdbc.sql("UPDATE emision SET revocada_en = now()").update() }
        assertEquals(1, administrador.queryForObject("SELECT count(*) FROM emision WHERE revocada_en IS NULL", Int::class.java))
    }

    @Test
    fun `una comuna no puede registrar emisiones a nombre de otra`() {
        assertFailsWith<DataAccessException> {
            comunas.en(providencia) {
                jdbc.sql(
                    "INSERT INTO emision (id, comuna, punto, expira_en, emitida_por, kid, consentimiento_version, consentimiento_en, productos) " +
                        "VALUES ('01M0000000000000000000000C', '13101', 'x', now(), 'u', 'k', 'v', now(), '[]'::jsonb)",
                ).update()
            }
        }
    }

    @Test
    fun `una emisión no se puede borrar`() {
        assertFailsWith<DataAccessException> {
            comunas.en(providencia) { jdbc.sql("DELETE FROM emision").update() }
        }
    }
}
