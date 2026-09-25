package cl.midosis.core.dominio.comuna

/**
 * Identificador territorial de la comuna emisora, clave «cm» de la carga util.
 * Es ademas el discriminador con que se aisla el dato de cada cliente: no hay
 * consulta al catalogo ni a los tratamientos que no lo lleve (ADR-011).
 */
@JvmInline
value class CodigoComuna private constructor(val valor: String) {

    override fun toString(): String = valor

    companion object {
        private val FORMATO = Regex("""^\d{5}$""")

        fun de(entrada: String): CodigoComuna {
            val limpio = entrada.trim()
            if (!FORMATO.matches(limpio)) {
                throw ComunaInvalida("el codigo de comuna son cinco digitos; llego «$entrada»")
            }
            return CodigoComuna(limpio)
        }
    }
}

class ComunaInvalida(mensaje: String) : IllegalArgumentException(mensaje)
