package cl.midosis.credencial

/**
 * Base45 (RFC 9285): el alfabeto del modo alfanumérico del QR, que empaqueta
 * dos caracteres en once bits.
 */
object Base45 {
    private const val ALFABETO = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"
    private val VALOR = IntArray(128) { -1 }.also { tabla -> ALFABETO.forEachIndexed { i, c -> tabla[c.code] = i } }

    fun codificar(datos: ByteArray): String {
        val salida = StringBuilder((datos.size + 1) / 2 * 3)
        var i = 0
        while (i + 1 < datos.size) {
            var n = (datos[i].toInt() and 0xFF) * 256 + (datos[i + 1].toInt() and 0xFF)
            repeat(3) {
                salida.append(ALFABETO[n % 45])
                n /= 45
            }
            i += 2
        }
        if (i < datos.size) {
            val n = datos[i].toInt() and 0xFF
            salida.append(ALFABETO[n % 45]).append(ALFABETO[n / 45])
        }
        return salida.toString()
    }

    /**
     * Estricta: rechaza caracteres fuera del alfabeto, largos imposibles y grupos
     * cuyo valor no cabe en los bytes que representan.
     */
    fun decodificar(texto: String): ByteArray {
        require(texto.length % 3 != 1) { "largo imposible para Base45: ${texto.length}" }
        val salida = ByteArray(texto.length / 3 * 2 + (if (texto.length % 3 == 2) 1 else 0))
        var j = 0
        var i = 0
        while (i < texto.length) {
            val grupo = minOf(3, texto.length - i)
            var n = 0
            var peso = 1
            for (k in 0 until grupo) {
                n += valor(texto[i + k]) * peso
                peso *= 45
            }
            if (grupo == 3) {
                require(n <= 0xFFFF) { "grupo fuera de rango en la posición $i" }
                salida[j++] = (n shr 8).toByte()
                salida[j++] = n.toByte()
            } else {
                require(n <= 0xFF) { "par fuera de rango en la posición $i" }
                salida[j++] = n.toByte()
            }
            i += grupo
        }
        return salida
    }

    private fun valor(c: Char): Int {
        val v = if (c.code < 128) VALOR[c.code] else -1
        require(v >= 0) { "carácter fuera del alfabeto Base45" }
        return v
    }
}
