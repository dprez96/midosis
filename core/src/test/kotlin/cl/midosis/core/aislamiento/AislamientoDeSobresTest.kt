package cl.midosis.core.aislamiento

import cl.midosis.core.aplicacion.canje.CanjeDeCodigos
import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.core.infraestructura.persistencia.ContextoCanje
import cl.midosis.core.infraestructura.persistencia.ContextoComuna
import cl.midosis.core.soporte.PruebaIntegracion
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.simple.JdbcClient

/**
 * Aislamiento de los sobres (ADR-011, HU-10). La comuna emisora ve solo los suyos, y el
 * contexto de canje expone exactamente el sobre del código que se canjea: ni los demás de su
 * comuna ni los de otras.
 */
class AislamientoDeSobresTest : PruebaIntegracion() {

    @Autowired lateinit var canje: CanjeDeCodigos
    @Autowired lateinit var comunas: ContextoComuna
    @Autowired lateinit var canjes: ContextoCanje
    @Autowired lateinit var jdbc: JdbcClient

    private val providencia = CodigoComuna.de("13123")
    private val santiago = CodigoComuna.de("13101")
    private lateinit var deProvidencia: CanjeDeCodigos.Registrado
    private lateinit var otroDeProvidencia: CanjeDeCodigos.Registrado
    private lateinit var deSantiago: CanjeDeCodigos.Registrado

    @BeforeEach
    fun datos() {
        limpiarDatos()
        val vence = Instant.now().plus(Duration.ofHours(72))
        deProvidencia = canje.registrar(providencia, "MD1:A", vence)
        otroDeProvidencia = canje.registrar(providencia, "MD1:B", vence)
        deSantiago = canje.registrar(santiago, "MD1:C", vence)
    }

    private fun contar(): Int = jdbc.sql("SELECT count(*) FROM sobre").query(Int::class.java).single()

    @Test
    fun `cada comuna ve solo sus sobres`() {
        assertEquals(2, comunas.en(providencia) { contar() })
        assertEquals(1, comunas.en(santiago) { contar() })
    }

    @Test
    fun `el canje expone solo el sobre del código que se canjea`() {
        assertEquals(1, canjes.en(deProvidencia.codigoCorto) { contar() })
        val codigos = canjes.en(deProvidencia.codigoCorto) {
            jdbc.sql("SELECT codigo FROM sobre").query(String::class.java).list()
        }
        assertEquals(listOf("MD1:A"), codigos)
    }

    @Test
    fun `fuera de todo contexto no se ve ningún sobre`() {
        assertEquals(0, contar())
    }

    @Test
    fun `el canje no puede borrar ni cambiar otros sobres`() {
        canjes.en(deProvidencia.codigoCorto) {
            jdbc.sql("DELETE FROM sobre").update()
            jdbc.sql("UPDATE sobre SET fallidos = 99").update()
        }
        // Solo cayó el de su propio código.
        assertEquals(2, administrador.queryForObject("SELECT count(*) FROM sobre", Int::class.java))
        assertEquals(0, administrador.queryForObject("SELECT max(fallidos) FROM sobre", Int::class.java))
    }

    @Test
    fun `una comuna no puede registrar sobres a nombre de otra`() {
        assertFailsWith<DataAccessException> {
            comunas.en(providencia) {
                jdbc.sql(
                    "INSERT INTO sobre (codigo_corto, comuna, codigo, pin_derivado, pin_sal, expira_en) " +
                        "VALUES ('0000000000', '13101', 'MD1:X', decode('00', 'hex'), decode('00', 'hex'), now())",
                ).update()
            }
        }
        assertEquals(0, administrador.queryForObject("SELECT count(*) FROM sobre WHERE codigo_corto = '0000000000'", Int::class.java))
    }

    @Test
    fun `el canje de un código de otra comuna funciona igual porque el paciente no sabe de comunas`() {
        val r = canje.canjear(deSantiago.codigoCorto, deSantiago.pin)
        assertEquals(cl.midosis.core.dominio.canje.ResultadoDeCanje.Canjeado("MD1:C"), r)
    }
}
