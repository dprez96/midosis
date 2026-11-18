package cl.midosis.paciente.alarmas

import android.content.Context
import android.util.Log
import cl.midosis.paciente.cripto.Cifrador
import cl.midosis.paciente.cripto.ClaveDelDispositivo
import java.io.File
import java.time.LocalDateTime

/**
 * Guarda el plan de alarmas vigente, cifrado, para poder reprogramarlo cuando el teléfono
 * se reinicia (HU-17): Android borra todas las alarmas al apagarse.
 *
 * El plan incluye medicamentos y dosis, que son datos de salud, así que se guarda cifrado
 * con una clave del almacén de Android (ADR-008), en una carpeta que no entra en respaldos.
 * Cuando exista la base cifrada del tratamiento (HU-08), el plan pasa a vivir en ella.
 */
class AlmacenDelPlan(
    contexto: Context,
    private val cifrador: Cifrador = Cifrador(ClaveDelDispositivo::obtener),
) {
    private val archivo = File(contexto.noBackupFilesDir, ARCHIVO)

    /** Todas las alarmas guardadas, incluidas las que ya pasaron. */
    fun leer(): List<SolicitudDeAlarma> = synchronized(BLOQUEO) {
        if (!archivo.exists()) return emptyList()
        return try {
            FormatoDelPlan.leer(String(cifrador.descifrar(archivo.readBytes()), Charsets.UTF_8))
        } catch (e: Exception) {
            // Sin la clave, por ejemplo tras restaurar el teléfono, el plan es irrecuperable:
            // se descarta en vez de dejar la aplicación rota. El paciente vuelve a cargarlo.
            Log.w(ETIQUETA, "no se pudo descifrar el plan guardado; se descarta", e)
            archivo.delete()
            emptyList()
        }
    }

    /**
     * Agrega alarmas al plan guardado. Una alarma con el mismo identificador reemplaza a la
     * anterior, y las que ya pasaron se eliminan para que el archivo no crezca sin límite.
     */
    fun agregar(nuevas: List<SolicitudDeAlarma>, ahora: LocalDateTime) = synchronized(BLOQUEO) {
        val porId = leer().associateBy { it.id }.toMutableMap()
        nuevas.forEach { porId[it.id] = it }
        guardar(porId.values.filter { it.momento.isAfter(ahora) }.sortedBy { it.momento })
    }

    /**
     * Quita del plan las líneas que cumplen [esDe] (texto y momento de la alarma). Devuelve las
     * alarmas que quedaron sin ninguna línea, para cancelarlas, y las que perdieron alguna, para
     * volver a entregarlas con su texto nuevo.
     */
    fun quitar(esDe: (String, LocalDateTime) -> Boolean, ahora: LocalDateTime): Pair<List<SolicitudDeAlarma>, List<SolicitudDeAlarma>> =
        synchronized(BLOQUEO) {
            val vacias = mutableListOf<SolicitudDeAlarma>()
            val cambiadas = mutableListOf<SolicitudDeAlarma>()
            val nuevo = leer().mapNotNull { s ->
                val quedan = s.lineas.filterNot { esDe(it, s.momento) }
                when {
                    quedan.size == s.lineas.size -> s
                    quedan.isEmpty() -> { vacias += s; null }
                    else -> s.copy(lineas = quedan).also { cambiadas += it }
                }
            }
            guardar(nuevo.filter { it.momento.isAfter(ahora) })
            vacias to cambiadas
        }

    fun vaciar() = synchronized(BLOQUEO) { archivo.delete() }

    private fun guardar(plan: List<SolicitudDeAlarma>) {
        val temporal = File(archivo.parentFile, "$ARCHIVO.tmp")
        temporal.writeBytes(cifrador.cifrar(FormatoDelPlan.escribir(plan).toByteArray(Charsets.UTF_8)))
        // Reemplazo en un solo paso: un corte de energía a mitad de camino no deja el plan
        // a medio escribir.
        if (!temporal.renameTo(archivo)) {
            archivo.delete()
            temporal.renameTo(archivo)
        }
    }

    private companion object {
        const val ARCHIVO = "plan-de-alarmas.bin"
        const val ETIQUETA = "MiDosis"
        val BLOQUEO = Any()
    }
}
