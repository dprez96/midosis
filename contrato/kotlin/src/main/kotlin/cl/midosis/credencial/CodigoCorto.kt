package cl.midosis.credencial

import java.security.SecureRandom

/**
 * El código corto del comprobante (HU-10, tabla 88 del informe): diez caracteres de
 * Crockford Base32, nueve aleatorios (45 bits) y uno de verificación. Con el PIN, permite
 * descargar el código completo cuando el QR no se puede leer.
 *
 * El verificador es Luhn mod 32: detecta cualquier carácter mal escrito y casi todas las
 * transposiciones de dos vecinos, antes de gastar un intento en el servidor.
 */
@JvmInline
value class CodigoCorto private constructor(val valor: String) {

    /** Como se imprime y se dicta: «ABCDE-FGHJK». */
    val agrupado: String get() = valor.substring(0, 5) + "-" + valor.substring(5)

    override fun toString(): String = agrupado

    companion object {
        const val LARGO = 10
        private const val ALFABETO = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        private const val BASE = 32

        fun generar(aleatorio: SecureRandom = SecureRandom()): CodigoCorto {
            val cuerpo = String(CharArray(LARGO - 1) { ALFABETO[aleatorio.nextInt(BASE)] })
            return CodigoCorto(cuerpo + verificador(cuerpo))
        }

        /**
         * Lee lo que escribe el paciente: ignora espacios y guiones, acepta minúsculas y corrige
         * las confusiones que Crockford prevé (O por 0, I y L por 1). Null si no es un código
         * válido.
         */
        fun de(texto: String): CodigoCorto? {
            val limpio = texto.uppercase()
                .filterNot { it == '-' || it.isWhitespace() }
                .map { c -> when (c) { 'O' -> '0'; 'I', 'L' -> '1'; else -> c } }
                .joinToString("")
            if (limpio.length != LARGO || limpio.any { it !in ALFABETO }) return null
            return if (sumaLuhn(limpio, desdeElVerificador = true) % BASE == 0) CodigoCorto(limpio) else null
        }

        /** El carácter que hace válida la suma de Luhn mod 32. */
        internal fun verificador(cuerpo: String): Char =
            ALFABETO[(BASE - sumaLuhn(cuerpo, desdeElVerificador = false) % BASE) % BASE]

        private fun sumaLuhn(texto: String, desdeElVerificador: Boolean): Int {
            var factor = if (desdeElVerificador) 1 else 2
            var suma = 0
            for (c in texto.reversed()) {
                val sumando = factor * ALFABETO.indexOf(c)
                suma += sumando / BASE + sumando % BASE
                factor = if (factor == 2) 1 else 2
            }
            return suma
        }
    }
}
