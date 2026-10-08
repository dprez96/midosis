package cl.midosis.credencial

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/**
 * Clave pública con que una comuna firma sus códigos (sección 7.6.2 del informe).
 * Tiene identificador, período de validez y estado; la aplicación conserva las
 * antiguas para seguir leyendo códigos emitidos antes de una rotación (ADR-013).
 */
class ClaveDeEmisor(
    val kid: String,
    publica: ByteArray,
    /** La comuna cuyos códigos puede firmar. */
    val comuna: String,
    /** Desde cuándo firma, en segundos desde 1970. */
    val vigenteDesde: Long,
    /** Hasta cuándo firma, sin incluir ese instante. Null si sigue vigente. */
    val vigenteHasta: Long? = null,
    val revocada: Boolean = false,
) {
    val publica: ByteArray = publica.copyOf()

    init {
        require(kid.isNotEmpty() && kid.toByteArray(Charsets.UTF_8).size <= MAXIMO_KID) { "kid inválido «$kid»" }
        require(publica.size == 32) { "una clave pública Ed25519 tiene 32 bytes, llegaron ${publica.size}" }
        require(vigenteHasta == null || vigenteHasta > vigenteDesde) { "la vigencia de $kid termina antes de empezar" }
    }

    /** Si la clave podía firmar un código emitido en ese instante. */
    fun vigenteEn(instante: Long): Boolean = instante >= vigenteDesde && (vigenteHasta == null || instante < vigenteHasta)
}

/**
 * Clave simétrica que cifra el sobre (ADR-003). Viaja dentro de la aplicación, así que
 * protege del lector genérico y no de quien la extraiga: ese es el alcance declarado.
 */
class ClaveDeContenido(val kid: String, clave: ByteArray) {
    internal val clave: ByteArray = clave.copyOf()

    init {
        require(kid.isNotEmpty() && kid.toByteArray(Charsets.UTF_8).size <= MAXIMO_KID) { "kid inválido «$kid»" }
        require(clave.size == 32) { "una clave AES-256 tiene 32 bytes, llegaron ${clave.size}" }
    }
}

/**
 * Lo que la aplicación trae empaquetado para verificar sin conexión.
 *
 * Las claves de prueba de contrato/vectores empiezan con «prueba-». Un conjunto de
 * producción que las contenga es un error de empaquetado y se rechaza al crearlo,
 * antes de leer ningún código.
 */
class ClavesDeConfianza(
    emisores: List<ClaveDeEmisor>,
    contenido: List<ClaveDeContenido>,
    admiteClavesDePrueba: Boolean = false,
) {
    private val emisores = emisores.associateBy { it.kid }
    private val contenido = contenido.associateBy { it.kid }

    init {
        require(this.emisores.size == emisores.size) { "hay dos claves de emisor con el mismo kid" }
        require(this.contenido.size == contenido.size) { "hay dos claves de contenido con el mismo kid" }
        if (!admiteClavesDePrueba) {
            val dePrueba = (this.emisores.keys + this.contenido.keys).filter { it.startsWith(PREFIJO_DE_PRUEBA) }
            require(dePrueba.isEmpty()) { "el conjunto de producción trae claves de prueba: $dePrueba" }
        }
    }

    fun emisor(kid: String): ClaveDeEmisor? = emisores[kid]

    fun contenido(kid: String): ClaveDeContenido? = contenido[kid]

    companion object {
        const val PREFIJO_DE_PRUEBA = "prueba-"
    }
}

/**
 * Quien firma los códigos. Core lo implementa con Cloud KMS, que nunca entrega la
 * clave privada; FirmanteEd25519 es para pruebas y desarrollo.
 */
interface Firmante {
    val kid: String

    /** Firma Ed25519 (RFC 8032) de los datos: 64 bytes. */
    fun firmar(datos: ByteArray): ByteArray
}

/** Firma con una clave privada en memoria. No usar en producción. */
class FirmanteEd25519(override val kid: String, privada: ByteArray) : Firmante {
    private val clave = Ed25519PrivateKeyParameters(privada.copyOf(), 0)

    /** La clave pública que corresponde, para armar el conjunto de confianza de prueba. */
    val publica: ByteArray get() = clave.generatePublicKey().encoded

    override fun firmar(datos: ByteArray): ByteArray =
        Ed25519Signer().run {
            init(true, clave)
            update(datos, 0, datos.size)
            generateSignature()
        }
}

internal const val MAXIMO_KID = 32
