package cl.midosis.motor.rutina

import java.time.Duration
import java.time.LocalTime

/**
 * Los horarios reales del paciente (HU-13, RF-M-05): las alarmas se ajustan a su vida y no
 * al revés. Es el dato más revelador de su vida privada, así que nunca sale del teléfono
 * (sección 7.7 del informe).
 *
 * La secuencia se lee como un día que empieza al despertar: desayuno, almuerzo y cena van
 * en ese orden, y acostarse puede caer después de la medianoche.
 */
data class Rutina(
    val despertar: LocalTime,
    val desayuno: LocalTime,
    val almuerzo: LocalTime,
    val cena: LocalTime,
    val acostarse: LocalTime,
    /**
     * Si acepta que una alarma lo despierte. Con intervalos de 8 horas o menos el
     * tratamiento las exige igual (RN-15); con 12 o más se privilegia la vigilia.
     */
    val toleraAlarmasNocturnas: Boolean = true,
) {
    init {
        problema(despertar, desayuno, almuerzo, cena, acostarse)?.let { throw RutinaInvalida(it) }
    }

    /** Las tres comidas, para las tomas «con alimentos». */
    val comidas: List<LocalTime> get() = listOf(desayuno, almuerzo, cena)

    /** Cuánto dura el día despierto, de despertar a acostarse. */
    val vigilia: Duration get() = Duration.ofMinutes(minutosDesde(despertar, acostarse).toLong())

    /** Si la hora cae mientras el paciente duerme. */
    fun duerme(hora: LocalTime): Boolean = minutosDesde(despertar, hora) >= minutosDesde(despertar, acostarse)

    companion object {
        /** Lo que se aplica si el paciente omite la entrevista (CU-P-02, flujo 1a). */
        val POR_DEFECTO = Rutina(
            despertar = LocalTime.of(7, 0),
            desayuno = LocalTime.of(8, 0),
            almuerzo = LocalTime.of(13, 0),
            cena = LocalTime.of(20, 0),
            acostarse = LocalTime.of(23, 0),
        )

        /** Al menos esto dormido: una secuencia con menos es un error al ingresar las horas. */
        private const val SUENO_MINIMO_MIN = 3 * 60

        /**
         * Qué tiene de incoherente la secuencia, en palabras para el paciente, o null si está
         * bien (CU-P-02, flujo 6a). Sirve para avisar antes de intentar crear la rutina.
         */
        fun problema(despertar: LocalTime, desayuno: LocalTime, almuerzo: LocalTime, cena: LocalTime, acostarse: LocalTime): String? {
            // Todo se mide en minutos desde que despierta: el desayuno puede ser a la misma
            // hora, y acostarse, después de la medianoche.
            val d = minutosDesde(despertar, desayuno)
            val a = minutosDesde(despertar, almuerzo)
            val c = minutosDesde(despertar, cena)
            val s = minutosDesde(despertar, acostarse)
            return when {
                s == 0 || MINUTOS_POR_DIA - s < SUENO_MINIMO_MIN -> "Entre acostarse y despertar deben quedar al menos 3 horas."
                d >= s -> "El desayuno debe ser después de despertar."
                a <= d -> "El almuerzo debe ser después del desayuno."
                c <= a -> "La cena debe ser después del almuerzo."
                s <= c -> "La hora de acostarse debe ser después de la cena."
                else -> null
            }
        }

        private const val MINUTOS_POR_DIA = 24 * 60

        /** Minutos desde [inicio] hasta [hora], avanzando en el reloj: de 23:00 a 01:00 son 120. */
        internal fun minutosDesde(inicio: LocalTime, hora: LocalTime): Int {
            val diferencia = (hora.toSecondOfDay() - inicio.toSecondOfDay()) / 60
            return Math.floorMod(diferencia, MINUTOS_POR_DIA)
        }
    }
}

class RutinaInvalida(mensaje: String) : IllegalArgumentException(mensaje)
