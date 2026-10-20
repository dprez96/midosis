package cl.midosis.core.aplicacion.canje

import cl.midosis.core.dominio.canje.Pin
import cl.midosis.core.dominio.canje.RepositorioDeSobres
import cl.midosis.core.dominio.canje.ResultadoDeCanje
import cl.midosis.core.dominio.canje.Sobre
import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.core.infraestructura.persistencia.ContextoCanje
import cl.midosis.core.infraestructura.persistencia.ContextoComuna
import cl.midosis.credencial.CodigoCorto
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service

/**
 * Canje con código corto y PIN (HU-10, tabla 88 del informe).
 *
 * La emisión (HU-01) registra cada código con [registrar] e imprime su código corto y su PIN.
 * El paciente los escribe en la aplicación y recibe el código completo, que la aplicación
 * verifica igual que si lo hubiera escaneado: este servicio no lo descifra ni lo revisa.
 */
@Service
class CanjeDeCodigos(
    private val sobres: RepositorioDeSobres,
    private val comunas: ContextoComuna,
    private val canjes: ContextoCanje,
) {
    private val aleatorio = SecureRandom()

    data class Registrado(val codigoCorto: CodigoCorto, val pin: Pin)

    fun registrar(comuna: CodigoComuna, codigo: String, expiraEn: Instant, emision: String? = null): Registrado {
        val pin = Pin.generar(aleatorio)
        val sal = ByteArray(16).also(aleatorio::nextBytes)
        val derivado = pin.derivar(sal)
        // 45 bits al azar: un choque es casi imposible, pero si ocurre se intenta con otro.
        repeat(INTENTOS_DE_GENERACION) {
            val corto = CodigoCorto.generar(aleatorio)
            try {
                comunas.en(comuna) {
                    sobres.guardar(Sobre(corto, comuna, codigo, derivado, sal, expiraEn, 0, null, 0, emision))
                }
                return Registrado(corto, pin)
            } catch (e: DuplicateKeyException) {
                // Otro sobre ya tiene ese código corto.
            }
        }
        error("no se pudo generar un código corto libre")
    }

    fun canjear(codigo: CodigoCorto, pin: Pin, ahora: Instant = Instant.now()): ResultadoDeCanje = canjes.en(codigo) {
        val sobre = sobres.buscarParaCanje(codigo)
        when {
            sobre == null || !ahora.isBefore(sobre.expiraEn) -> ResultadoDeCanje.NoEncontrado
            sobre.fallidos >= MAXIMO_FALLIDOS -> ResultadoDeCanje.Bloqueado
            else -> intentar(sobre, pin, ahora)
        }
    }

    private fun intentar(sobre: Sobre, pin: Pin, ahora: Instant): ResultadoDeCanje {
        val inicio = sobre.ventanaDesde
        val ventanaVigente = inicio != null && ahora.isBefore(inicio.plus(VENTANA))
        val desde = if (ventanaVigente) inicio!! else ahora
        val intentos = if (ventanaVigente) sobre.intentosVentana else 0
        if (intentos >= MAXIMO_POR_VENTANA) return ResultadoDeCanje.DemasiadosIntentos(desde.plus(VENTANA))

        if (pin.corresponde(sobre.pinDerivado, sobre.pinSal)) {
            // El sobre es efímero: se destruye al canjearse (sección 7.7).
            sobres.destruir(sobre.codigoCorto)
            return ResultadoDeCanje.Canjeado(sobre.codigo)
        }
        val fallidos = sobre.fallidos + 1
        sobres.registrarIntento(sobre.codigoCorto, intentos + 1, desde, fallidos)
        return if (fallidos >= MAXIMO_FALLIDOS) {
            ResultadoDeCanje.Bloqueado
        } else {
            ResultadoDeCanje.PinIncorrecto(MAXIMO_FALLIDOS - fallidos)
        }
    }

    companion object {
        const val MAXIMO_POR_VENTANA = 5
        const val MAXIMO_FALLIDOS = 10
        val VENTANA: Duration = Duration.ofMinutes(10)
        private const val INTENTOS_DE_GENERACION = 5
    }
}
