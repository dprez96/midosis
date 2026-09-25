package cl.midosis.core.dominio.posologia

import java.time.Duration
import java.time.LocalDate

/**
 * La dispensacion fraccionada como dato de primera clase (ADR-010).
 *
 * Tres valores distintos que el sistema no debe confundir:
 *
 *  - «du» la duracion que indico el medico,
 *  - «e»  las dosis que la farmacia entrego en este retiro,
 *  - «co» los dias que esas dosis alcanzan a cubrir.
 *
 * El caso corriente en la farmacia popular es que los tres difieran: se indican
 * 180 dias, se entregan 60 comprimidos a uno cada doce horas, y eso cubre 30
 * dias. Registrar solo lo entregado haria que la aplicacion diera el tratamiento
 * por terminado cuando en realidad falta reponerlo.
 */
data class TramoDispensado(
    val duracionIndicadaDias: Int,
    val dosisEntregadas: Int,
    val frecuencia: Frecuencia,
) {
    init {
        require(duracionIndicadaDias > 0) {
            "la duracion indicada debe ser positiva, llego $duracionIndicadaDias"
        }
        require(dosisEntregadas > 0) {
            "las dosis entregadas deben ser positivas, llego $dosisEntregadas"
        }
    }

    /** Lo que alcanzan a cubrir las dosis entregadas. */
    val cobertura: Duration = frecuencia.lapsoDe(dosisEntregadas)

    /** La misma cobertura en dias enteros, que es como viaja en el codigo. */
    val coberturaDias: Int = (cobertura.toMinutes() / Duration.ofDays(1).toMinutes()).toInt()

    /**
     * Si lo entregado completa lo indicado. Mientras sea falso, el tratamiento
     * sigue abierto y corresponde avisar el proximo retiro (RN-07).
     */
    val completaLoIndicado: Boolean = coberturaDias >= duracionIndicadaDias

    /** Dias de tratamiento que quedan sin cubrir despues de este retiro. */
    val diasPendientes: Int = maxOf(0, duracionIndicadaDias - coberturaDias)

    /**
     * Se entrego mas de lo indicado. No es un error del sistema sino un dato que
     * conviene mostrar en el mesón: suele delatar una receta mal transcrita.
     */
    val excedeLoIndicado: Boolean = coberturaDias > duracionIndicadaDias

    /** Fecha en que se agota lo entregado, contada desde el retiro. */
    fun seAgotaEl(fechaRetiro: LocalDate): LocalDate =
        fechaRetiro.plusDays(coberturaDias.toLong())

    /**
     * Cuando avisar del proximo retiro: con la anticipacion indicada respecto del
     * agotamiento, y nunca antes del dia del retiro.
     */
    fun avisoDeProximoRetiro(
        fechaRetiro: LocalDate,
        anticipacionDias: Long = ANTICIPACION_AVISO_DIAS,
    ): LocalDate? {
        if (completaLoIndicado) return null
        val aviso = seAgotaEl(fechaRetiro).minusDays(anticipacionDias)
        return if (aviso.isBefore(fechaRetiro)) fechaRetiro else aviso
    }

    companion object {
        /**
         * Dias de anticipacion con que se avisa del proximo retiro antes de que se
         * agote lo entregado (RN-07). Valor confirmado por el quimico farmaceutico
         * asesor: deja margen para acudir a la farmacia sin interrumpir las tomas.
         * Es una regla clinica; no se cambia sin su visto bueno.
         */
        const val ANTICIPACION_AVISO_DIAS: Long = 5
    }
}
