package cl.midosis.paciente.codigo

import cl.midosis.credencial.Carga
import cl.midosis.credencial.ClaveDeContenido
import cl.midosis.credencial.CodecDeCarga
import cl.midosis.credencial.EmisorDeCodigos
import cl.midosis.credencial.FirmanteEd25519
import cl.midosis.credencial.Observacion
import cl.midosis.credencial.Producto
import cl.midosis.credencial.Via
import java.security.SecureRandom
import org.json.JSONObject

/**
 * Códigos generados en el teléfono con las claves de prueba, para mostrar cómo responde
 * la verificación mientras no exista la emisión real (HU-01, S3). Solo existe en la
 * compilación de depuración: las claves privadas de prueba no van en la de producción.
 *
 * Los datos son sintéticos.
 */
class CodigosDeEjemplo(
    private val firmante: FirmanteEd25519,
    private val firmanteDesconocido: FirmanteEd25519,
    private val contenido: ClaveDeContenido,
) {
    enum class Caso(val nombre: String) {
        VALIDO("Código válido"),
        ALTERADO("Código alterado"),
        EMISOR_DESCONOCIDO("Farmacia no reconocida"),
        VENCIDO("Código vencido"),
        OTRO_QR("Un QR cualquiera"),
        VERSION_FUTURA("Versión más nueva"),
    }

    fun generar(caso: Caso, ahora: Long): String {
        val reciente = ahora - 60
        return when (caso) {
            Caso.VALIDO -> emitir(firmante, reciente)
            Caso.ALTERADO -> alterar(emitir(firmante, reciente))
            Caso.EMISOR_DESCONOCIDO -> emitir(firmanteDesconocido, reciente)
            Caso.VENCIDO -> emitir(firmante, ahora - VIGENCIA_S - 3600)
            Caso.OTRO_QR -> "HTTPS://WWW.EJEMPLO.CL/PROMOCION"
            Caso.VERSION_FUTURA -> "MD2:" + emitir(firmante, reciente).removePrefix("MD1:")
        }
    }

    private fun emitir(quien: FirmanteEd25519, emitidoEn: Long): String =
        EmisorDeCodigos(quien, contenido).emitir(losartan(emitidoEn))

    /** Cambia un carácter del medio por otro del alfabeto, como una mancha en el papel. */
    private fun alterar(codigo: String): String {
        val i = codigo.length / 2
        val nuevo = if (codigo[i] == 'A') 'B' else 'A'
        return codigo.substring(0, i) + nuevo + codigo.substring(i + 1)
    }

    private fun losartan(emitidoEn: Long) = Carga(
        comuna = "13123",
        emisor = "CL-FP-13123-01",
        id = ulid(emitidoEn),
        emitidoEn = emitidoEn,
        expiraEn = emitidoEn + VIGENCIA_S,
        productos = listOf(
            Producto(
                gtin = "7802250012344",
                etiqueta = "Losartán 50 mg comp.",
                principioActivo = "Losartán",
                dosis = "1 comprimido",
                intervalo = "PT12H",
                duracionIndicadaDias = 180,
                dosisEntregadas = 60,
                coberturaDias = CodecDeCarga.coberturaDias(60, "PT12H"),
                via = Via.ORAL,
                observaciones = listOf(Observacion.CON_ALIMENTOS),
                nota = "No suspender sin indicación médica",
            ),
        ),
    )

    companion object {
        private const val VIGENCIA_S = 72L * 3600
        private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        private val aleatorio = SecureRandom()

        /** ULID: 48 bits de milisegundos y 80 aleatorios, en 26 caracteres. */
        fun ulid(segundos: Long): String {
            val tiempo = segundos * 1000
            val azar = ByteArray(10).also { aleatorio.nextBytes(it) }
            val bits = java.math.BigInteger(1, ByteArray(6) { (tiempo shr (8 * (5 - it))).toByte() } + azar)
            return (25 downTo 0).joinToString("") { CROCKFORD[bits.shiftRight(5 * it).toInt() and 31].toString() }
        }

        /** Desde assets/firmantes-de-prueba.json, que solo existe en la compilación de depuración. */
        fun desdeJson(firmantes: String, contenido: ClaveDeContenido): CodigosDeEjemplo {
            val porKid = JSONObject(firmantes).getJSONArray("firmantes").objetos()
                .associate { it.getString("kid") to FirmanteEd25519(it.getString("kid"), hex(it.getString("privada"))) }
            return CodigosDeEjemplo(porKid.getValue("prueba-1"), porKid.getValue("prueba-desconocida"), contenido)
        }
    }
}
