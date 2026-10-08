package cl.midosis.credencial

import com.upokecenter.cbor.CBOREncodeOptions
import com.upokecenter.cbor.CBORObject
import com.upokecenter.cbor.CBORType

/**
 * Convierte la carga útil en CBOR y de vuelta, con las reglas de
 * contrato/esquema/carga-v1.md. Los pasos de la lectura siguen el orden de ese
 * documento: el primero que falla decide el motivo y la regla del rechazo.
 */
object CodecDeCarga {

    private const val VIGENCIA_MAXIMA_S = 72L * 3600 // RN-03
    private const val INTERVALO_MINIMO_MIN = 30
    private const val INTERVALO_MAXIMO_MIN = 90 * 24 * 60
    private const val MINUTOS_POR_DIA = 24 * 60

    private val CLAVES_RAIZ = setOf("v", "cm", "iss", "jti", "iat", "exp", "rx", "rp")
    private val OBLIGATORIAS_RAIZ = CLAVES_RAIZ - "rp"
    private val CLAVES_PRODUCTO = setOf("c", "n", "a", "d", "f", "du", "e", "co", "r", "o", "nt")
    private val OBLIGATORIAS_PRODUCTO = CLAVES_PRODUCTO - setOf("o", "nt")

    private val COMUNA = Regex("^[0-9]{5}$")
    private val EMISOR = Regex("^CL-FP-[0-9]{5}-[0-9]{2}$")
    private val ULID = Regex("^[0-7][0-9A-HJKMNP-TV-Z]{25}$")
    private val GTIN = Regex("^([0-9]{8}|[0-9]{12,14})$")
    private val INTERVALO = Regex("^PT(?:([1-9][0-9]*)H(?:([1-9]|[1-5][0-9])M)?|([1-9]|[1-5][0-9])M)$")

    /** CBOR determinista (RFC 8949, sección 4.2.1): claves ordenadas y longitudes mínimas. */
    private val DETERMINISTA = CBOREncodeOptions("ctap2canonical=true")

    fun codificar(carga: Carga): ByteArray {
        val raiz = CBORObject.NewMap()
            .Add("v", Carga.VERSION)
            .Add("cm", carga.comuna)
            .Add("iss", carga.emisor)
            .Add("jti", carga.id)
            .Add("iat", carga.emitidoEn)
            .Add("exp", carga.expiraEn)
            .Add("rx", CBORObject.NewArray().apply { carga.productos.forEach { Add(producto(it)) } })
        carga.reemplazaA?.let { raiz.Add("rp", it) }
        return raiz.EncodeToBytes(DETERMINISTA)
    }

    private fun producto(p: Producto): CBORObject {
        val mapa = CBORObject.NewMap()
            .Add("c", p.gtin)
            .Add("n", p.etiqueta)
            .Add("a", p.principioActivo)
            .Add("d", p.dosis)
            .Add("f", p.intervalo)
            .Add("du", p.duracionIndicadaDias)
            .Add("e", p.dosisEntregadas)
            .Add("co", p.coberturaDias)
            .Add("r", p.via.codigo)
        if (p.observaciones.isNotEmpty()) {
            mapa.Add("o", CBORObject.NewArray().apply { p.observaciones.forEach { Add(it.codigo) } })
        }
        p.nota?.let { mapa.Add("nt", it) }
        return mapa
    }

    /** Días completos que cubren las dosis entregadas, redondeando hacia abajo (ADR-010). */
    fun coberturaDias(dosisEntregadas: Int, intervalo: String): Int =
        (dosisEntregadas.toLong() * minutos(intervalo) / MINUTOS_POR_DIA).toInt()

    /** PT1H30M son 90 minutos. Falla si el intervalo no está en forma canónica. */
    fun minutos(intervalo: String): Int {
        val partes = INTERVALO.matchEntire(intervalo) ?: throw IllegalArgumentException("intervalo no canónico «$intervalo»")
        val horas = partes.groupValues[1].toLongOrNull() ?: 0
        val mins = (partes.groupValues[2].ifEmpty { partes.groupValues[3] }).toLongOrNull() ?: 0
        val total = horas * 60 + mins
        return if (total > Int.MAX_VALUE) Int.MAX_VALUE else total.toInt()
    }

