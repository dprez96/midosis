package cl.midosis.paciente.codigo

import androidx.annotation.StringRes
import cl.midosis.credencial.Motivo
import cl.midosis.paciente.R

/**
 * Lo que ve el paciente cuando un código no se acepta. Cada caso se distingue de los
 * demás porque pide algo distinto: volver a la farmacia, pedir que lo reemitan o
 * actualizar la aplicación (HU-09, sección 7.6.1 del informe).
 */
enum class MensajeDeRechazo(@StringRes val titulo: Int, @StringRes val explicacion: Int) {
    NO_ES_DE_MIDOSIS(R.string.rechazo_no_es_de_midosis, R.string.rechazo_no_es_de_midosis_explicacion),
    ALTERADO(R.string.rechazo_alterado, R.string.rechazo_alterado_explicacion),
    EMISOR_DESCONOCIDO(R.string.rechazo_emisor_desconocido, R.string.rechazo_emisor_desconocido_explicacion),
    EXPIRADO(R.string.rechazo_expirado, R.string.rechazo_expirado_explicacion),
    ACTUALIZAR(R.string.rechazo_actualizar, R.string.rechazo_actualizar_explicacion),
    ERROR_DE_LA_FARMACIA(R.string.rechazo_error_de_la_farmacia, R.string.rechazo_error_de_la_farmacia_explicacion),
    ;

    companion object {
        fun de(motivo: Motivo): MensajeDeRechazo = when (motivo) {
            Motivo.NO_ES_DE_MIDOSIS -> NO_ES_DE_MIDOSIS
            Motivo.ALTERADO -> ALTERADO
            Motivo.EMISOR_DESCONOCIDO -> EMISOR_DESCONOCIDO
            Motivo.EXPIRADO -> EXPIRADO
            Motivo.VERSION_NO_SOPORTADA -> ACTUALIZAR
            // La firma era válida: el código salió así de la farmacia.
            Motivo.ESTRUCTURA_INVALIDA, Motivo.CONTENIDO_INCOHERENTE -> ERROR_DE_LA_FARMACIA
        }
    }
}
