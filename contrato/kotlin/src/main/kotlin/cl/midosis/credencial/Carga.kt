package cl.midosis.credencial

/**
 * La carga útil del código de tratamiento, versión 1 (contrato/esquema/carga-v1.cddl).
 *
 * No hay ningún campo que identifique al paciente (ADR-002), y la lectura rechaza
 * cualquier clave que el esquema no defina, así que tampoco puede llegar uno.
 */
data class Carga(
    /** «cm»: comuna emisora, código del INE. */
    val comuna: String,
    /** «iss»: punto de dispensación que emitió el código. */
    val emisor: String,
    /** «jti»: identificador del tratamiento, un ULID. */
    val id: String,
    /** «iat»: emisión, en segundos desde 1970. */
    val emitidoEn: Long,
    /** «exp»: fin de la vigencia del canje, en segundos desde 1970. */
    val expiraEn: Long,
    /** «rx» */
    val productos: List<Producto>,
    /** «rp»: el tratamiento que este código reemplaza, solo en las correcciones (RN-25). */
    val reemplazaA: String? = null,
) {
    companion object {
        const val VERSION = 1
    }
}

/**
 * Un producto del tratamiento. Duración indicada, dosis entregadas y cobertura son
 * tres valores distintos (ADR-010).
 */
data class Producto(
    /** «c» */
    val gtin: String,
    /** «n»: etiqueta de respaldo cuando el GTIN no está en el catálogo del teléfono. */
    val etiqueta: String,
    /** «a» */
    val principioActivo: String,
    /** «d»: dosis por toma, como se indicó. */
    val dosis: String,
    /** «f»: intervalo entre tomas, duración ISO 8601 canónica como PT12H. */
    val intervalo: String,
    /** «du» */
    val duracionIndicadaDias: Int,
    /** «e»: contadas en tomas, no en unidades. */
    val dosisEntregadas: Int,
    /** «co» */
    val coberturaDias: Int,
    /** «r» */
    val via: Via,
    /** «o» */
    val observaciones: List<Observacion> = emptyList(),
    /** «nt»: nota del químico farmacéutico. */
    val nota: String? = null,
)

/** Vía de administración («r»). */
enum class Via(val codigo: String) {
    ORAL("VO"),
    SUBLINGUAL("SL"),
    TOPICA("TOP"),
    TRANSDERMICA("TD"),
    OFTALMICA("OFT"),
    OTICA("OTI"),
    NASAL("NAS"),
    INHALATORIA("INH"),
    RECTAL("REC"),
    VAGINAL("VAG"),
    SUBCUTANEA("SC"),
    INTRAMUSCULAR("IM"),
    ;

    companion object {
        fun deCodigo(codigo: String): Via? = entries.firstOrNull { it.codigo == codigo }
    }
}

/** Observación de administración («o»). */
enum class Observacion(val codigo: Int) {
    EN_AYUNAS(1),
    CON_ALIMENTOS(2),
    AL_ACOSTARSE(3),
    NO_PARTIR_NI_MASTICAR(4),
    AGITAR_ANTES_DE_USAR(5),
    ;

    companion object {
        fun deCodigo(codigo: Int): Observacion? = entries.firstOrNull { it.codigo == codigo }
    }
}
