package cl.midosis.paciente.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cl.midosis.credencial.Carga
import cl.midosis.credencial.Lectura
import cl.midosis.motor.rutina.Rutina
import cl.midosis.paciente.BuildConfig
import cl.midosis.paciente.aviso.AlmacenDeAviso
import cl.midosis.paciente.codigo.ClavesEmpaquetadas
import cl.midosis.paciente.codigo.CodigosDeEjemplo
import cl.midosis.paciente.codigo.MensajeDeRechazo
import cl.midosis.paciente.codigo.ClienteDeEstado
import cl.midosis.paciente.rutina.AlmacenDeRutina
import cl.midosis.paciente.rutina.Entrevista
import cl.midosis.paciente.rutina.RutinaGuardada
import cl.midosis.paciente.tratamiento.AlmacenDeTratamientos
import cl.midosis.paciente.tratamiento.TratamientoGuardado
import cl.midosis.paciente.tratamiento.Resumen
import cl.midosis.paciente.alarmas.AvisosDeRetiroEnElTelefono
import cl.midosis.paciente.alarmas.Diagnostico
import cl.midosis.paciente.alarmas.PlanDeAlarmas
import cl.midosis.paciente.alarmas.ProgramadorDeAlarmas
import cl.midosis.paciente.alarmas.AlmacenDelPlan
import cl.midosis.paciente.tratamiento.Planificacion
import cl.midosis.paciente.privacidad.Supresion
import cl.midosis.paciente.tratamiento.RevisionDelPlan
import cl.midosis.paciente.tratamiento.AlmacenDeEstados
import cl.midosis.paciente.tratamiento.Correcciones
import cl.midosis.paciente.tratamiento.EstadoDeCodigo
import cl.midosis.paciente.tratamiento.Continuidad
import cl.midosis.paciente.tratamiento.Continuacion
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Dónde está el paciente dentro de la aplicación. */
private sealed interface Pantalla {
    data object Inicio : Pantalla
    data object Escanear : Pantalla
    data object CodigoCorto : Pantalla
    data class Confirmar(val lectura: Lectura.Aceptado) : Pantalla
    data class Rechazo(val mensaje: MensajeDeRechazo) : Pantalla
    data object YaCargado : Pantalla
    data class Entrevistar(val entrevista: Entrevista) : Pantalla
    data object Pruebas : Pantalla
    data object Permisos : Pantalla
    data object Privacidad : Pantalla

    /** El aviso de uso y responsabilidad: [primeraVez] solo se cierra con «Entiendo, continuar». */
    data class Aviso(val primeraVez: Boolean) : Pantalla
    data object Voz : Pantalla
    data object Borrado : Pantalla
    data class Revisar(val tratamiento: TratamientoGuardado, val revision: RevisionDelPlan) : Pantalla
}

/**
 * La aplicación del paciente. Se usa sin cuenta (HU-11, RF-M-01): nunca pide nombre, RUT ni
 * correo. Del código se pasa a la confirmación, de ahí a la entrevista de rutina si es el primer
 * tratamiento (HU-13), y a la pantalla principal.
 */
