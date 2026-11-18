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
                    "suspendido" -> EstadoDeCodigo.Corregido(suspender = true, alarmasDesactivadas = true)
                    "revocado" -> EstadoDeCodigo.Revocado
                    else -> null
                }
                estado?.let { partes[0] to it }
            }.toMap()
        }

    /**
     * Reemplaza lo anotado de [consultados] con lo que respondió core. Si el paciente ya
     * desactivó las alarmas de un código suspendido, eso se conserva.
     */
    fun actualizar(consultados: Map<String, EstadoDeCodigo>) {
        val antes = leer()
        val todos = antes + consultados.mapValues { (id, nuevo) ->
            val previo = antes[id]
            if (previo is EstadoDeCodigo.Corregido && previo.alarmasDesactivadas && nuevo is EstadoDeCodigo.Corregido && nuevo.suspender) previo else nuevo
        }
        escribir(todos)
    }

    /** El paciente desactivó las alarmas del código suspendido [id]. */
    fun marcarAlarmasDesactivadas(id: String) {
        escribir(leer() + (id to EstadoDeCodigo.Corregido(suspender = true, alarmasDesactivadas = true)))
    }

    private fun escribir(todos: Map<String, EstadoDeCodigo>) {
        archivo.parentFile?.mkdirs()
        archivo.writeText(
            todos.mapNotNull { (id, e) ->
                when (e) {
                    is EstadoDeCodigo.Corregido -> "$id ${
                        when {
                            !e.suspender -> "continuar"
                            e.alarmasDesactivadas -> "suspendido"
                            else -> "suspender"
                        }
                    }"
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
