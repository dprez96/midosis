package cl.midosis.core.aplicacion.emision

import cl.midosis.core.aplicacion.canje.CanjeDeCodigos
import cl.midosis.core.dominio.catalogo.CatalogoDeProductos
import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.core.dominio.emision.CodigoEmitido
import cl.midosis.core.dominio.emision.EmisionInvalida
import cl.midosis.core.dominio.emision.EmisionNoDisponible
import cl.midosis.core.dominio.emision.SolicitudDeEmision
import cl.midosis.core.dominio.posologia.ProductoNoEncontrado
import cl.midosis.core.infraestructura.claves.ProveedorDeClaves
import cl.midosis.core.infraestructura.persistencia.ContextoComuna
import cl.midosis.core.infraestructura.persistencia.EmisionesJdbc
import cl.midosis.credencial.Carga
import cl.midosis.credencial.CodecDeCarga
import cl.midosis.credencial.CodigoRechazado
import cl.midosis.credencial.EmisorDeCodigos
import cl.midosis.credencial.Producto
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service

/**
 * Emisión del código de tratamiento (HU-01) con el consentimiento del paciente (HU-05).
 *
 * Arma la carga con los datos del catálogo de la comuna y lo registrado en el mesón, la firma
 * con la clave de la comuna, registra el código corto y el PIN (HU-10) y deja el registro
 * seudonimizado de la emisión. Nada de lo que recibe ni de lo que guarda identifica al paciente.
 */
@Service
class EmisionDeCodigos(
    private val contexto: ContextoComuna,
    private val catalogo: CatalogoDeProductos,
    private val emisiones: EmisionesJdbc,
    private val canje: CanjeDeCodigos,
    private val claves: ObjectProvider<ProveedorDeClaves>,
) {
    private val aleatorio = SecureRandom()

    fun emitir(comuna: CodigoComuna, autor: String, solicitud: SolicitudDeEmision, ahora: Instant = Instant.now()): CodigoEmitido {
        val productos = contexto.en(comuna) {
            solicitud.lineas.map { linea ->
                val producto = catalogo.buscar(linea.gtin) ?: throw ProductoNoEncontrado()
                val tomas = linea.tomasEntregadas
                val intervalo = linea.frecuencia.toString()
                Producto(
                    gtin = linea.gtin.valor,
                    etiqueta = recortar(producto.nombre, 40),
                    principioActivo = recortar(producto.principioActivo, 60),
                    dosis = recortar(linea.dosis.texto, 40),
                    intervalo = intervalo,
                    duracionIndicadaDias = linea.duracionIndicadaDias,
                    dosisEntregadas = tomas,
                    coberturaDias = CodecDeCarga.coberturaDias(tomas, intervalo),
                    via = linea.via,
                    nota = linea.indicaciones?.let { recortar(it, 240) },
                )
            }
        }

        // Las claves se piden después de revisar la atención: un producto ajeno responde igual
        // que uno inexistente, haya o no claves.
        val proveedor = claves.ifAvailable ?: throw EmisionNoDisponible("la emisión todavía no tiene claves de producción")
        val firmante = proveedor.firmante(comuna) ?: throw EmisionNoDisponible("la comuna no tiene clave de firma")

        val emitida = ahora.epochSecond
        val expira = emitida + VIGENCIA.seconds
        val carga = Carga(
            comuna = comuna.valor,
            emisor = puntoDeDispensacion(comuna),
            id = Ulid.generar(ahora, aleatorio),
            emitidoEn = emitida,
            expiraEn = expira,
            productos = productos,
        )
        // Lo que se firma tiene que poder leerse: se pasa por el mismo lector de la aplicación.
        try {
            CodecDeCarga.leer(CodecDeCarga.codificar(carga))
        } catch (e: CodigoRechazado) {
            throw EmisionInvalida("el código no cumple el contrato: ${e.regla}")
        }

        val codigo = EmisorDeCodigos(firmante, proveedor.contenido(), aleatorio).emitir(carga)
        contexto.en(comuna) {
            emisiones.registrar(carga, autor, firmante.kid, solicitud.consentimiento)
        }
        val registrado = canje.registrar(comuna, codigo, Instant.ofEpochSecond(expira), carga.id)
        return CodigoEmitido(carga.id, codigo, registrado.codigoCorto.agrupado, registrado.pin.valor, Instant.ofEpochSecond(expira))
    }

    companion object {
        /** RN-03: el canje vence a las 72 horas. */
        val VIGENCIA: Duration = Duration.ofHours(72)

        /**
         * Por ahora un punto de dispensación por comuna, la farmacia popular. Cuando una comuna
         * tenga más de uno, el punto vendrá del perfil del usuario.
         */
        fun puntoDeDispensacion(comuna: CodigoComuna) = "CL-FP-${comuna.valor}-01"

        /** Corta el texto para que quepa en el límite del contrato, que se cuenta en bytes UTF-8. */
        fun recortar(texto: String, bytes: Int): String {
            var resultado = texto.filterNot { Character.getType(it) == Character.CONTROL.toInt() }.trim()
            while (resultado.toByteArray(Charsets.UTF_8).size > bytes) resultado = resultado.dropLast(1)
            return resultado.trimEnd()
        }
    }
}

/** ULID: 48 bits de milisegundos y 80 aleatorios, en Crockford Base32. */
object Ulid {
    private const val ALFABETO = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    fun generar(momento: Instant, aleatorio: SecureRandom): String {
        val tiempo = momento.toEpochMilli()
        val azar = ByteArray(10).also(aleatorio::nextBytes)
        val bits = java.math.BigInteger(1, ByteArray(6) { (tiempo shr (8 * (5 - it))).toByte() } + azar)
        return (25 downTo 0).joinToString("") { ALFABETO[bits.shiftRight(5 * it).toInt() and 31].toString() }
    }
}
