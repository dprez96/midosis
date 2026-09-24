package cl.midosis.core.dominio.posologia

import java.time.Duration

/**
 * Intervalo entre tomas. Corresponde a la clave «f» de la carga util del codigo
 * de tratamiento, expresada como duracion ISO 8601: PT8H son ocho horas, P1D un
 * dia. El formato se fija en el contrato y no admite semanas ni meses, porque su
 * duracion no es constante y el calculo de cobertura dejaria de ser exacto.
 */
@JvmInline
value class Frecuencia private constructor(val intervalo: Duration) {

    /** Cuantas tomas cubre el intervalo en el lapso indicado. */
    fun dosisEn(lapso: Duration): Int =
        (lapso.toMinutes() / intervalo.toMinutes()).toInt()

    /** Cuanto tiempo cubren las dosis indicadas. */
    fun lapsoDe(dosis: Int): Duration = intervalo.multipliedBy(dosis.toLong())

    override fun toString(): String = intervalo.toString()

    companion object {
        /** Ninguna posologia legitima repite antes de esto. */
        private val MINIMO: Duration = Duration.ofMinutes(30)

        /** Mas alla de esto no se trata de una frecuencia sino de un evento aislado. */
        private val MAXIMO: Duration = Duration.ofDays(90)

        fun de(iso: String): Frecuencia {
            val d = try {
                Duration.parse(iso)
            } catch (e: Exception) {
                throw FrecuenciaInvalida("«$iso» no es una duracion ISO 8601 valida")
            }
            if (d < MINIMO) {
                throw FrecuenciaInvalida("el intervalo $iso es menor que el minimo admitido de $MINIMO")
            }
            if (d > MAXIMO) {
                throw FrecuenciaInvalida("el intervalo $iso supera el maximo admitido de $MAXIMO")
            }
            return Frecuencia(d)
        }

        fun cadaHoras(horas: Long): Frecuencia = de(Duration.ofHours(horas).toString())
    }
}

class FrecuenciaInvalida(mensaje: String) : IllegalArgumentException(mensaje)
