package cl.midosis.credencial

/**
 * Por qué se rechazó un código. Decide el mensaje al paciente, así que cada
 * motivo debe poder distinguirse de los demás (sección 7.6.1 del informe).
 *
 * Los nombres son los mismos en todos los módulos y en los vectores de
 * contrato/vectores.
 */
enum class Motivo(val codigo: String) {
    /** No es un código de MiDosis: un QR cualquiera. */
    NO_ES_DE_MIDOSIS("no-es-de-midosis"),

    /** Lo emitió una versión más nueva del sistema: hay que actualizar la aplicación. */
    VERSION_NO_SOPORTADA("version-no-soportada"),

    /** Dañado o manipulado: «Este código fue alterado». */
    ALTERADO("alterado"),

    /** La firma no es de una farmacia que la aplicación reconozca. */
    EMISOR_DESCONOCIDO("emisor-desconocido"),

    /** Pasaron las 72 horas de vigencia del canje (RN-03). */
    EXPIRADO("expirado"),

    /** La carga útil no cumple el esquema. Con la firma válida, es un error del emisor. */
    ESTRUCTURA_INVALIDA("estructura-invalida"),

    /** La carga útil cumple el esquema pero sus valores no cuadran entre sí. */
    CONTENIDO_INCOHERENTE("contenido-incoherente"),
    ;

    companion object {
        fun deCodigo(codigo: String): Motivo =
            entries.firstOrNull { it.codigo == codigo } ?: throw IllegalArgumentException("motivo desconocido «$codigo»")
    }
}

/**
 * Un código rechazado. La regla nombra el paso exacto que falló, como
 * «cobertura» o «clave-revocada»; sirve para el registro y las pruebas, no
 * para mostrarla al paciente.
 */
class CodigoRechazado(val motivo: Motivo, val regla: String) : Exception("${motivo.codigo}/$regla")