@Composable
fun AppPaciente() {
    val contexto = LocalContext.current
    val tratamientos = remember { AlmacenDeTratamientos(contexto) }
    val rutinas = remember { AlmacenDeRutina(contexto) }
    val lector = remember { ClavesEmpaquetadas.lector(contexto) }

    var lista by remember { mutableStateOf(tratamientos.leer()) }
    var rutina by remember { mutableStateOf(rutinas.leer()) }
    val avisos = remember { AlmacenDeAviso(contexto) }
    var pantalla by remember {
        mutableStateOf<Pantalla>(if (avisos.debeMostrarse()) Pantalla.Aviso(primeraVez = true) else Pantalla.Inicio)
    }
    var porProgramar by remember { mutableStateOf<TratamientoGuardado?>(null) }
    val programador = remember { ProgramadorDeAlarmas(contexto) }
    val estadosGuardados = remember { AlmacenDeEstados(contexto) }
    var estados by remember { mutableStateOf(estadosGuardados.leer()) }

    // Al abrir la aplicación y con cada tratamiento nuevo, pregunta a core si la farmacia corrigió
    // o revocó alguno (HU-33). Sin conexión no pasa nada: queda lo último que se supo.
    LaunchedEffect(lista) {
        val ids = lista.map { it.carga.id }
        val consultados = withContext(Dispatchers.IO) { ClienteDeEstado(BuildConfig.URL_CORE).consultar(ids) } ?: return@LaunchedEffect
        withContext(Dispatchers.IO) { estadosGuardados.actualizar(consultados) }
        estados = estadosGuardados.leer()
    }


    fun leerCodigo(texto: String) {
        pantalla = when (val lectura = lector.leer(texto.trim())) {
            is Lectura.Aceptado -> Pantalla.Confirmar(lectura)
            is Lectura.Rechazado -> Pantalla.Rechazo(MensajeDeRechazo.de(lectura.motivo))
        }
    }

    /** Propone el calendario y lo muestra para que el paciente lo revise antes de activarlo (HU-14). */
    fun revisarPlan(t: TratamientoGuardado, conRutina: RutinaGuardada) {
        val ahora = LocalDateTime.now()
        // Un tramo siguiente con la misma posología sigue la cadencia del anterior (HU-21).
        val plan = AlmacenDelPlan(contexto).leer()
        val continuaDesde = Continuidad.detectar(t.carga, lista).mapNotNull { c ->
            val linea = Correcciones.linea(c.productoAnterior.etiqueta, c.productoAnterior.dosis)
            val esDe = Correcciones.esDe(c.anterior)
            val ultima = plan.filter { s -> s.lineas.any { it == linea && esDe(it, s.momento) } }.maxOfOrNull { it.momento }
            Continuidad.inicioDeTomas(c, ultima, ahora)?.let { c.producto.gtin to it }
        }.toMap()
        pantalla = Pantalla.Revisar(t, RevisionDelPlan(Planificacion.proponer(t, conRutina.rutina, ahora, continuaDesde)))
    }

    /**
     * Guarda el tratamiento con el plan ya revisado y programa las alarmas del tramo cubierto
     * (HU-08). Se guarda recién aquí: si la aplicación se cierra durante la revisión no queda un
     * tratamiento sin alarmas, y basta con volver a escanear el código. Si no quedó ninguna
     * alarma, faltan permisos: se dice, porque el sistema nunca debe aparentar estar activo sin
     * estarlo (CU-P-03).
     */
    fun programarAlarmas(t: TratamientoGuardado, revision: RevisionDelPlan) {
        val reemplazado = lista.firstOrNull { it.carga.id == t.carga.reemplazaA }
        if (tratamientos.guardar(t) == AlmacenDeTratamientos.Resultado.YaCargado) {
            pantalla = Pantalla.YaCargado
            return
        }
        // Un código de reemplazo (HU-33) sustituye al tratamiento que corrige: se quitan sus
        // alarmas antes de programar las nuevas, que pueden tener el mismo texto.
        if (reemplazado != null) {
            programador.quitarTomas(Correcciones.esDe(reemplazado))
            tratamientos.quitar(reemplazado.carga.id)
            estadosGuardados.quitar(reemplazado.carga.id)
            estados = estadosGuardados.leer()
        }
        lista = tratamientos.leer()
        val ahora = LocalDateTime.now()
        // Con otra posología, la nueva rige desde ahora: se quitan las tomas pendientes del tramo
        // anterior de ese producto (HU-21). El historial de respuestas no se toca.
        Continuidad.detectar(t.carga, lista.filterNot { it.carga.id == t.carga.id }).filterNot { it.mismaPosologia }.forEach { c ->
            val linea = Correcciones.linea(c.productoAnterior.etiqueta, c.productoAnterior.dosis)
            val esDe = Correcciones.esDe(c.anterior)
            programador.quitarTomas({ texto, momento -> texto == linea && esDe(texto, momento) }, ahora)
        }
        val tomas = Planificacion.tomasDe(t, revision.planes)
        val programadas = programador.programar(PlanDeAlarmas.construir(tomas, ahora), ahora)
        AvisosDeRetiroEnElTelefono.programar(contexto, ahora)
        // También si quedaron programadas pero el ahorro de batería puede retrasarlas (HU-24).
        pantalla = if (programadas == 0 || !Diagnostico.del(contexto).todoEnOrden) Pantalla.Permisos else Pantalla.Inicio
    }

    fun guardarRutina(guardada: RutinaGuardada) {
        rutinas.guardar(guardada)
        rutina = guardada
        // La hora de acostarse cambia la del aviso de alarmas nocturnas.
        programador.reprogramarGuardadas()
        val pendiente = porProgramar
        porProgramar = null
        if (pendiente != null) revisarPlan(pendiente, guardada) else pantalla = Pantalla.Inicio
    }

    // En el aviso de la primera vez, «Atrás» cierra la aplicación: no se entra sin leerlo.
    BackHandler(enabled = pantalla != Pantalla.Inicio && pantalla != Pantalla.Aviso(primeraVez = true)) {
        // Salir de la revisión no deja el tratamiento sin alarmas: se activan las propuestas.
        when (val p = pantalla) {
            is Pantalla.Revisar -> programarAlarmas(p.tratamiento, p.revision)
            else -> pantalla = Pantalla.Inicio
        }
    }

    when (val p = pantalla) {
        Pantalla.Inicio -> Inicio(
            tratamientos = lista,
            estados = estados,
            alDesactivarAlarmas = { t ->
                // Lo confirma el paciente: la respuesta de core no va firmada (ver EstadoDeCodigo).
                programador.quitarTomas(Correcciones.esDe(t))
                estadosGuardados.marcarAlarmasDesactivadas(t.carga.id)
                estados = estadosGuardados.leer()
            },
            rutina = rutina,
            alCargar = { pantalla = Pantalla.Escanear },
            alAjustarRutina = { pantalla = Pantalla.Entrevistar(rutina?.let { Entrevista.desde(it.rutina) } ?: Entrevista()) },
            alAbrirPruebas = { pantalla = Pantalla.Pruebas },
            alRevisarPermisos = { pantalla = Pantalla.Permisos },
            alAbrirPrivacidad = { pantalla = Pantalla.Privacidad },
            alAbrirVoz = { pantalla = Pantalla.Voz },
            alAbrirAviso = { pantalla = Pantalla.Aviso(primeraVez = false) },
        )
        is Pantalla.Aviso -> PantallaDeAviso(
            alEntender = if (p.primeraVez) ({ avisos.registrarLectura(); pantalla = Pantalla.Inicio }) else null,
            alVolver = if (p.primeraVez) null else ({ pantalla = Pantalla.Inicio }),
        )
        Pantalla.Privacidad -> Privacidad(
            alBorrar = {
                Supresion.ejecutar(contexto)
                lista = emptyList()
                rutina = null
                porProgramar = null
                pantalla = Pantalla.Borrado
            },
            alVolver = { pantalla = Pantalla.Inicio },
        )
        Pantalla.Voz -> LecturaEnVozAlta(alVolver = { pantalla = Pantalla.Inicio })
        Pantalla.Borrado -> Aviso(
            titulo = "Se borraron todos tus datos",
            explicacion = "Ya no queda en este teléfono ningún tratamiento, horario ni alarma de MiDosis.",
            alReintentar = null,
            alVolver = { pantalla = Pantalla.Inicio },
        )
        Pantalla.Escanear -> Marco("Carga tu tratamiento") {
            Text("Apunta la cámara al código impreso en el comprobante de la farmacia.")
            EscanerDeCodigo(alLeer = ::leerCodigo)
            if (BuildConfig.DEBUG) CodigoDePrueba(alGenerar = ::leerCodigo)
            TextButton(onClick = { pantalla = Pantalla.CodigoCorto }) { Text("¿No se puede leer? Escribe el código y el PIN") }
            OutlinedButton(onClick = { pantalla = Pantalla.Inicio }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
        }
        Pantalla.CodigoCorto -> Marco("Escribe el código") {
            IngresoDeCodigoCorto(alObtener = ::leerCodigo, alCancelar = { pantalla = Pantalla.Escanear })
        }
        is Pantalla.Confirmar -> Confirmacion(
            carga = p.lectura.carga,
            reemplaza = lista.firstOrNull { it.carga.id == p.lectura.carga.reemplazaA },
            continuaciones = Continuidad.detectar(p.lectura.carga, lista),
            alGuardar = {
                val nuevo = TratamientoGuardado(p.lectura.carga, Instant.now().epochSecond, p.lectura.kid)
                val conRutina = rutina
                when {
                    tratamientos.contiene(nuevo.carga.id) -> pantalla = Pantalla.YaCargado
                    conRutina == null -> {
                        // Con el primer tratamiento se pregunta la rutina (CU-P-02); el plan se
                        // revisa y se guarda al terminar la entrevista.
                        porProgramar = nuevo
                        pantalla = Pantalla.Entrevistar(Entrevista())
                    }
                    else -> revisarPlan(nuevo, conRutina)
                }
            },
            alCancelar = { pantalla = Pantalla.Inicio },
        )
        is Pantalla.Rechazo -> Aviso(
            titulo = stringResource(p.mensaje.titulo),
            explicacion = stringResource(p.mensaje.explicacion),
            alReintentar = { pantalla = Pantalla.Escanear },
            alVolver = { pantalla = Pantalla.Inicio },
        )
        Pantalla.YaCargado -> Aviso(
            titulo = "Este tratamiento ya está en tu teléfono",
            explicacion = "No se cargó de nuevo. Lo encuentras en la pantalla principal.",
            alReintentar = null,
            alVolver = { pantalla = Pantalla.Inicio },
        )
        is Pantalla.Entrevistar -> EntrevistaDeRutina(
            inicial = p.entrevista,
            alTerminar = { guardarRutina(RutinaGuardada(it, porDefecto = false)) },
            alOmitir = { guardarRutina(RutinaGuardada(Rutina.POR_DEFECTO, porDefecto = true)) },
        )
        is Pantalla.Revisar -> RevisarPlan(
            inicial = p.revision,
            rutina = rutina?.rutina ?: Rutina.POR_DEFECTO,
            alConfirmar = { programarAlarmas(p.tratamiento, it) },
            alUsarPropuesto = { programarAlarmas(p.tratamiento, p.revision) },
        )
        Pantalla.Permisos -> PermisosDeAlarmas(
            alListo = { programador.reprogramarGuardadas(); pantalla = Pantalla.Inicio },
        )
        Pantalla.Pruebas -> Column {
            TextButton(onClick = { pantalla = Pantalla.Inicio }, modifier = Modifier.safeDrawingPadding()) { Text("← Volver") }
            PruebaDeAlarmas(rutina) { pantalla = Pantalla.Entrevistar(rutina?.let { Entrevista.desde(it.rutina) } ?: Entrevista()) }
        }
    }
}

@Composable
private fun Inicio(
    tratamientos: List<TratamientoGuardado>,
    estados: Map<String, EstadoDeCodigo>,
    alDesactivarAlarmas: (TratamientoGuardado) -> Unit,
    rutina: RutinaGuardada?,
    alCargar: () -> Unit,
    alAjustarRutina: () -> Unit,
    alAbrirPruebas: () -> Unit,
    alRevisarPermisos: () -> Unit,
    alAbrirPrivacidad: () -> Unit,
    alAbrirVoz: () -> Unit,
    alAbrirAviso: () -> Unit,
) {
    Marco("MiDosis") {
        if (tratamientos.isEmpty()) {
            Text("Te recordamos tus medicamentos a la hora que corresponde, aunque no tengas internet.")
            Text("No necesitas crear una cuenta ni dar tu nombre: todo queda en tu teléfono.")
        } else {
            val hoy = LocalDate.now()
            // De cada medicamento, solo el último tramo cargado; los anteriores quedan guardados
            // con su historial (HU-21).
            Continuidad.vigentes(tratamientos).groupBy({ it.first }, { it.second }).forEach { (t, productos) ->
                productos.forEach { p ->
                    TarjetaDeMedicamento(
                        Resumen.de(p, t.carga.emitidoEn, hoy, inicio = Continuidad.inicioDeCobertura(t, p, tratamientos)),
                        tramo = Continuidad.numeroDeTramo(t, p, tratamientos),
                    )
                }
                EstadoDelTratamiento(t, estados[t.carga.id], alDesactivar = { alDesactivarAlarmas(t) })
            }
            ProximaToma(alRevisar = alRevisarPermisos)
            Text(LEYENDA, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = alCargar, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(if (tratamientos.isEmpty()) "Cargar mi tratamiento" else "Cargar otro tratamiento")
        }
        if (rutina != null) {
            TextButton(onClick = alAjustarRutina) { Text("Ajustar mis horarios") }
            if (rutina.porDefecto) Text("Usamos horarios habituales. Ajústalos para que las alarmas calcen con tu día.")
        }
        TextButton(onClick = alAbrirVoz) { Text("Lectura en voz alta") }
        TextButton(onClick = alAbrirPrivacidad) { Text("Privacidad y mis datos") }
        TextButton(onClick = alAbrirAviso) { Text("Uso, cuándo consultar y responsabilidad") }
        if (BuildConfig.DEBUG) TextButton(onClick = alAbrirPruebas) { Text("Herramientas de prueba") }
    }
}

/**
 * Las dos cifras de la HU-19 a la vista, sin navegar: cuánto dura el tratamiento indicado y
 * hasta cuándo alcanza lo retirado. Nunca dice «terminado» mientras falte retirar (RN-07).
 */
@Composable
private fun TarjetaDeMedicamento(r: Resumen, tramo: Int = 1) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(r.etiqueta, style = MaterialTheme.typography.titleLarge)
            if (tramo > 1) Text("Retiro número $tramo de este tratamiento", style = MaterialTheme.typography.bodyMedium)
            Text("Tratamiento indicado: ${dias(r.indicadoDias.toLong())}", style = MaterialTheme.typography.titleMedium)
            Text(
                "Lo que retiraste alcanza hasta el ${r.alcanzaHasta.format(FECHA)} (${dias(r.cubiertoDias.toLong())})",
                style = MaterialTheme.typography.titleMedium,
            )
            val estado = when (r.estado) {
                Resumen.Estado.COMPLETO -> "Con lo que retiraste completas el tratamiento indicado."
                Resumen.Estado.AL_DIA -> "Te quedan ${dias(r.quedanDias)} de medicamento."
                Resumen.Estado.VOLVER_PRONTO ->
                    "Te quedan ${dias(r.quedanDias)}: vuelve a la farmacia a retirar lo que sigue."
                Resumen.Estado.SE_ACABO ->
                    "Se te acabó el ${r.alcanzaHasta.format(FECHA)}. Tu tratamiento sigue: retira lo que falta en la farmacia."
            }
            Text(
                estado,
                color = if (r.estado == Resumen.Estado.VOLVER_PRONTO || r.estado == Resumen.Estado.SE_ACABO) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

/**
 * La referencia del tratamiento, que el paciente le dicta a la farmacia para corregirlo, y lo
 * que la farmacia haya dicho de él (HU-33).
 */
@Composable
private fun EstadoDelTratamiento(t: TratamientoGuardado, estado: EstadoDeCodigo?, alDesactivar: () -> Unit) {
    val aviso = when (estado) {
        is EstadoDeCodigo.Corregido -> "La farmacia corrigió este tratamiento." to when {
            estado.suspender && estado.alarmasDesactivadas ->
                "Deja de tomarlo desde ya: sus alarmas están desactivadas. Pide en tu farmacia el código nuevo y cárgalo."
            estado.suspender -> "Deja de tomarlo desde ya. Pide en tu farmacia el código nuevo y cárgalo."
            else ->
                "Sigue tomándolo como hasta ahora hasta que cargues el código nuevo. Pídelo en tu farmacia."
        }
        EstadoDeCodigo.Revocado -> "La farmacia anuló este código." to
            "Consulta en tu farmacia antes de seguir con este tratamiento."
        else -> null
    }
    if (aviso != null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(aviso.first, style = MaterialTheme.typography.titleMedium)
                Text(aviso.second)
                if (estado is EstadoDeCodigo.Corregido && estado.suspender && !estado.alarmasDesactivadas) {
                    Button(onClick = alDesactivar, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Desactivar sus alarmas")
                    }
                }
            }
        }
    }
    Text(
        "Referencia: ${Correcciones.referencia(t.carga.id)}",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.semantics { contentDescription = "Referencia del tratamiento: ${Correcciones.referencia(t.carga.id).toList().joinToString(" ")}" },
    )
}

private val FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy")

private fun dias(n: Long): String = if (n == 1L) "1 día" else "$n días"

/**
 * El código continúa un tratamiento que ya está en el teléfono (HU-21). Con la misma posología
 * las tomas siguen donde quedaron; con otra, se muestran ambas y la nueva rige desde hoy.
 */
@Composable
private fun AvisoDeContinuacion(c: Continuacion) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Continúa tu tratamiento de ${c.producto.etiqueta}", style = MaterialTheme.typography.titleMedium)
            if (c.mismaPosologia) {
                Text("Las tomas siguen donde quedaron, sin repetir ni saltar ninguna. Tu historial se conserva.")
            } else {
                Text("La farmacia cambió cómo tomarlo. Desde hoy rige lo nuevo:")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Antes", style = MaterialTheme.typography.labelLarge)
                        Text("${c.productoAnterior.dosis}, ${describirIntervalo(c.productoAnterior.intervalo)}")
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Desde hoy", style = MaterialTheme.typography.labelLarge)
                        Text("${c.producto.dosis}, ${describirIntervalo(c.producto.intervalo)}", style = MaterialTheme.typography.titleSmall)
                    }
                }
                Text("Las tomas pendientes del retiro anterior se quitan. Tu historial se conserva.")
            }
        }
    }
}

