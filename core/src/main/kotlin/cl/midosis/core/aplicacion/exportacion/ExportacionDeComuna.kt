package cl.midosis.core.aplicacion.exportacion

import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.core.infraestructura.persistencia.ContextoComuna
import cl.midosis.core.infraestructura.seguridad.RegistroEventosSeguridad
import cl.midosis.core.infraestructura.seguridad.TipoEventoSeguridad
import java.io.ByteArrayOutputStream
import java.sql.ResultSet
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service

/**
 * La exportación de todos los datos de la comuna en formato abierto (HU-29, RF-B-12, RN-20), para
 * que la municipalidad no quede cautiva del proveedor: un ZIP con archivos CSV (UTF-8, separados
 * por comas, RFC 4180) y su diccionario de datos.
 *
 * Todo se lee dentro del contexto de la comuna, así que la seguridad a nivel de fila deja fuera
 * lo de las demás (ADR-011). No hay datos del paciente que exportar: el sistema no los tiene
 * (ADR-002). Cada exportación queda en la auditoría.
 */
@Service
class ExportacionDeComuna(
    private val contexto: ContextoComuna,
    private val jdbc: JdbcClient,
    private val registro: RegistroEventosSeguridad,
) {
    /** Un archivo del ZIP: su consulta y la descripción de cada columna. */
    private data class Archivo(val nombre: String, val descripcion: String, val consulta: String, val columnas: List<Pair<String, String>>)

    fun exportar(comuna: CodigoComuna, autor: String, ahora: Instant = Instant.now()): ByteArray {
        val salida = ByteArrayOutputStream()
        ZipOutputStream(salida, Charsets.UTF_8).use { zip ->
            contexto.en(comuna) {
                for (a in ARCHIVOS) {
                    val filas = jdbc.sql(a.consulta).query { rs, _ -> a.columnas.map { (c, _) -> valor(rs, c) } }.list()
                    escribir(zip, a.nombre, csv(a.columnas.map { it.first }, filas))
                }
            }
            escribir(zip, "diccionario.csv", csv(listOf("archivo", "columna", "descripcion"), diccionario()))
            escribir(zip, "LEEME.txt", leeme(comuna, ahora))
        }
        registro.registrar(comuna, TipoEventoSeguridad.EXPORTACION, "exportación de los datos de la comuna por $autor")
        return salida.toByteArray()
    }

    private fun escribir(zip: ZipOutputStream, nombre: String, contenido: String) {
        zip.putNextEntry(ZipEntry(nombre))
        zip.write(contenido.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun valor(rs: ResultSet, columna: String): String =
        when (val v = rs.getObject(columna)) {
            null -> ""
            is java.sql.Timestamp -> v.toInstant().toString()
            else -> v.toString().trim()
        }

    private fun leeme(comuna: CodigoComuna, ahora: Instant) = """
        Exportación de los datos de MiDosis de la comuna ${comuna.valor}, generada el $ahora (UTC).

        Archivos CSV en UTF-8, separados por comas, con encabezado y comillas según RFC 4180.
        Las fechas van en ISO 8601 y en UTC. El significado de cada columna está en diccionario.csv.

        No hay datos de pacientes: MiDosis no los recibe ni los guarda. Los usuarios figuran por el
        identificador de su cuenta, no por su nombre ni su correo.
    """.trimIndent() + "\n"

    private fun diccionario(): List<List<String>> =
        ARCHIVOS.flatMap { a -> listOf(listOf(a.nombre, "", a.descripcion)) + a.columnas.map { (c, d) -> listOf(a.nombre, c, d) } }

    companion object {
        /** RFC 4180: comillas si el valor trae coma, comillas o salto de línea. Fin de línea CRLF. */
        fun csv(encabezado: List<String>, filas: List<List<String>>): String =
            (listOf(encabezado) + filas).joinToString("\r\n", postfix = "\r\n") { fila ->
                fila.joinToString(",") { v -> if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${v.replace("\"", "\"\"")}\"" else v }
            }

        private val ARCHIVOS = listOf(
            Archivo(
                "catalogo.csv", "Productos del catálogo de la comuna.",
                "SELECT gtin, nombre, principio_activo, forma, concentracion, control_legal FROM producto ORDER BY nombre",
                listOf(
                    "gtin" to "Código de barras GTIN del envase.",
                    "nombre" to "Nombre comercial con su concentración.",
                    "principio_activo" to "Principio activo.",
                    "forma" to "Forma farmacéutica.",
                    "concentracion" to "Concentración.",
                    "control_legal" to "DS404 (estupefaciente), DS405 (psicotrópico) o vacío.",
                ),
            ),
            Archivo(
                "plantillas.csv", "Plantillas de posología creadas por los químicos farmacéuticos.",
                "SELECT id, gtin, cantidad, unidad, frecuencia, duracion_dias, indicaciones, creada_por, creada_en FROM plantilla_posologia ORDER BY creada_en",
                listOf(
                    "id" to "Identificador de la plantilla.",
                    "gtin" to "Producto al que se aplica.",
                    "cantidad" to "Cantidad por toma.",
                    "unidad" to "Unidad de la dosis (comprimido, ml, inhalación…).",
                    "frecuencia" to "Intervalo entre tomas en ISO 8601 (PT8H = cada 8 horas).",
                    "duracion_dias" to "Duración sugerida en días, si la hay.",
                    "indicaciones" to "Indicaciones para el paciente.",
                    "creada_por" to "Identificador de la cuenta que la creó.",
                    "creada_en" to "Fecha de creación.",
                ),
            ),
            Archivo(
                "tratamientos.csv", "Una fila por código emitido (tratamiento o tramo). Sin datos del paciente.",
                """
                SELECT id, punto, emitida_en, expira_en, emitida_por, kid, consentimiento_version, consentimiento_en,
                       revocada_en, revocada_por, motivo_revocacion, validada_por, validada_en, motivos_validacion
                FROM emision ORDER BY emitida_en
                """.trimIndent(),
                listOf(
                    "id" to "Identificador del código (jti, ULID). Sus últimos 8 caracteres son la referencia del comprobante.",
                    "punto" to "Punto de dispensación que emitió.",
                    "emitida_en" to "Fecha de emisión.",
                    "expira_en" to "Fin de la vigencia del canje (72 horas).",
                    "emitida_por" to "Identificador de la cuenta que emitió.",
                    "kid" to "Clave de firma de la comuna que firmó el código.",
                    "consentimiento_version" to "Versión del texto de consentimiento leído al paciente.",
                    "consentimiento_en" to "Fecha de la aceptación del consentimiento.",
                    "revocada_en" to "Fecha de revocación, si se revocó (HU-07).",
                    "revocada_por" to "Cuenta que revocó.",
                    "motivo_revocacion" to "Motivo de la revocación.",
                    "validada_por" to "Químico farmacéutico que validó la posología, si hizo falta (HU-32).",
                    "validada_en" to "Fecha de la validación.",
                    "motivos_validacion" to "Por qué hizo falta la validación, en JSON.",
                ),
            ),
            Archivo(
                "productos_por_tratamiento.csv", "Una fila por producto de cada código emitido.",
                """
                SELECT e.id AS tratamiento, p ->> 'gtin' AS gtin, p ->> 'du' AS duracion_indicada_dias,
                       p ->> 'e' AS dosis_entregadas, p ->> 'co' AS cobertura_dias, p ->> 'u' AS unidades_entregadas
                FROM emision e, jsonb_array_elements(e.productos) p ORDER BY e.emitida_en
                """.trimIndent(),
                listOf(
                    "tratamiento" to "Identificador del código (tratamientos.csv).",
                    "gtin" to "Producto entregado (catalogo.csv).",
                    "duracion_indicada_dias" to "Duración del tratamiento que indicó el médico.",
                    "dosis_entregadas" to "Tomas que alcanza lo entregado en este retiro.",
                    "cobertura_dias" to "Días que cubre lo entregado.",
                    "unidades_entregadas" to "Unidades entregadas (comprimidos, ml…), si se registraron.",
                ),
            ),
            Archivo(
                "correcciones.csv", "Tratamientos corregidos con un código de reemplazo (HU-33).",
                "SELECT original, reemplazo, motivo, instruccion, corregida_por, corregida_en FROM correccion ORDER BY corregida_en",
                listOf(
                    "original" to "Código corregido.",
                    "reemplazo" to "Código de reemplazo.",
                    "motivo" to "Motivo de la corrección.",
                    "instruccion" to "Qué hace el paciente mientras no carga el reemplazo: continuar o suspender.",
                    "corregida_por" to "Químico farmacéutico que corrigió.",
                    "corregida_en" to "Fecha de la corrección.",
                ),
            ),
            Archivo(
                "auditoria.csv", "Eventos de seguridad de la comuna.",
                "SELECT instante, tipo, detalle FROM evento_seguridad ORDER BY instante",
                listOf(
                    "instante" to "Fecha del evento.",
                    "tipo" to "COMUNA_MANIPULADA (petición con otra comuna, rechazada) o EXPORTACION.",
                    "detalle" to "Descripción del evento.",
                ),
            ),
            Archivo(
                "metricas_mensuales.csv", "Métricas agregadas por mes de emisión.",
                """
                SELECT to_char(date_trunc('month', e.emitida_en AT TIME ZONE 'America/Santiago'), 'YYYY-MM') AS mes,
                       count(*) AS codigos_emitidos,
                       count(*) FILTER (WHERE e.revocada_en IS NOT NULL) AS codigos_revocados,
                       count(*) FILTER (WHERE EXISTS (SELECT 1 FROM correccion c WHERE c.original = e.id)) AS codigos_corregidos,
                       count(*) FILTER (WHERE e.validada_por IS NOT NULL) AS codigos_validados,
                       sum(jsonb_array_length(e.productos)) AS productos_emitidos
                FROM emision e GROUP BY 1 ORDER BY 1
                """.trimIndent(),
                listOf(
                    "mes" to "Mes de emisión, hora de Chile continental (AAAA-MM).",
                    "codigos_emitidos" to "Códigos emitidos en el mes.",
                    "codigos_revocados" to "De ellos, revocados.",
                    "codigos_corregidos" to "De ellos, corregidos con un reemplazo.",
                    "codigos_validados" to "De ellos, con validación del químico farmacéutico.",
                    "productos_emitidos" to "Productos incluidos en esos códigos.",
                ),
            ),
        )
    }
}
