package cl.midosis.paciente.alarmas

import android.content.Context
import cl.midosis.paciente.datos.ArchivoCifrado
import java.time.Instant

/**
 * El historial de lo que el paciente contestó a cada recordatorio (HU-16): la cuenta real de su
 * tratamiento. Vive solo en el teléfono y cifrado (ADR-008); la continuidad del tratamiento
 * (HU-21) lo conserva al cargar el tramo siguiente.
 */
class HistorialDeTomas(private val archivo: ArchivoCifrado) {

    constructor(contexto: Context) : this(ArchivoCifrado(contexto, "historial-de-tomas.bin"))

    fun leer(): List<RespuestaRegistrada> = archivo.leer()?.let(FormatoDelHistorial::leer) ?: emptyList()

    fun anotar(respuesta: RespuestaRegistrada) = synchronized(BLOQUEO) {
        archivo.escribir(FormatoDelHistorial.escribir(leer() + respuesta))
    }

    /**
     * Si el aviso ya tiene una respuesta definitiva (tomada u omitida). Una pospuesta no lo
     * cierra: el recordatorio vuelve a aparecer.
     */
    fun cerrado(aviso: Int, programada: Instant): Boolean =
        leer().any { it.aviso == aviso && it.programada == programada && it.respuesta != Respuesta.POSPUESTA }

    fun borrar() = synchronized(BLOQUEO) { archivo.borrar() }

    private companion object {
        val BLOQUEO = Any()
    }
}
