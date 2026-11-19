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

/** La auditoría que lee la exportación (HU-29, V10): cada comuna solo la suya, y sin editarla. */
class AislamientoDeAuditoriaTest : PruebaIntegracion() {

    @Autowired lateinit var comunas: ContextoComuna
    @Autowired lateinit var jdbc: JdbcClient

    @BeforeEach
    fun datos() {
        limpiarDatos()
        administrador.update("INSERT INTO evento_seguridad (comuna, tipo, detalle) VALUES ('13123', 'EXPORTACION', 'a'), ('13101', 'EXPORTACION', 'b')")
    }

    private fun contar(): Int = jdbc.sql("SELECT count(*) FROM evento_seguridad").query(Int::class.java).single()

    @Test
    fun `cada comuna lee solo su auditoría`() {
        assertEquals(1, comunas.en(CodigoComuna.de("13123")) { contar() })
        assertEquals(1, comunas.en(CodigoComuna.de("13101")) { contar() })
        assertEquals(0, contar())
    }

    @Test
    fun `la auditoría no se edita ni se borra desde la aplicación`() {
        assertFailsWith<DataAccessException> { comunas.en(CodigoComuna.de("13123")) { jdbc.sql("UPDATE evento_seguridad SET detalle = 'x'").update() } }
        assertFailsWith<DataAccessException> { comunas.en(CodigoComuna.de("13123")) { jdbc.sql("DELETE FROM evento_seguridad").update() } }
    }
}
