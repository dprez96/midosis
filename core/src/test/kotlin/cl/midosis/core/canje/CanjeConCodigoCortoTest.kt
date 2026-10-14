package cl.midosis.core.canje

import cl.midosis.core.aplicacion.canje.CanjeDeCodigos
import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.core.soporte.PruebaIntegracion
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/**
 * Canje con código corto y PIN (HU-10): el paciente, sin cuenta, descarga el código completo
 * cuando el QR no se puede leer. Límites de la tabla 88 del informe.
 */
@AutoConfigureMockMvc
class CanjeConCodigoCortoTest : PruebaIntegracion() {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var canje: CanjeDeCodigos

    private val providencia = CodigoComuna.de("13123")
    private val codigoCompleto = "MD1:CODIGO-DE-PRUEBA-SINTETICO"
    private lateinit var registrado: CanjeDeCodigos.Registrado

    @BeforeEach
    fun datos() {
        limpiarDatos()
        registrado = canje.registrar(providencia, codigoCompleto, Instant.now().plus(Duration.ofHours(72)))
    }

    private fun canjear(codigo: String, pin: String) =
        mvc.post("/api/canje") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"codigo":"$codigo","pin":"$pin"}"""
        }

    private fun otroPin(): String = "%04d".format((registrado.pin.valor.toInt() + 1) % 10_000)

    @Test
    fun `con el código y el PIN correctos entrega el código completo, sin token`() {
        canjear(registrado.codigoCorto.agrupado, registrado.pin.valor).andExpect {
            status { isOk() }
            jsonPath("$.codigo") { value(codigoCompleto) }
        }
    }

    @Test
    fun `el sobre se destruye al canjearse`() {
        canjear(registrado.codigoCorto.valor, registrado.pin.valor).andExpect { status { isOk() } }
        canjear(registrado.codigoCorto.valor, registrado.pin.valor).andExpect { status { isNotFound() } }
        assert(administrador.queryForObject("SELECT count(*) FROM sobre", Int::class.java) == 0)
    }

    @Test
    fun `acepta el código como lo escribe una persona`() {
        canjear(" ${registrado.codigoCorto.agrupado.lowercase()} ", registrado.pin.valor).andExpect { status { isOk() } }
    }

    @Test
    fun `un PIN equivocado dice cuántos intentos quedan`() {
        canjear(registrado.codigoCorto.valor, otroPin()).andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.intentosRestantes") { value(9) }
        }
    }

    @Test
    fun `más de cinco intentos en diez minutos se frenan, aunque el sexto traiga el PIN correcto`() {
        repeat(5) { canjear(registrado.codigoCorto.valor, otroPin()).andExpect { status { isUnprocessableContent() } } }
        canjear(registrado.codigoCorto.valor, registrado.pin.valor).andExpect {
            status { isTooManyRequests() }
            header { exists("Retry-After") }
        }
    }

    @Test
    fun `pasada la ventana se puede volver a intentar, y a los diez fallidos se bloquea`() {
        val ahora = Instant.now()
        val corto = registrado.codigoCorto
        val malo = cl.midosis.core.dominio.canje.Pin.de(otroPin())!!
        repeat(5) { canje.canjear(corto, malo, ahora) }
        // Once minutos después la ventana se renueva.
        val despues = ahora.plus(Duration.ofMinutes(11))
        repeat(4) { canje.canjear(corto, malo, despues) }
        val decimo = canje.canjear(corto, malo, despues)
        assert(decimo == cl.midosis.core.dominio.canje.ResultadoDeCanje.Bloqueado) { "se esperaba el bloqueo, llegó $decimo" }
        // Ni el PIN correcto lo desbloquea.
        assert(canje.canjear(corto, registrado.pin, despues.plus(Duration.ofMinutes(30))) == cl.midosis.core.dominio.canje.ResultadoDeCanje.Bloqueado)
        canjear(corto.valor, registrado.pin.valor).andExpect { status { isLocked() } }
    }

    @Test
    fun `un código vencido responde igual que uno que no existe`() {
        val vencido = canje.registrar(providencia, codigoCompleto, Instant.now().minusSeconds(1))
        canjear(vencido.codigoCorto.valor, vencido.pin.valor).andExpect { status { isNotFound() } }
        canjear("0000000000", "1234").andExpect { status { isNotFound() } }
    }

    @Test
    fun `un código mal escrito o un PIN que no son cuatro dígitos se rechazan antes de contar un intento`() {
        val malEscrito = registrado.codigoCorto.valor.dropLast(1) + if (registrado.codigoCorto.valor.last() == 'A') "B" else "A"
        canjear(malEscrito, registrado.pin.valor).andExpect { status { isBadRequest() } }
        canjear(registrado.codigoCorto.valor, "12a4").andExpect { status { isBadRequest() } }
        val fallidos = administrador.queryForObject("SELECT fallidos FROM sobre", Int::class.java)
        assert(fallidos == 0)
    }

    @Test
    fun `el PIN no queda en claro en la base`() {
        val guardado = administrador.queryForObject("SELECT encode(pin_derivado, 'escape') FROM sobre", String::class.java)
        assert(guardado?.contains(registrado.pin.valor) != true)
        val largo = administrador.queryForObject("SELECT length(pin_derivado) FROM sobre", Int::class.java)
        assert(largo == 32)
    }
}
