package cl.midosis.core.infraestructura.claves

import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.credencial.ClaveDeContenido
import cl.midosis.credencial.Firmante
import com.google.cloud.kms.v1.AsymmetricSignRequest
import com.google.cloud.kms.v1.CryptoKeyVersion
import com.google.cloud.kms.v1.KeyManagementServiceClient
import com.google.protobuf.ByteString
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.CRC32C
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Lo mínimo que core necesita de Cloud KMS, separado para probar sin la nube: la versión
 * vigente de una clave y firmar con ella. La clave privada nunca sale de KMS.
 */
interface ServicioDeFirma {
    /** El nombre completo de la versión habilitada más reciente, o null si la clave no existe. */
    fun versionVigente(clave: String): String?

    fun firmar(version: String, datos: ByteArray): ByteArray
}

/**
 * Las claves de producción (ADR-016): una de firma Ed25519 por comuna en Cloud KMS, llamada
 * firma-<comuna> dentro del llavero, y la de contenido, nacional, desde Secret Manager.
 *
 * Se activa solo si se configura midosis.claves.kms.llavero. La clave de contenido llega como
 * variable de entorno desde Secret Manager (Cloud Run la monta así); nunca está en el repositorio.
 */
@Component
@ConditionalOnProperty("midosis.claves.kms.llavero")
class ClavesDeKms(
    private val servicio: ServicioDeFirma,
    @Value("\${midosis.claves.kms.llavero}") private val llavero: String,
    @Value("\${midosis.claves.contenido.kid}") kidContenido: String,
    @Value("\${midosis.claves.contenido.valor}") valorContenido: String,
) : ProveedorDeClaves {

    private val contenido = ClaveDeContenido(kidContenido, Base64.getDecoder().decode(valorContenido.trim()))

    /** La versión vigente se consulta cada tanto, no en cada emisión: una rotación tarda en verse. */
    private val vigentes = ConcurrentHashMap<String, Pair<String?, Instant>>()

    override fun firmante(comuna: CodigoComuna): Firmante? {
        val clave = "$llavero/cryptoKeys/firma-${comuna.valor}"
        val (version, consultada) = vigentes[clave] ?: (null to Instant.EPOCH)
        val vigente = if (Duration.between(consultada, Instant.now()) < CACHE) {
            version
        } else {
            servicio.versionVigente(clave).also { vigentes[clave] = it to Instant.now() }
        }
        return vigente?.let { FirmanteKms(servicio, it, kid(comuna, it)) }
    }

    override fun contenido(): ClaveDeContenido = contenido

    companion object {
        private val CACHE: Duration = Duration.ofMinutes(10)

        /** Corto, porque viaja en cada QR: «13123-1» es la versión 1 de la clave de la comuna 13123. */
        fun kid(comuna: CodigoComuna, version: String): String = "${comuna.valor}-${version.substringAfterLast('/')}"
    }
}

class FirmanteKms(private val servicio: ServicioDeFirma, private val version: String, override val kid: String) : Firmante {
    override fun firmar(datos: ByteArray): ByteArray = servicio.firmar(version, datos)
}

/** El cliente real de Cloud KMS, con la verificación de integridad CRC32C que recomienda Google. */
@Component
@ConditionalOnProperty("midosis.claves.kms.llavero")
class ServicioDeFirmaKms : ServicioDeFirma, AutoCloseable {
    private val cliente: KeyManagementServiceClient by lazy { KeyManagementServiceClient.create() }

    override fun versionVigente(clave: String): String? =
        cliente.listCryptoKeyVersions(clave).iterateAll()
            .filter { it.state == CryptoKeyVersion.CryptoKeyVersionState.ENABLED }
            .maxByOrNull { it.name.substringAfterLast('/').toInt() }
            ?.name

    override fun firmar(version: String, datos: ByteArray): ByteArray {
        val crc = CRC32C().apply { update(datos) }.value
        val respuesta = cliente.asymmetricSign(
            AsymmetricSignRequest.newBuilder()
                .setName(version)
                .setData(ByteString.copyFrom(datos))
                .setDataCrc32C(com.google.protobuf.Int64Value.of(crc))
                .build(),
        )
        check(respuesta.verifiedDataCrc32C) { "KMS no confirmó la integridad de lo que se firmó" }
        val firma = respuesta.signature.toByteArray()
        check(respuesta.signatureCrc32C.value == CRC32C().apply { update(firma) }.value) { "la firma llegó dañada desde KMS" }
        return firma
    }

    override fun close() {
        cliente.close()
    }
}