    // -----------------------------------------------------------------------
    // Lectura
    // -----------------------------------------------------------------------

    fun leer(datos: ByteArray): Carga {
        val raiz = try {
            // Por omisión rechaza las claves repetidas y lo que sobre después del
            // primer elemento.
            CBORObject.DecodeFromBytes(datos)
        } catch (e: Exception) {
            rechazar(Motivo.ESTRUCTURA_INVALIDA, "cbor-mal-formado")
        }

        // La versión se mira antes que el resto: un código de una versión futura no
        // es un código dañado, y hay que pedir que se actualice la aplicación.
        if (raiz.isTagged || raiz.type != CBORType.Map) rechazar(Motivo.ESTRUCTURA_INVALIDA, "no-es-mapa")
        val v = raiz.GetOrDefault(CBORObject.FromObject("v"), null)
        if (v == null || v.isTagged || v.type != CBORType.Integer || v.isNegative) {
            rechazar(Motivo.ESTRUCTURA_INVALIDA, "sin-version")
        }
        if (!v.CanValueFitInInt32() || v.AsInt32Value() != Carga.VERSION) {
            rechazar(Motivo.VERSION_NO_SOPORTADA, "version")
        }

        val carga = Esquema.leer(raiz) ?: rechazar(Motivo.ESTRUCTURA_INVALIDA, "esquema")
        revisarContenido(carga)
        return carga
    }

    /** Lo que exige carga-v1.cddl. Devuelve null ante la primera diferencia. */
    private object Esquema {
        fun leer(raiz: CBORObject): Carga? {
            val campos = mapaCerrado(raiz, CLAVES_RAIZ, OBLIGATORIAS_RAIZ) ?: return null
            val rx = campos.getValue("rx")
            if (rx.isTagged || rx.type != CBORType.Array || rx.size() !in 1..20) return null
            val productos = rx.values.map { producto(it) ?: return null }
            return Carga(
                comuna = texto(campos.getValue("cm"), COMUNA) ?: return null,
                emisor = texto(campos.getValue("iss"), EMISOR) ?: return null,
                id = texto(campos.getValue("jti"), ULID) ?: return null,
                emitidoEn = instante(campos.getValue("iat")) ?: return null,
                expiraEn = instante(campos.getValue("exp")) ?: return null,
                productos = productos,
                reemplazaA = campos["rp"]?.let { texto(it, ULID) ?: return null },
            )
        }

        private fun producto(objeto: CBORObject): Producto? {
            val campos = mapaCerrado(objeto, CLAVES_PRODUCTO, OBLIGATORIAS_PRODUCTO) ?: return null
            val observaciones = campos["o"]?.let { o ->
                if (o.isTagged || o.type != CBORType.Array || o.size() !in 1..5) return null
                o.values.map { Observacion.deCodigo(entero(it, 1..5) ?: return null) ?: return null }
            } ?: emptyList()
            return Producto(
                gtin = texto(campos.getValue("c"), GTIN) ?: return null,
                etiqueta = texto(campos.getValue("n"), bytes = 1..40) ?: return null,
                principioActivo = texto(campos.getValue("a"), bytes = 1..60) ?: return null,
                dosis = texto(campos.getValue("d"), bytes = 1..40) ?: return null,
                intervalo = texto(campos.getValue("f"), INTERVALO) ?: return null,
                duracionIndicadaDias = entero(campos.getValue("du"), 1..365) ?: return null,
                dosisEntregadas = entero(campos.getValue("e"), 1..9999) ?: return null,
                coberturaDias = entero(campos.getValue("co"), 0..9999) ?: return null,
                via = Via.deCodigo(texto(campos.getValue("r")) ?: return null) ?: return null,
                observaciones = observaciones,
                nota = campos["nt"]?.let { texto(it, bytes = 1..240) ?: return null },
            )
        }

