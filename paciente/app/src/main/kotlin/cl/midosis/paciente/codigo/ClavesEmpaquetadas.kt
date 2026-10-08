package cl.midosis.paciente.codigo

import android.content.Context
import cl.midosis.credencial.ClaveDeContenido
import cl.midosis.credencial.ClaveDeEmisor
import cl.midosis.credencial.ClavesDeConfianza
import cl.midosis.credencial.LectorDeCodigos
import cl.midosis.paciente.BuildConfig
import java.time.Instant
import org.json.JSONObject

/**
 * Las claves con que la aplicación verifica los códigos sin conexión (sección 7.6.2 del
 * informe). Vienen empaquetadas en assets/claves-de-confianza.json: la compilación de
 * depuración trae las de prueba de contrato/vectores y la de producción, las reales.
 *
 * Solo la compilación de depuración admite claves «prueba-…». Si una llegara a la de
 * producción, la aplicación falla al cargarlas en vez de aceptar códigos falsificables.
 */
object ClavesEmpaquetadas {
    const val ARCHIVO = "claves-de-confianza.json"

    fun cargar(contexto: Context): ClavesDeConfianza =
        desdeJson(contexto.assets.open(ARCHIVO).bufferedReader().use { it.readText() }, BuildConfig.DEBUG)

    fun desdeJson(texto: String, admiteClavesDePrueba: Boolean): ClavesDeConfianza {
        val json = JSONObject(texto)
        val emisores = json.getJSONArray("emisores").objetos().map {
            ClaveDeEmisor(
                kid = it.getString("kid"),
                publica = hex(it.getString("publica")),
                comuna = it.getString("comuna"),
                vigenteDesde = it.getLong("vigenteDesde"),
                vigenteHasta = if (it.isNull("vigenteHasta")) null else it.getLong("vigenteHasta"),
                revocada = it.getBoolean("revocada"),
            )
        }
        val contenido = json.getJSONArray("contenido").objetos().map {
            ClaveDeContenido(it.getString("kid"), hex(it.getString("clave")))
        }
        return ClavesDeConfianza(emisores, contenido, admiteClavesDePrueba)
    }

    /** El lector con las claves empaquetadas y la hora del teléfono. */
    fun lector(contexto: Context): LectorDeCodigos = LectorDeCodigos(cargar(contexto)) { Instant.now().epochSecond }
}

internal fun org.json.JSONArray.objetos(): List<JSONObject> = List(length()) { getJSONObject(it) }

internal fun hex(texto: String): ByteArray {
    require(texto.length % 2 == 0) { "hexadecimal de largo impar" }
    return ByteArray(texto.length / 2) { texto.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
}
