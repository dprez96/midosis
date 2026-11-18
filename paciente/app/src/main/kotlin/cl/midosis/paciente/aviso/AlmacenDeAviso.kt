package cl.midosis.paciente.aviso

import android.content.Context
import java.io.File
import java.time.Instant

/**
 * Qué versión del [AvisoDeUso] leyó el paciente y cuándo. No es un dato de salud: se guarda sin
 * cifrar, en una carpeta que no entra en respaldos. Al borrar todos los datos (HU-25) se borra
 * con lo demás, y el aviso se vuelve a mostrar.
 */
class AlmacenDeAviso(private val archivo: File) {

    constructor(contexto: Context) : this(File(contexto.noBackupFilesDir, ARCHIVO))

    /** La versión leída, o null si nunca se leyó o el archivo no se entiende. */
    fun versionLeida(): Int? =
        archivo.takeIf { it.exists() }?.readText()?.substringBefore(";")?.trim()?.toIntOrNull()

    fun debeMostrarse(): Boolean = (versionLeida() ?: 0) < AvisoDeUso.VERSION

    /** «1;2026-11-18T13:00:00Z»: la versión y el momento en que se leyó. */
    fun registrarLectura(ahora: Instant = Instant.now()) {
        archivo.parentFile?.mkdirs()
        archivo.writeText("${AvisoDeUso.VERSION};$ahora")
    }

    private companion object {
        const val ARCHIVO = "aviso-de-uso.txt"
    }
}
