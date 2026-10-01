package cl.midosis.core.dominio.catalogo

import java.text.Normalizer

/**
 * Lo que escribe el auxiliar en la búsqueda manual asistida, cuando el código del envase
 * no está en el catálogo o el lector no logra leerlo (HU-02).
 *
 * Se compara sin distinguir mayúsculas ni tildes: en el mesón nadie escribe «Losartán»
 * con tilde, y encontrar el producto no puede depender de eso.
 */
@JvmInline
value class TerminoDeBusqueda private constructor(val normalizado: String) {

    override fun toString(): String = normalizado

    companion object {
        const val LARGO_MINIMO = 2
        const val LARGO_MAXIMO = 50

        private val ESPACIOS = Regex("""\s+""")

        fun de(entrada: String): TerminoDeBusqueda {
            val limpio = normalizar(entrada.filter { !it.isISOControl() })
                .replace(ESPACIOS, " ")
                .trim()
            if (limpio.length < LARGO_MINIMO) {
                throw TerminoInvalido("escribe al menos $LARGO_MINIMO caracteres para buscar")
            }
            if (limpio.length > LARGO_MAXIMO) {
                throw TerminoInvalido("la búsqueda admite hasta $LARGO_MAXIMO caracteres")
            }
            return TerminoDeBusqueda(limpio)
        }

        /** Minúsculas, sin tildes ni diéresis. La eñe queda como ene. */
        fun normalizar(texto: String): String =
            Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replace(Regex("""\p{M}+"""), "")
                .lowercase()
    }
}

class TerminoInvalido(mensaje: String) : IllegalArgumentException(mensaje)
