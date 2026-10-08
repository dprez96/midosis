package cl.midosis.credencial

import com.upokecenter.cbor.CBOREncodeOptions
import com.upokecenter.cbor.CBORObject
import com.upokecenter.cbor.CBORType
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/*
 * La pila del código de tratamiento (ADR-004, contrato/esquema/pila-v1.md):
 *
 *   carga (CBOR) -> COSE_Sign1 Ed25519 -> zlib -> COSE_Encrypt0 A256GCM -> Base45 -> «MD1:»
 *
 * Se firma antes de cifrar para que un lector genérico no vea ni el emisor, y se
 * comprime antes de cifrar porque lo cifrado ya no tiene redundancia.
 */

internal object Cose {
    const val PREFIJO = "MD1:"
    const val ETIQUETA_SIGN1 = 18
    const val ETIQUETA_ENCRYPT0 = 16
    const val ALG_EDDSA = -8
    const val ALG_A256GCM = 3
    const val H_ALG = 1
    const val H_KID = 4
    const val H_IV = 5
    const val LARGO_IV = 12
    const val LARGO_FIRMA = 64
    const val LARGO_TAG_BITS = 128

    /** El tope de lo que puede ocupar la carga firmada al descomprimirla. */
    const val LIMITE_DESCOMPRIMIDO = 8 * 1024

    val DETERMINISTA = CBOREncodeOptions("ctap2canonical=true")

    /**
     * El mensaje con su etiqueta COSE delante. La etiqueta se escribe a mano porque la
     * codificación determinista de la biblioteca (ctap2canonical) descarta las etiquetas.
     */
    fun etiquetado(etiqueta: Int, mensaje: CBORObject): ByteArray {
        require(etiqueta in 0..23) { "la etiqueta $etiqueta no cabe en el byte inicial" }
        return byteArrayOf((0xC0 or etiqueta).toByte()) + mensaje.EncodeToBytes(DETERMINISTA)
    }

    fun cabeceraProtegida(vararg pares: Pair<Int, Any>): ByteArray =
        CBORObject.NewMap().apply { pares.forEach { (k, v) -> Add(k, v) } }.EncodeToBytes(DETERMINISTA)

    /** Sig_structure de RFC 9052, sección 4.4, sin datos externos. */
    fun estructuraDeFirma(protegida: ByteArray, carga: ByteArray): ByteArray =
        CBORObject.NewArray().Add("Signature1").Add(protegida).Add(ByteArray(0)).Add(carga).EncodeToBytes()

    /** Enc_structure de RFC 9052, sección 5.3, sin datos externos. */
    fun estructuraDeCifrado(protegida: ByteArray): ByteArray =
        CBORObject.NewArray().Add("Encrypt0").Add(protegida).Add(ByteArray(0)).EncodeToBytes()

    fun aesGcm(modo: Int, clave: ClaveDeContenido, iv: ByteArray, protegida: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(modo, SecretKeySpec(clave.clave, "AES"), GCMParameterSpec(LARGO_TAG_BITS, iv))
            updateAAD(estructuraDeCifrado(protegida))
        }
}

/**
 * Emite el texto del código a partir de la carga. Core lo usa con un firmante de
 * Cloud KMS y una clave de contenido de Secret Manager.
 */
class EmisorDeCodigos(
    private val firmante: Firmante,
    private val contenido: ClaveDeContenido,
    private val aleatorio: SecureRandom = SecureRandom(),
) {
    fun emitir(carga: Carga): String {
        val firmado = firmar(CodecDeCarga.codificar(carga))
        return Cose.PREFIJO + Base45.codificar(cifrar(comprimir(firmado)))
    }

    internal fun firmar(carga: ByteArray): ByteArray {
        val protegida = Cose.cabeceraProtegida(
            Cose.H_ALG to Cose.ALG_EDDSA,
            Cose.H_KID to firmante.kid.toByteArray(Charsets.UTF_8),
        )
        val firma = firmante.firmar(Cose.estructuraDeFirma(protegida, carga))
        check(firma.size == Cose.LARGO_FIRMA) { "el firmante entregó ${firma.size} bytes; Ed25519 son 64" }
        return Cose.etiquetado(
            Cose.ETIQUETA_SIGN1,
            CBORObject.NewArray().Add(protegida).Add(CBORObject.NewMap()).Add(carga).Add(firma),
        )
    }

    internal fun comprimir(datos: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        try {
            deflater.setInput(datos)
            deflater.finish()
            val salida = ByteArrayOutputStream()
            val bloque = ByteArray(512)
            while (!deflater.finished()) salida.write(bloque, 0, deflater.deflate(bloque))
            return salida.toByteArray()
        } finally {
            deflater.end()
        }
    }

    internal fun cifrar(datos: ByteArray): ByteArray {
        val iv = ByteArray(Cose.LARGO_IV).also { aleatorio.nextBytes(it) }
        val protegida = Cose.cabeceraProtegida(Cose.H_ALG to Cose.ALG_A256GCM)
        val cifrado = Cose.aesGcm(Cipher.ENCRYPT_MODE, contenido, iv, protegida).doFinal(datos)
        val libre = CBORObject.NewMap()
            .Add(Cose.H_KID, contenido.kid.toByteArray(Charsets.UTF_8))
            .Add(Cose.H_IV, iv)
        return Cose.etiquetado(Cose.ETIQUETA_ENCRYPT0, CBORObject.NewArray().Add(protegida).Add(libre).Add(cifrado))
    }
}

