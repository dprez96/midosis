package cl.midosis.core.infraestructura.claves

import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.credencial.ClaveDeContenido
import cl.midosis.credencial.Firmante
import cl.midosis.credencial.FirmanteEd25519
import java.io.File
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/**
 * Las claves con que core emite (ADR-016): una de firma por comuna y la de contenido, nacional.
 *
 * En producción la firma la hace Cloud KMS, que nunca entrega la clave privada, y la clave de
 * contenido viene de Secret Manager. Mientras ese proveedor no exista no hay ninguno registrado,
 * y la emisión responde que no está disponible: nunca se emite con claves de prueba fuera de
 * los entornos de prueba.
 */
interface ProveedorDeClaves {
    /** Null si la comuna no tiene clave de firma. */
    fun firmante(comuna: CodigoComuna): Firmante?

    fun contenido(): ClaveDeContenido
}

/**
 * Las claves de prueba de contrato/vectores, para correr core en el equipo y en las pruebas.
 * Solo se activa si se configura midosis.claves.prueba (perfil local y pruebas). No protegen
 * nada, y la aplicación de producción las rechaza.
 */
@Component
@ConditionalOnProperty("midosis.claves.prueba")
class ClavesDePrueba(@Value("\${midosis.claves.prueba}") archivo: String) : ProveedorDeClaves {

    private val firmantes: Map<String, Firmante>
    private val contenido: ClaveDeContenido

    init {
        val json = JsonMapper.builder().build().readTree(File(archivo))
        firmantes = json.get("firma").toList()
            .filter { it.get("enElConjunto").asBoolean() && !it.get("revocada").asBoolean() && it.get("kid").asString() == KID_FIRMA }
            .associate { it.get("comuna").asString() to FirmanteEd25519(it.get("kid").asString(), hex(it.get("privada").asString())) }
        val c = json.get("contenido").toList().first { it.get("kid").asString() == KID_CONTENIDO }
        contenido = ClaveDeContenido(c.get("kid").asString(), hex(c.get("clave").asString()))
    }

    override fun firmante(comuna: CodigoComuna): Firmante? = firmantes[comuna.valor]

    override fun contenido(): ClaveDeContenido = contenido

    private companion object {
        /** La clave vigente de la comuna 13123 en los vectores. */
        const val KID_FIRMA = "prueba-1"
        const val KID_CONTENIDO = "prueba-c1"

        fun hex(texto: String) = ByteArray(texto.length / 2) { texto.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
    }
}
