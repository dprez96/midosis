package cl.midosis.core.dominio.catalogo

/**
 * Codigo de producto en formato GTIN, clave primaria con que la aplicacion
 * resuelve el producto contra su catalogo local. Admite GTIN-8, 12, 13 y 14.
 *
 * El digito verificador se comprueba aqui y no en el borde de la aplicacion,
 * porque un codigo mal leido por el lector debe detenerse antes de convertirse
 * en un tratamiento.
 */
@JvmInline
value class Gtin private constructor(val valor: String) {

    override fun toString(): String = valor

    companion object {
        private val LARGOS = setOf(8, 12, 13, 14)

        fun de(entrada: String): Gtin {
            val limpio = entrada.trim()
            if (limpio.length !in LARGOS) {
                throw GtinInvalido("un GTIN tiene 8, 12, 13 o 14 digitos; «$entrada» tiene ${limpio.length}")
            }
            if (!limpio.all { it.isDigit() }) {
                throw GtinInvalido("«$entrada» contiene caracteres que no son digitos")
            }
            if (!verificadorCorrecto(limpio)) {
                throw GtinInvalido("el digito verificador de «$entrada» no corresponde")
            }
            return Gtin(limpio)
        }

        fun esValido(entrada: String): Boolean = try {
            de(entrada); true
        } catch (e: GtinInvalido) {
            false
        }

        /**
         * Suma ponderada alternando 3 y 1 de derecha a izquierda sobre el cuerpo;
         * el verificador es lo que falta para el siguiente multiplo de diez.
         */
        private fun verificadorCorrecto(codigo: String): Boolean {
            val cuerpo = codigo.dropLast(1)
            val declarado = codigo.last().digitToInt()
            var suma = 0
            for ((i, c) in cuerpo.reversed().withIndex()) {
                suma += c.digitToInt() * if (i % 2 == 0) 3 else 1
            }
            val esperado = (10 - suma % 10) % 10
            return declarado == esperado
        }
    }
}

class GtinInvalido(mensaje: String) : IllegalArgumentException(mensaje)