/** El resultado de leer un código. */
sealed interface Lectura {
    /** El código es auténtico y vigente. kid identifica la clave que lo firmó. */
    data class Aceptado(val carga: Carga, val kid: String) : Lectura

    data class Rechazado(val motivo: Motivo, val regla: String) : Lectura
}

/**
 * Lee un código sin conexión, en el orden de la sección 7.6.1 del informe: prefijo
 * y versión, descifrado, descompresión con límite, emisor, firma y vigencia. La
 * reutilización (registro local de jti) la resuelve quien guarda el tratamiento.
 *
 * @param ahora segundos desde 1970 según el reloj del teléfono. El paciente puede
 * atrasarlo; es un riesgo residual declarado en la sección 7.6.3.
 */
class LectorDeCodigos(private val claves: ClavesDeConfianza, private val ahora: () -> Long) {

    fun leer(codigo: String): Lectura =
        try {
            val (carga, kid) = verificar(codigo)
            Lectura.Aceptado(carga, kid)
        } catch (e: CodigoRechazado) {
            Lectura.Rechazado(e.motivo, e.regla)
        }

    private fun verificar(codigo: String): Pair<Carga, String> {
        val prefijo = PREFIJO.find(codigo) ?: rechazar(Motivo.NO_ES_DE_MIDOSIS, "prefijo")
        if (prefijo.value != Cose.PREFIJO) rechazar(Motivo.VERSION_NO_SOPORTADA, "prefijo")
        val sobre = try {
            Base45.decodificar(codigo.substring(Cose.PREFIJO.length))
        } catch (e: IllegalArgumentException) {
            rechazar(Motivo.ALTERADO, "base45")
        }

        val firmado = descomprimir(descifrar(sobre))
        val (cargaBytes, clave) = verificarFirma(firmado)
        val carga = CodecDeCarga.leer(cargaBytes)

        if (carga.comuna != clave.comuna) rechazar(Motivo.EMISOR_DESCONOCIDO, "comuna-de-la-clave")
        if (!clave.vigenteEn(carga.emitidoEn)) rechazar(Motivo.EMISOR_DESCONOCIDO, "clave-fuera-de-vigencia")
        if (ahora() >= carga.expiraEn) rechazar(Motivo.EXPIRADO, "vigencia")
        return carga to clave.kid
    }

    private fun descifrar(sobre: ByteArray): ByteArray {
        val m = MensajeCose.leer(sobre, Cose.ETIQUETA_ENCRYPT0, 3)
        if (m.protegida.size() != 1 || entero(m.protegida, Cose.H_ALG) != Cose.ALG_A256GCM) {
            rechazar(Motivo.ALTERADO, "algoritmo")
        }
        val libre = m.partes[1]
        val kid = bytes(libre, Cose.H_KID)
        val iv = bytes(libre, Cose.H_IV)
        val cifrado = m.partes[2]
        if (libre.size() != 2 || kid == null || iv == null || iv.size != Cose.LARGO_IV ||
            cifrado.isTagged || cifrado.type != CBORType.ByteString
        ) {
            rechazar(Motivo.ALTERADO, "cose")
        }
        // Una clave de contenido que la aplicación no trae sale de una versión más
        // nueva del sistema: se pide actualizar.
        val clave = texto(kid)?.let { claves.contenido(it) } ?: rechazar(Motivo.VERSION_NO_SOPORTADA, "clave-de-contenido")
        return try {
            Cose.aesGcm(Cipher.DECRYPT_MODE, clave, iv, m.protegidaBytes).doFinal(cifrado.GetByteString())
        } catch (e: AEADBadTagException) {
            rechazar(Motivo.ALTERADO, "cifrado")
        }
    }

