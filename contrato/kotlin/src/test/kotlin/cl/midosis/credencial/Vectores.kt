package cl.midosis.credencial

import java.io.File
import java.security.SecureRandom
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** Acceso a contrato/vectores, la fuente de verdad del formato. */
object Vectores {
    private val directorio: File = File(
        System.getProperty("midosis.vectores") ?: error("falta la propiedad midosis.vectores; la fija build.gradle.kts"),
    )
    private val json = JsonMapper.builder().build()

    fun leer(nombre: String): JsonNode = json.readTree(File(directorio, nombre))

    /** Los vectores de la carga útil: un caso por archivo, sin los del código completo. */
    fun deCarga(): List<Pair<String, JsonNode>> =
        archivos { (it.startsWith("valido-") || it.startsWith("invalido-") || it.startsWith("limite-")) }

    fun deCodigo(): List<Pair<String, JsonNode>> = archivos { it.startsWith("codigo-") }

    private fun archivos(filtro: (String) -> Boolean): List<Pair<String, JsonNode>> =
        directorio.listFiles { f -> f.name.endsWith(".json") && filtro(f.name) }!!
            .sortedBy { it.name }
            .map { it.name.removeSuffix(".json") to json.readTree(it) }

    /** El conjunto de confianza de la aplicación de prueba, desde claves-de-prueba.json. */
    fun claves(): ClavesDeConfianza {
        val c = leer("claves-de-prueba.json")
        val emisores = c.get("firma").toList().filter { it.get("enElConjunto").asBoolean() }.map {
            ClaveDeEmisor(
                kid = it.get("kid").asString(),
                publica = hex(it.get("publica").asString()),
                comuna = it.get("comuna").asString(),
                vigenteDesde = it.get("vigenteDesde").asLong(),
                vigenteHasta = it.get("vigenteHasta").takeUnless { v -> v.isNull }?.asLong(),
                revocada = it.get("revocada").asBoolean(),
            )
        }
        val contenido = c.get("contenido").toList().filter { it.get("enElConjunto").asBoolean() }.map {
            ClaveDeContenido(it.get("kid").asString(), hex(it.get("clave").asString()))
        }
        return ClavesDeConfianza(emisores, contenido, admiteClavesDePrueba = true)
    }

    fun firmante(kid: String): FirmanteEd25519 {
        val clave = leer("claves-de-prueba.json").get("firma").toList().first { it.get("kid").asString() == kid }
        return FirmanteEd25519(kid, hex(clave.get("privada").asString()))
    }

    fun contenido(kid: String): ClaveDeContenido {
        val clave = leer("claves-de-prueba.json").get("contenido").toList().first { it.get("kid").asString() == kid }
        return ClaveDeContenido(kid, hex(clave.get("clave").asString()))
    }
}

fun hex(texto: String): ByteArray = ByteArray(texto.length / 2) { texto.substring(2 * it, 2 * it + 2).toInt(16).toByte() }

fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

/** Entrega siempre los mismos bytes: para reproducir el IV de un vector. */
class AleatorioFijo(private val bytes: ByteArray) : SecureRandom() {
    override fun nextBytes(destino: ByteArray) {
        bytes.copyInto(destino, endIndex = destino.size)
    }
}
