package cl.midosis.paciente.tratamiento

import cl.midosis.motor.posologia.Frecuencia
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.json.JSONObject

/**
 * Lo que core sabe de un código cargado aquí (RF-B-15, RF-M-30): sigue vigente, la farmacia lo
 * corrigió (HU-33) o lo revocó. Core no sabe nada del paciente: solo recibe los jti.
 */
sealed interface EstadoDeCodigo {
    data object Vigente : EstadoDeCodigo

    /**
     * El químico farmacéutico emitió un código de reemplazo y dijo qué hacer mientras tanto. Con
     * [suspender], [alarmasDesactivadas] dice si el paciente ya desactivó sus alarmas: la
     * aplicación no lo hace sola, porque la respuesta de core no va firmada y una respuesta falsa
     * no debe poder apagar un tratamiento.
     */
    data class Corregido(val suspender: Boolean, val alarmasDesactivadas: Boolean = false) : EstadoDeCodigo

    data object Revocado : EstadoDeCodigo

    /** Core no lo conoce, por ejemplo un código de prueba. No se muestra nada. */
    data object Desconocido : EstadoDeCodigo
}

/**
 * La corrección de un tratamiento ya cargado (HU-33, RN-25), del lado del teléfono: la
 * referencia que el paciente le dicta a la farmacia, la respuesta de la consulta de estado y
 * qué alarmas son las de un tratamiento.
 */
object Correcciones {

    /** Core acepta hasta 20 jti por consulta. */
    const val MAXIMO_POR_CONSULTA = 20

    /**
     * La referencia del tratamiento: los últimos 8 caracteres del jti, en dos grupos de 4. Es la
     * misma que va impresa en el comprobante, y con ella la farmacia encuentra el código aunque
     * ya haya vencido.
     */
    fun referencia(id: String): String = id.takeLast(8).let { "${it.take(4)}-${it.drop(4)}" }

    /** El cuerpo de POST /api/estado. */
    fun consulta(ids: List<String>): String = JSONObject().put("ids", ids.take(MAXIMO_POR_CONSULTA)).toString()

    /** La respuesta de POST /api/estado, o un mapa vacío si no se entiende. */
    fun interpretar(cuerpo: String): Map<String, EstadoDeCodigo> {
        val codigos = runCatching { JSONObject(cuerpo).getJSONArray("codigos") }.getOrNull() ?: return emptyMap()
        return (0 until codigos.length()).mapNotNull { i ->
            val c = codigos.optJSONObject(i) ?: return@mapNotNull null
            val id = c.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            id to when (c.optString("estado")) {
                "vigente" -> EstadoDeCodigo.Vigente
                "corregido" -> EstadoDeCodigo.Corregido(suspender = c.optString("instruccion") == "suspender")
                "revocado" -> EstadoDeCodigo.Revocado
                else -> EstadoDeCodigo.Desconocido
            }
        }.toMap()
    }

    /**
     * Si una alarma del plan es de [tratamiento]: el plan guarda cada toma como «producto · dosis»
     * y no el tratamiento al que pertenece, así que se reconoce por ese texto y por caer dentro
     * de lo que cubre lo entregado (con un día de margen, por si el paciente corrió las horas). El
     * límite evita quitar las tomas de un tramo posterior del mismo producto (HU-21).
     */
    fun esDe(tratamiento: TratamientoGuardado, zona: ZoneId = ZoneId.systemDefault()): (String, LocalDateTime) -> Boolean {
        val cargado = Instant.ofEpochSecond(tratamiento.cargadoEn).atZone(zona).toLocalDateTime()
        val limites = tratamiento.carga.productos.associate { p ->
            val intervalo = Frecuencia.de(p.intervalo).intervalo
            linea(p.etiqueta, p.dosis) to cargado.plus(intervalo.multipliedBy(p.dosisEntregadas.toLong())).plusDays(1)
        }
        return { texto, momento -> limites[texto]?.let { momento.isBefore(it) } ?: false }
    }

    /** Como arma PlanDeAlarmas cada línea de una alarma. */
    fun linea(producto: String, dosis: String): String = "$producto · $dosis"
}