    /** Con tope: unos cien bytes de ceros comprimidos se expanden a megas. */
    private fun descomprimir(datos: ByteArray): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(datos)
            val salida = ByteArray(Cose.LIMITE_DESCOMPRIMIDO + 1)
            var largo = 0
            while (!inflater.finished() && largo < salida.size) {
                val n = inflater.inflate(salida, largo, salida.size - largo)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                largo += n
            }
            if (!inflater.finished() || largo > Cose.LIMITE_DESCOMPRIMIDO || inflater.remaining != 0) {
                rechazar(Motivo.ALTERADO, "descompresion")
            }
            return salida.copyOf(largo)
        } catch (e: DataFormatException) {
            rechazar(Motivo.ALTERADO, "descompresion")
        } finally {
            inflater.end()
        }
    }

    private fun verificarFirma(firmado: ByteArray): Pair<ByteArray, ClaveDeEmisor> {
        val m = MensajeCose.leer(firmado, Cose.ETIQUETA_SIGN1, 4)
        if (entero(m.protegida, Cose.H_ALG) != Cose.ALG_EDDSA) rechazar(Motivo.ALTERADO, "algoritmo")
        val kid = bytes(m.protegida, Cose.H_KID)
        val carga = m.partes[2]
        val firma = m.partes[3]
        if (m.protegida.size() != 2 || kid == null ||
            carga.isTagged || carga.type != CBORType.ByteString ||
            firma.isTagged || firma.type != CBORType.ByteString || firma.GetByteString().size != Cose.LARGO_FIRMA
        ) {
            rechazar(Motivo.ALTERADO, "cose")
        }
        val clave = texto(kid)?.let { claves.emisor(it) } ?: rechazar(Motivo.EMISOR_DESCONOCIDO, "clave-desconocida")
        if (clave.revocada) rechazar(Motivo.EMISOR_DESCONOCIDO, "clave-revocada")

        val cargaBytes = carga.GetByteString()
        val estructura = Cose.estructuraDeFirma(m.protegidaBytes, cargaBytes)
        val valida = Ed25519Signer().run {
            init(false, Ed25519PublicKeyParameters(clave.publica, 0))
            update(estructura, 0, estructura.size)
            verifySignature(firma.GetByteString())
        }
        if (!valida) rechazar(Motivo.ALTERADO, "firma")
        return cargaBytes to clave
    }

    /** Un mensaje COSE etiquetado: el arreglo, la cabecera protegida ya leída y sus bytes. */
    private class MensajeCose(val partes: List<CBORObject>, val protegidaBytes: ByteArray, val protegida: CBORObject) {
        companion object {
            fun leer(datos: ByteArray, etiqueta: Int, largo: Int): MensajeCose {
                val objeto = try {
                    CBORObject.DecodeFromBytes(datos)
                } catch (e: Exception) {
                    rechazar(Motivo.ALTERADO, "cose")
                }
                val tags = objeto.mostOuterTag
                if (!objeto.isTagged || objeto.UntagOne().isTagged || tags.CanFitInInt32().not() ||
                    tags.ToInt32Checked() != etiqueta
                ) {
                    rechazar(Motivo.ALTERADO, "cose")
                }
                val arreglo = objeto.UntagOne()
                if (arreglo.type != CBORType.Array || arreglo.size() != largo) rechazar(Motivo.ALTERADO, "cose")
                val partes = arreglo.values.toList()
                val protegidaBytes = partes[0]
                if (protegidaBytes.isTagged || protegidaBytes.type != CBORType.ByteString ||
                    partes[1].isTagged || partes[1].type != CBORType.Map
                ) {
                    rechazar(Motivo.ALTERADO, "cose")
                }
                val crudos = protegidaBytes.GetByteString()
                val protegida = if (crudos.isEmpty()) {
                    CBORObject.NewMap()
                } else {
                    try {
                        CBORObject.DecodeFromBytes(crudos)
                    } catch (e: Exception) {
                        rechazar(Motivo.ALTERADO, "cose")
                    }
                }
                if (protegida.isTagged || protegida.type != CBORType.Map) rechazar(Motivo.ALTERADO, "cose")
                return MensajeCose(partes, crudos, protegida)
            }
        }
    }

    private fun entero(mapa: CBORObject, etiqueta: Int): Int? {
        val v = mapa.GetOrDefault(CBORObject.FromObject(etiqueta), null) ?: return null
        return if (!v.isTagged && v.type == CBORType.Integer && v.CanValueFitInInt32()) v.AsInt32Value() else null
    }

    private fun bytes(mapa: CBORObject, etiqueta: Int): ByteArray? {
        val v = mapa.GetOrDefault(CBORObject.FromObject(etiqueta), null) ?: return null
        return if (!v.isTagged && v.type == CBORType.ByteString) v.GetByteString() else null
    }

    /** El kid como texto, o null si no es UTF-8 válido: entonces no coincide con ninguna clave. */
    private fun texto(kid: ByteArray): String? =
        try {
            Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(kid)).toString()
        } catch (e: java.nio.charset.CharacterCodingException) {
            null
        }

    private companion object {
        val PREFIJO = Regex("^MD[0-9]+:")
    }
}
