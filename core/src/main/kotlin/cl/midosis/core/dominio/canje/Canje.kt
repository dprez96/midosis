package cl.midosis.core.dominio.canje

import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.credencial.CodigoCorto
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * El PIN de cuatro dígitos que acompaña al código corto. Se imprime en el comprobante y en la
 * base solo queda derivado con PBKDF2 y una sal propia.
 *
 * Con diez mil PINs posibles, la derivación no basta por sí sola: lo que de verdad protege es
 * la limitación de intentos del canje.
 */
@JvmInline
value class Pin private constructor(val valor: String) {
    override fun toString(): String = "****"

    fun derivar(sal: ByteArray): ByteArray {
        val especificacion = PBEKeySpec(valor.toCharArray(), sal, ITERACIONES, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(especificacion).encoded
    }

    /** Comparación en tiempo constante, para no filtrar cuánto del PIN coincide. */
    fun corresponde(derivado: ByteArray, sal: ByteArray): Boolean = MessageDigest.isEqual(derivar(sal), derivado)

    companion object {
        private const val ITERACIONES = 100_000

        fun generar(aleatorio: SecureRandom): Pin = Pin("%04d".format(aleatorio.nextInt(10_000)))

        fun de(texto: String): Pin? = texto.trim().takeIf { it.length == 4 && it.all(Char::isDigit) }?.let(::Pin)
    }
}

/** Un sobre guardado: el código completo, cifrado, a la espera de que lo canjeen. */
data class Sobre(
    val codigoCorto: CodigoCorto,
    val comuna: CodigoComuna,
    val codigo: String,
    val pinDerivado: ByteArray,
    val pinSal: ByteArray,
    val expiraEn: Instant,
    val intentosVentana: Int,
    val ventanaDesde: Instant?,
    val fallidos: Int,
)

sealed interface ResultadoDeCanje {
    data class Canjeado(val codigo: String) : ResultadoDeCanje

    /** No existe, ya se canjeó o venció. Las tres respuestas son iguales a propósito. */
    data object NoEncontrado : ResultadoDeCanje

    data class PinIncorrecto(val intentosRestantes: Int) : ResultadoDeCanje

    /** Más de 5 intentos en 10 minutos. */
    data class DemasiadosIntentos(val reintentarEn: Instant) : ResultadoDeCanje

    /** 10 PIN fallidos: el código ya no se puede canjear. */
    data object Bloqueado : ResultadoDeCanje
}

/** Acceso a los sobres. */
interface RepositorioDeSobres {
    /** Solo dentro del contexto de la comuna emisora. Falla si el código corto ya existe. */
    fun guardar(sobre: Sobre)

    /** Solo dentro del contexto de canje de ese código: bloquea la fila hasta terminar. */
    fun buscarParaCanje(codigo: CodigoCorto): Sobre?

    fun registrarIntento(codigo: CodigoCorto, intentosVentana: Int, ventanaDesde: Instant, fallidos: Int)

    fun destruir(codigo: CodigoCorto)
}