/** Lo que trae el código, para que el paciente lo revise antes de guardarlo. */
@Composable
private fun Confirmacion(
    carga: Carga,
    reemplaza: TratamientoGuardado?,
    continuaciones: List<Continuacion>,
    alGuardar: () -> Unit,
    alCancelar: () -> Unit,
) {
    Marco("Revisa tu tratamiento") {
        Text("Entregado por la farmacia ${carga.emisor}.")
        continuaciones.forEach { c -> AvisoDeContinuacion(c) }
        if (reemplaza != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Este código corrige un tratamiento tuyo", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Reemplaza a ${reemplaza.carga.productos.joinToString(", ") { it.etiqueta }} " +
                            "(referencia ${Correcciones.referencia(reemplaza.carga.id)}). Al guardarlo se quitan sus " +
                            "alarmas y quedan las de este.",
                    )
                }
            }
        }
        carga.productos.forEach { p ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(p.etiqueta, style = MaterialTheme.typography.titleMedium)
                    Text("${p.dosis}, ${describirIntervalo(p.intervalo)}")
                    p.nota?.let { Text(it) }
                }
            }
        }
        Text(LEYENDA, style = MaterialTheme.typography.bodySmall)
        Button(onClick = alGuardar, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Guardar en mi teléfono") }
        OutlinedButton(onClick = alCancelar, modifier = Modifier.fillMaxWidth()) { Text("No guardar") }
    }
}

