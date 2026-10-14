package cl.midosis.paciente.rutina

import android.content.Context
import cl.midosis.motor.rutina.Rutina
import cl.midosis.paciente.datos.ArchivoCifrado
import java.time.LocalTime

/**
 * La rutina declarada en la entrevista (HU-13). Solo vive en el teléfono, cifrada: nunca se
 * transmite (sección 7.7 del informe).
 */
class AlmacenDeRutina(private val archivo: ArchivoCifrado) {

    constructor(contexto: Context) : this(ArchivoCifrado(contexto, "rutina.bin"))

    fun leer(): RutinaGuardada? = archivo.leer()?.let(FormatoDeRutina::leer)

    fun guardar(guardada: RutinaGuardada) = archivo.escribir(FormatoDeRutina.escribir(guardada))
}

/**
 * La rutina y si salió de la entrevista o se aplicó por defecto al omitirla. En el segundo
 * caso la aplicación ofrece ajustarla después (CU-P-02, flujo 1a).
 */
data class RutinaGuardada(val rutina: Rutina, val porDefecto: Boolean)

/** Una línea de texto: «07:00;08:00;13:00;20:00;23:00;nocturnas=si;defecto=no». */
object FormatoDeRutina {
    fun escribir(g: RutinaGuardada): String = with(g.rutina) {
        listOf(despertar, desayuno, almuerzo, cena, acostarse).joinToString(";") { it.toString() } +
            ";nocturnas=${siNo(toleraAlarmasNocturnas)};defecto=${siNo(g.porDefecto)}"
    }

    fun leer(texto: String): RutinaGuardada {
        val partes = texto.trim().split(";")
        require(partes.size == 7) { "rutina guardada con ${partes.size} campos" }
        val horas = partes.take(5).map(LocalTime::parse)
        return RutinaGuardada(
            rutina = Rutina(horas[0], horas[1], horas[2], horas[3], horas[4], toleraAlarmasNocturnas = valor(partes[5], "nocturnas")),
            porDefecto = valor(partes[6], "defecto"),
        )
    }

    private fun siNo(b: Boolean) = if (b) "si" else "no"

    private fun valor(parte: String, nombre: String): Boolean {
        val (clave, v) = parte.split("=").also { require(it.size == 2) { "campo «$parte» mal formado" } }
        require(clave == nombre) { "se esperaba «$nombre», llegó «$clave»" }
        return when (v) {
            "si" -> true
            "no" -> false
            else -> throw IllegalArgumentException("valor «$v» no es si ni no")
        }
    }
}
