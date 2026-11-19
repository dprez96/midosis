package cl.midosis.paciente.perfiles

import android.content.Context
import cl.midosis.credencial.Carga
import cl.midosis.paciente.datos.ArchivoCifrado
import cl.midosis.paciente.tratamiento.Continuidad
import cl.midosis.paciente.tratamiento.TratamientoGuardado
import java.security.SecureRandom
import java.util.Base64

/**
 * Los colores de los perfiles: pocos y bien distintos entre sí, para reconocer de un vistazo a
 * quién corresponde un aviso (RN-21, RN-23).
 */
enum class ColorDePerfil(val nombre: String, val argb: Long) {
    AZUL("Azul", 0xFF1565C0),
    VERDE("Verde", 0xFF2E7D32),
    NARANJA("Naranja", 0xFFE65100),
    MORADO("Morado", 0xFF6A1B9A),
}

/**
 * Un paciente cuyos tratamientos se administran en este teléfono (HU-31, RN-21). Solo un apodo,
 * su inicial y un color: nunca el nombre completo ni el RUT. Existe solo aquí.
 */
data class Perfil(val id: String, val apodo: String, val color: ColorDePerfil) {
    val inicial: Char get() = apodo.trim().first().uppercaseChar()
}

/** Las reglas de los perfiles (RN-21, RN-22). */
object Perfiles {

    const val MAXIMO = 4
    const val APODO_MAXIMO = 12

    /** El perfil con el que parte el teléfono: todo lo cargado antes de la HU-31 es suyo. */
    const val PRINCIPAL = ""

    fun principal() = Perfil(PRINCIPAL, "Yo", ColorDePerfil.AZUL)

    /** Qué tiene de malo el apodo, en palabras para el paciente, o null si sirve. */
    fun problemaDelApodo(apodo: String, otros: List<Perfil>): String? {
        val limpio = apodo.trim()
        return when {
            limpio.isEmpty() -> "Escribe un apodo, por ejemplo «Mamá» o «Juan»."
            limpio.length > APODO_MAXIMO -> "El apodo puede tener hasta $APODO_MAXIMO letras."
            !limpio.first().isLetter() -> "El apodo debe empezar con una letra."
            limpio.any { it.isDigit() } -> "Usa solo letras: no escribas el RUT ni números."
            otros.any { it.apodo.equals(limpio, ignoreCase = true) } -> "Ya hay un perfil con ese apodo."
            else -> null
        }
    }

    /** El primer color que ningún otro perfil usa. */
    fun colorLibre(perfiles: List<Perfil>): ColorDePerfil =
        ColorDePerfil.entries.firstOrNull { c -> perfiles.none { it.color == c } } ?: ColorDePerfil.AZUL

    /**
     * El lugar del perfil en la lista, de 0 a 3. Distingue sus alarmas de las de otro perfil a la
     * misma hora (RN-23).
     */
    fun orden(perfiles: List<Perfil>, id: String): Int = perfiles.indexOfFirst { it.id == id }.coerceAtLeast(0)

    private val aleatorio = SecureRandom()
    private const val ALFABETO = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    /**
     * Los principios activos de [carga] que ya tiene vigentes otro perfil (RF-M-27): suele delatar
     * que se está cargando en el perfil equivocado. Devuelve cada principio con el perfil que lo
     * tiene.
     */
    fun principiosEnOtroPerfil(
        carga: Carga,
        elegido: String,
        tratamientos: List<TratamientoGuardado>,
        perfiles: List<Perfil>,
    ): List<Pair<String, Perfil>> =
        Continuidad.vigentes(tratamientos.filter { it.perfil != elegido }).mapNotNull { (t, p) ->
            val coincide = carga.productos.any { it.principioActivo.equals(p.principioActivo, ignoreCase = true) }
            val perfil = perfiles.firstOrNull { it.id == t.perfil }
            if (coincide && perfil != null) p.principioActivo to perfil else null
        }.distinct()

    fun nuevoId(): String = (1..12).map { ALFABETO[aleatorio.nextInt(ALFABETO.length)] }.joinToString("")
}

/** Una línea por perfil: «id;apodo en Base64;COLOR». El principal tiene id vacío. */
object FormatoDePerfiles {
    fun escribir(perfiles: List<Perfil>): String =
        perfiles.joinToString("\n") { p ->
            "${p.id};${Base64.getEncoder().encodeToString(p.apodo.toByteArray(Charsets.UTF_8))};${p.color.name}"
        }

    fun leer(texto: String): List<Perfil> =
        texto.lines().filter { it.isNotBlank() }.map { linea ->
            val partes = linea.split(";")
            require(partes.size == 3) { "perfil guardado con ${partes.size} campos" }
            Perfil(partes[0], String(Base64.getDecoder().decode(partes[1]), Charsets.UTF_8), ColorDePerfil.valueOf(partes[2]))
        }
}

/**
 * Los perfiles del teléfono, cifrados como el resto de los datos de salud (ADR-008). Sin nada
 * guardado hay uno solo, el principal.
 */
class AlmacenDePerfiles(private val archivo: ArchivoCifrado) {

    constructor(contexto: Context) : this(ArchivoCifrado(contexto, "perfiles.bin"))

    fun leer(): List<Perfil> = archivo.leer()?.let(FormatoDePerfiles::leer)?.takeIf { it.isNotEmpty() } ?: listOf(Perfiles.principal())

    /** El perfil de un aviso, solo si hay más de uno: con uno solo, el aviso no lo nombra. */
    fun paraAviso(id: String): Perfil? = leer().takeIf { it.size > 1 }?.firstOrNull { it.id == id }

    fun guardar(perfiles: List<Perfil>) {
        require(perfiles.size in 1..Perfiles.MAXIMO) { "un teléfono tiene de 1 a ${Perfiles.MAXIMO} perfiles" }
        archivo.escribir(FormatoDePerfiles.escribir(perfiles))
    }
}
