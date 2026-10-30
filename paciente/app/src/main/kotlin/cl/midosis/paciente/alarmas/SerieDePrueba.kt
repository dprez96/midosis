package cl.midosis.paciente.alarmas

import cl.midosis.motor.planificacion.TomaProgramada
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Tomas sintéticas para probar las alarmas en un teléfono real, mientras no exista la
 * carga de tratamientos desde el código (HU-08). Nunca contienen datos de pacientes.
 */
object SerieDePrueba {

    /**
     * Una sola alarma, dentro de los minutos indicados, con dos medicamentos a la misma
     * hora: sirve para ver de una vez que suena y que agrupa (HU-18).
     */
    fun alarmaUnica(ahora: LocalDateTime, enMinutos: Long = 1): List<TomaProgramada> {
        val momento = ahora.truncatedTo(ChronoUnit.MINUTES).plusMinutes(enMinutos)
        return listOf(
            TomaProgramada("prueba-1", "Medicamento de prueba A", "1 comprimido", momento),
            TomaProgramada("prueba-2", "Medicamento de prueba B", "2 comprimidos", momento),
        )
    }

    /**
     * Una alarma cada cierto intervalo, para el banco de dispositivos (#43). La de 30 días
     * tiene 1440 alarmas, más que las 500 que Android admite por aplicación: se guardan todas
     * en el plan y el programador entrega solo las de los próximos 7 días, que avanzan cada vez
     * que suena una (HU-08). Así la serie corre sola el mes entero, sin reiniciarla a mano.
     */
    fun serie(ahora: LocalDateTime, cada: Duration, durante: Duration): List<TomaProgramada> {
        require(!cada.isNegative && !cada.isZero) { "el intervalo debe ser positivo" }
        val inicio = ahora.truncatedTo(ChronoUnit.MINUTES).plus(cada)
        val cantidad = (durante.toMinutes() / cada.toMinutes()).toInt()
        require(cantidad in 1..MAXIMO_SERIE) { "la serie debe tener entre 1 y $MAXIMO_SERIE alarmas" }
        return (0 until cantidad).map { i ->
            TomaProgramada("banco", "Alarma de banco", "sin dosis", inicio.plus(cada.multipliedBy(i.toLong())))
        }
    }

    /** Treinta días, cada 30 minutos. */
    const val MAXIMO_SERIE = 30 * 48
}
