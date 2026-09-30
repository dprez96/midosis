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
     * Una alarma cada cierto intervalo durante unas horas, para el banco de dispositivos.
     * Se queda muy por debajo del límite de 500 alarmas por aplicación de Android.
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

    const val MAXIMO_SERIE = 200
}
