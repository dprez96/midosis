package cl.midosis.paciente.tratamiento

import android.content.Context
import java.io.File

/**
 * El último estado conocido de cada código corregido o revocado (HU-33), para mostrarlo también
 * sin conexión. Solo guarda el jti y el estado, sin datos de salud; se borra con todo lo demás
 * (HU-25). Los vigentes no se anotan.
 */
class AlmacenDeEstados(private val archivo: File) {

    constructor(contexto: Context) : this(File(contexto.noBackupFilesDir, "estados.txt"))

    fun leer(): Map<String, EstadoDeCodigo> =
        if (!archivo.exists()) {
            emptyMap()
        } else {
            archivo.readLines().mapNotNull { linea ->
                val partes = linea.split(" ")
                val estado = when (partes.getOrNull(1)) {
                    "continuar" -> EstadoDeCodigo.Corregido(suspender = false)
                    "suspender" -> EstadoDeCodigo.Corregido(suspender = true)
                    "revocado" -> EstadoDeCodigo.Revocado
                    else -> null
                }
                estado?.let { partes[0] to it }
            }.toMap()
        }

    /** Reemplaza lo anotado de [consultados] con lo que respondió core. */
    fun actualizar(consultados: Map<String, EstadoDeCodigo>) {
        val todos = leer() + consultados
        archivo.parentFile?.mkdirs()
        archivo.writeText(
            todos.mapNotNull { (id, e) ->
                when (e) {
                    is EstadoDeCodigo.Corregido -> "$id ${if (e.suspender) "suspender" else "continuar"}"
                    EstadoDeCodigo.Revocado -> "$id revocado"
                    else -> null
                }
            }.joinToString("\n"),
        )
    }

    fun quitar(id: String) {
        if (archivo.exists()) archivo.writeText(archivo.readLines().filterNot { it.startsWith("$id ") }.joinToString("\n"))
    }
}