@Composable
private fun Aviso(titulo: String, explicacion: String, alReintentar: (() -> Unit)?, alVolver: () -> Unit) {
    Marco(titulo) {
        Text(explicacion)
        alReintentar?.let { Button(onClick = it, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Escanear otro código") } }
        OutlinedButton(onClick = alVolver, modifier = Modifier.fillMaxWidth()) { Text("Volver al inicio") }
    }
}

/** En depuración, mientras no exista la emisión real (HU-01): un código válido generado aquí. */
@Composable
private fun CodigoDePrueba(alGenerar: (String) -> Unit) {
    val contexto = LocalContext.current
    val ejemplos = remember {
        runCatching {
            val firmantes = contexto.assets.open("firmantes-de-prueba.json").bufferedReader().use { it.readText() }
            val contenido = ClavesEmpaquetadas.cargar(contexto).contenido("prueba-c1") ?: error("sin clave de contenido")
            CodigosDeEjemplo.desdeJson(firmantes, contenido)
        }.getOrNull()
    } ?: return
    TextButton(onClick = { alGenerar(ejemplos.generar(CodigosDeEjemplo.Caso.VALIDO, Instant.now().epochSecond)) }) {
        Text("Usar un código de prueba")
    }
}

@Composable
private fun Marco(titulo: String, contenido: @Composable () -> Unit) {
    Column(
        modifier = Modifier.safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(titulo, style = MaterialTheme.typography.headlineMedium)
        contenido()
    }
}

/** RN-16: toda pantalla de tratamiento la muestra. */
internal const val LEYENDA = "Ante cualquier duda, consulte a su médico o químico farmacéutico."

/** PT12H se lee «cada 12 horas»; PT24H, «una vez al día». */
internal fun describirIntervalo(iso: String): String {
    val minutos = cl.midosis.credencial.CodecDeCarga.minutos(iso)
    return when {
        minutos == 24 * 60 -> "una vez al día"
        minutos % (24 * 60) == 0 -> "cada ${minutos / (24 * 60)} días"
        minutos % 60 == 0 -> if (minutos == 60) "cada 1 hora" else "cada ${minutos / 60} horas"
        else -> "cada ${minutos / 60} h ${minutos % 60} min"
    }
}
