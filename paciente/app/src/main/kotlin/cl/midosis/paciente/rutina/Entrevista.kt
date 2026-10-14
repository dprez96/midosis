package cl.midosis.paciente.rutina

import cl.midosis.motor.rutina.Rutina
import java.time.LocalTime

/**
 * La entrevista de rutina (HU-13, CU-P-02): cinco pasos, todos con la rutina por defecto ya
 * puesta, para que quien la acepte termine en pocos toques y muy por debajo de 45 segundos.
 *
 * Es un estado inmutable sin nada de Android, para probarlo en la computadora.
 */
data class Entrevista(
    val paso: Paso = Paso.DESPERTAR,
    val despertar: LocalTime = Rutina.POR_DEFECTO.despertar,
    val desayuno: LocalTime = Rutina.POR_DEFECTO.desayuno,
    val almuerzo: LocalTime = Rutina.POR_DEFECTO.almuerzo,
    val cena: LocalTime = Rutina.POR_DEFECTO.cena,
    val acostarse: LocalTime = Rutina.POR_DEFECTO.acostarse,
    val toleraAlarmasNocturnas: Boolean = Rutina.POR_DEFECTO.toleraAlarmasNocturnas,
) {
    enum class Paso(val numero: Int) { DESPERTAR(1), COMIDAS(2), ACOSTARSE(3), NOCHE(4), RESUMEN(5) }

    enum class Hora { DESPERTAR, DESAYUNO, ALMUERZO, CENA, ACOSTARSE }

    /** Qué está mal hasta el paso actual, en palabras para el paciente, o null. */
    val problema: String?
        get() = when (paso) {
            Paso.DESPERTAR -> null
            // Todavía no declara cuándo se acuesta: se prueba con lo más tarde posible, y lo que
            // dependa de esa hora se revisa en el paso siguiente.
            Paso.COMIDAS -> Rutina.problema(despertar, desayuno, almuerzo, cena, despertar.minusHours(3))
                .takeUnless { it?.startsWith("La hora de acostarse") == true }
            else -> Rutina.problema(despertar, desayuno, almuerzo, cena, acostarse)
        }

    fun hora(cual: Hora): LocalTime = when (cual) {
        Hora.DESPERTAR -> despertar
        Hora.DESAYUNO -> desayuno
        Hora.ALMUERZO -> almuerzo
        Hora.CENA -> cena
        Hora.ACOSTARSE -> acostarse
    }

    /** Mueve una hora en minutos, hacia adelante o hacia atrás, dando la vuelta al reloj. */
    fun mover(cual: Hora, minutos: Long): Entrevista {
        val nueva = hora(cual).plusMinutes(minutos)
        return when (cual) {
            Hora.DESPERTAR -> copy(despertar = nueva)
            Hora.DESAYUNO -> copy(desayuno = nueva)
            Hora.ALMUERZO -> copy(almuerzo = nueva)
            Hora.CENA -> copy(cena = nueva)
            Hora.ACOSTARSE -> copy(acostarse = nueva)
        }
    }

    /** Avanza si el paso está bien; si no, se queda donde está y [problema] dice por qué. */
    fun siguiente(): Entrevista =
        if (problema != null || paso == Paso.RESUMEN) this else copy(paso = Paso.entries[paso.ordinal + 1])

    fun anterior(): Entrevista = if (paso == Paso.DESPERTAR) this else copy(paso = Paso.entries[paso.ordinal - 1])

    /** La rutina declarada, si la secuencia es coherente. */
    fun rutina(): Rutina? =
        if (Rutina.problema(despertar, desayuno, almuerzo, cena, acostarse) != null) {
            null
        } else {
            Rutina(despertar, desayuno, almuerzo, cena, acostarse, toleraAlarmasNocturnas)
        }

    companion object {
        /** Para ajustar una rutina ya guardada: parte desde ella. */
        fun desde(r: Rutina) = Entrevista(
            despertar = r.despertar,
            desayuno = r.desayuno,
            almuerzo = r.almuerzo,
            cena = r.cena,
            acostarse = r.acostarse,
            toleraAlarmasNocturnas = r.toleraAlarmasNocturnas,
        )
    }
}
