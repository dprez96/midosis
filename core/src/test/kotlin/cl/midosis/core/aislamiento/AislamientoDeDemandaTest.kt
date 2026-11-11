package cl.midosis.core.aislamiento

import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.core.infraestructura.persistencia.ContextoComuna
import cl.midosis.core.infraestructura.persistencia.EmisionesJdbc
import cl.midosis.core.soporte.PruebaIntegracion
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/** Aislamiento de la lectura de retiros para la proyección de la demanda (ADR-011, HU-27). */
class AislamientoDeDemandaTest : PruebaIntegracion() {

    @Autowired lateinit var comunas: ContextoComuna
    @Autowired lateinit var emisiones: EmisionesJdbc

    private val providencia = CodigoComuna.de("13123")
    private val santiago = CodigoComuna.de("13101")
    private val desde = Instant.parse("2020-01-01T00:00:00Z")

    @BeforeEach
    fun datos() {
        limpiarDatos()
        for ((id, comuna) in listOf("01M0000000000000000000000A" to "13123", "01M0000000000000000000000B" to "13101")) {
            administrador.update(
                "INSERT INTO emision (id, comuna, punto, expira_en, emitida_por, kid, consentimiento_version, consentimiento_en, productos) " +
                    "VALUES (?, ?, 'x', now(), 'u', 'k', 'v', now(), '[{\"gtin\":\"7802250012344\",\"du\":180,\"e\":30,\"co\":30}]'::jsonb)",
                id, comuna,
            )
        }
    }

    @Test
    fun `cada comuna lee solo sus retiros`() {
        assertEquals(1, comunas.en(providencia) { emisiones.retiros(desde, ZoneOffset.UTC) }.size)
        assertEquals(1, comunas.en(santiago) { emisiones.retiros(desde, ZoneOffset.UTC) }.size)
    }

    @Test
    fun `sin comuna no se lee ningun retiro`() {
        assertEquals(0, emisiones.retiros(desde, ZoneOffset.UTC).size)
    }
}
