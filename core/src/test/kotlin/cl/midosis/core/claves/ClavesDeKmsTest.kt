package cl.midosis.core.claves

import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.core.infraestructura.claves.ClavesDeKms
import cl.midosis.core.infraestructura.claves.ServicioDeFirma
import cl.midosis.credencial.Carga
import cl.midosis.credencial.ClaveDeContenido
import cl.midosis.credencial.ClaveDeEmisor
import cl.midosis.credencial.ClavesDeConfianza
import cl.midosis.credencial.EmisorDeCodigos
import cl.midosis.credencial.FirmanteEd25519
import cl.midosis.credencial.LectorDeCodigos
import cl.midosis.credencial.Lectura
import cl.midosis.credencial.Producto
import cl.midosis.credencial.Via
import java.security.SecureRandom
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** El proveedor de producción, con un KMS falso que firma con una clave en memoria. */
class ClavesDeKmsTest {

    private val llavero = "projects/p/locations/southamerica-west1/keyRings/midosis-firma"
    private val clavePrivada = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private val enMemoria = FirmanteEd25519("x", clavePrivada)

    private inner class KmsFalso : ServicioDeFirma {
        var consultas = 0

        override fun versionVigente(clave: String): String? {
            consultas++
            return if (clave == "$llavero/cryptoKeys/firma-13123") "$clave/cryptoKeyVersions/3" else null
        }

        override fun firmar(version: String, datos: ByteArray): ByteArray = enMemoria.firmar(datos)
    }

    private val contenido = ByteArray(32) { 7 }
    private val kms = KmsFalso()
    private val claves = ClavesDeKms(kms, llavero, "c1", Base64.getEncoder().encodeToString(contenido))

    @Test
    fun `firma con la versión vigente de la clave de la comuna, con un kid corto`() {
        val firmante = claves.firmante(CodigoComuna.de("13123"))!!
        assertEquals("13123-3", firmante.kid)
    }

    @Test
    fun `una comuna sin clave en KMS no puede emitir`() {
        assertNull(claves.firmante(CodigoComuna.de("13101")))
    }

    @Test
    fun `no consulta KMS en cada emisión`() {
        repeat(5) { claves.firmante(CodigoComuna.de("13123")) }
        assertEquals(1, kms.consultas)
    }

    @Test
    fun `lo firmado por KMS lo acepta el lector de la aplicación`() {
        val firmante = claves.firmante(CodigoComuna.de("13123"))!!
        val ahora = System.currentTimeMillis() / 1000
        val carga = Carga(
            "13123", "CL-FP-13123-01", "01M0877000WQNC8FHDYAA6Y83C", ahora, ahora + 3600,
            listOf(Producto("7802250012344", "Losartán 50 mg", "Losartán", "1 comprimido", "PT24H", 30, 30, 30, Via.ORAL)),
        )
        val codigo = EmisorDeCodigos(firmante, claves.contenido()).emitir(carga)
        val confianza = ClavesDeConfianza(
            listOf(ClaveDeEmisor("13123-3", enMemoria.publica, "13123", 0)),
            listOf(ClaveDeContenido("c1", contenido)),
        )
        assertIs<Lectura.Aceptado>(LectorDeCodigos(confianza) { ahora }.leer(codigo))
    }
}