        /** Un mapa sin etiquetas, de claves de texto conocidas y con todas las obligatorias. */
        private fun mapaCerrado(objeto: CBORObject, admitidas: Set<String>, obligatorias: Set<String>): Map<String, CBORObject>? {
            if (objeto.isTagged || objeto.type != CBORType.Map) return null
            val campos = HashMap<String, CBORObject>()
            for (clave in objeto.keys) {
                if (clave.isTagged || clave.type != CBORType.TextString) return null
                val nombre = clave.AsString()
                if (nombre !in admitidas) return null
                campos[nombre] = objeto[clave]
            }
            return if (campos.keys.containsAll(obligatorias)) campos else null
        }

        private fun texto(objeto: CBORObject, patron: Regex? = null, bytes: IntRange? = null): String? {
            if (objeto.isTagged || objeto.type != CBORType.TextString) return null
            val valor = objeto.AsString()
            if (patron != null && !patron.matches(valor)) return null
            if (bytes != null && valor.toByteArray(Charsets.UTF_8).size !in bytes) return null
            return valor
        }

        private fun entero(objeto: CBORObject, rango: IntRange): Int? {
            if (objeto.isTagged || objeto.type != CBORType.Integer || !objeto.CanValueFitInInt32()) return null
            return objeto.AsInt32Value().takeIf { it in rango }
        }

        private fun instante(objeto: CBORObject): Long? {
            if (objeto.isTagged || objeto.type != CBORType.Integer || objeto.isNegative) return null
            return if (objeto.CanValueFitInInt64()) objeto.AsInt64Value() else null
        }
    }

    /** Las reglas que el esquema no expresa, en el orden de carga-v1.md. */
    private fun revisarContenido(c: Carga) {
        if (c.emisor.substring(6, 11) != c.comuna) rechazar(Motivo.CONTENIDO_INCOHERENTE, "comuna-del-emisor")
        if (!(c.emitidoEn < c.expiraEn && c.expiraEn - c.emitidoEn <= VIGENCIA_MAXIMA_S)) {
            rechazar(Motivo.CONTENIDO_INCOHERENTE, "vigencia")
        }
        if (c.reemplazaA == c.id) rechazar(Motivo.CONTENIDO_INCOHERENTE, "reemplazo-distinto")
        for (p in c.productos) {
            if (!Gtin.esValido(p.gtin)) rechazar(Motivo.CONTENIDO_INCOHERENTE, "gtin-digito-verificador")
            if (minutos(p.intervalo) !in INTERVALO_MINIMO_MIN..INTERVALO_MAXIMO_MIN) {
                rechazar(Motivo.CONTENIDO_INCOHERENTE, "intervalo-en-rango")
            }
            if (p.coberturaDias != coberturaDias(p.dosisEntregadas, p.intervalo)) {
                rechazar(Motivo.CONTENIDO_INCOHERENTE, "cobertura")
            }
            val codigos = p.observaciones.map { it.codigo }
            if (codigos.zipWithNext().any { (a, b) -> a >= b }) rechazar(Motivo.CONTENIDO_INCOHERENTE, "observaciones-ordenadas")
            if (Observacion.EN_AYUNAS in p.observaciones && Observacion.CON_ALIMENTOS in p.observaciones) {
                rechazar(Motivo.CONTENIDO_INCOHERENTE, "observaciones-compatibles")
            }
            if (listOfNotNull(p.etiqueta, p.principioActivo, p.dosis, p.nota).any { tieneCaracterDeControl(it) }) {
                rechazar(Motivo.CONTENIDO_INCOHERENTE, "sin-caracteres-de-control")
            }
        }
    }

    /** Categoría Unicode Cc: C0, DEL y C1. */
    private fun tieneCaracterDeControl(texto: String): Boolean =
        texto.codePoints().anyMatch { Character.getType(it) == Character.CONTROL.toInt() }
}

/** El dígito verificador GS1: pesos 3 y 1 alternados desde la derecha. */
internal object Gtin {
    fun esValido(gtin: String): Boolean {
        val cuerpo = gtin.dropLast(1)
        val suma = cuerpo.reversed().mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 3 else 1 }.sum()
        return (10 - suma % 10) % 10 == gtin.last() - '0'
    }
}

internal fun rechazar(motivo: Motivo, regla: String): Nothing = throw CodigoRechazado(motivo, regla)
