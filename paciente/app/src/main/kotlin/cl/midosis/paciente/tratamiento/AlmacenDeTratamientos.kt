package cl.midosis.paciente.tratamiento

import android.content.Context
import cl.midosis.credencial.Carga
import cl.midosis.credencial.CodecDeCarga
import cl.midosis.paciente.datos.ArchivoCifrado
import java.util.Base64

/**
 * Un tratamiento cargado en este teléfono: la carga ya verificada, cuándo se cargó y la
 * clave de la farmacia que lo firmó. Vive solo aquí (ADR-008): MiDosis no tiene una copia.
 */
data class TratamientoGuardado(val carga: Carga, val cargadoEn: Long, val kid: String)

/**
 * Los tratamientos del teléfono, cifrados con la clave del almacén de Android. No guarda nada
 * que identifique al paciente: la carga no lo trae (ADR-002) y la aplicación no lo pide (HU-11).
 */
class AlmacenDeTratamientos(private val archivo: ArchivoCifrado) {

    constructor(contexto: Context) : this(ArchivoCifrado(contexto, "tratamientos.bin"))

    sealed interface Resultado {
        data object Guardado : Resultado

        /**
         * El mismo código ya se cargó aquí. Es el registro local de la sección 7.6.1: impide
         * la recarga en este teléfono, no en otro (ADR-012).
         */
        data object YaCargado : Resultado
    }

    fun leer(): List<TratamientoGuardado> = archivo.leer()?.let(FormatoDeTratamientos::leer) ?: emptyList()

    fun guardar(nuevo: TratamientoGuardado): Resultado = synchronized(this) {
        val actuales = leer()
        if (actuales.any { it.carga.id == nuevo.carga.id }) return Resultado.YaCargado
        archivo.escribir(FormatoDeTratamientos.escribir(actuales + nuevo))
        return Resultado.Guardado
    }
}

/**
 * Una línea por tratamiento: la carga en CBOR (Base64), cuándo se cargó y el kid. La carga se
 * guarda tal como el contrato la define y se vuelve a validar al leerla.
 */
object FormatoDeTratamientos {
    fun escribir(tratamientos: List<TratamientoGuardado>): String =
        tratamientos.joinToString("\n") { t ->
            listOf(Base64.getEncoder().encodeToString(CodecDeCarga.codificar(t.carga)), t.cargadoEn.toString(), t.kid)
                .joinToString(" ")
        }

    fun leer(texto: String): List<TratamientoGuardado> =
        texto.lines().filter { it.isNotBlank() }.map { linea ->
            val partes = linea.split(" ")
            require(partes.size == 3) { "tratamiento guardado con ${partes.size} campos" }
            TratamientoGuardado(
                carga = CodecDeCarga.leer(Base64.getDecoder().decode(partes[0])),
                cargadoEn = partes[1].toLong(),
                kid = partes[2],
            )
        }
}
