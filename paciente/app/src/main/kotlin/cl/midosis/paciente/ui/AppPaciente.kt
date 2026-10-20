package cl.midosis.paciente.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cl.midosis.credencial.Carga
import cl.midosis.credencial.Lectura
import cl.midosis.motor.rutina.Rutina
import cl.midosis.paciente.BuildConfig
import cl.midosis.paciente.codigo.ClavesEmpaquetadas
import cl.midosis.paciente.codigo.CodigosDeEjemplo
import cl.midosis.paciente.codigo.MensajeDeRechazo
import cl.midosis.paciente.rutina.AlmacenDeRutina
import cl.midosis.paciente.rutina.Entrevista
import cl.midosis.paciente.rutina.RutinaGuardada
import cl.midosis.paciente.tratamiento.AlmacenDeTratamientos
import cl.midosis.paciente.tratamiento.TratamientoGuardado
import cl.midosis.paciente.tratamiento.Resumen
import cl.midosis.paciente.alarmas.Diagnostico
import cl.midosis.paciente.alarmas.PlanDeAlarmas
import cl.midosis.paciente.alarmas.ProgramadorDeAlarmas
import cl.midosis.paciente.tratamiento.Planificacion
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    var pantalla by remember { mutableStateOf<Pantalla>(Pantalla.Inicio) }
    var porProgramar by remember { mutableStateOf<TratamientoGuardado?>(null) }
    val programador = remember { ProgramadorDeAlarmas(contexto) }

    BackHandler(enabled = pantalla != Pantalla.Inicio) { pantalla = Pantalla.Inicio }

    fun leerCodigo(texto: String) {
        pantalla = when (val lectura = lector.leer(texto.trim())) {
            is Lectura.Aceptado -> Pantalla.Confirmar(lectura)
            is Lectura.Rechazado -> Pantalla.Rechazo(MensajeDeRechazo.de(lectura.motivo))
        }
    }

    /**
     * Programa las alarmas del tramo cubierto (HU-08). Si no quedó ninguna, faltan permisos: se
     * dice, porque el sistema nunca debe aparentar estar activo sin estarlo (CU-P-03).
     */
    fun programarAlarmas(t: TratamientoGuardado, conRutina: RutinaGuardada) {
        val ahora = LocalDateTime.now()
        val tomas = Planificacion.tomasDe(t, conRutina.rutina, ahora)
        val programadas = programador.programar(PlanDeAlarmas.construir(tomas, ahora), ahora)
        // También si quedaron programadas pero el ahorro de batería puede retrasarlas (HU-24).
        pantalla = if (programadas == 0 || !Diagnostico.del(contexto).todoEnOrden) Pantalla.Permisos else Pantalla.Inicio
    }

    fun guardarRutina(guardada: RutinaGuardada) {
        rutinas.guardar(guardada)
        rutina = guardada
        val pendiente = porProgramar
        porProgramar = null
        if (pendiente != null) programarAlarmas(pendiente, guardada) else pantalla = Pantalla.Inicio
    }

    when (val p = pantalla) {
        Pantalla.Inicio -> Inicio(
            tratamientos = lista,
            rutina = rutina,
            alCargar = { pantalla = Pantalla.Escanear },
            alAjustarRutina = { pantalla = Pantalla.Entrevistar(rutina?.let { Entrevista.desde(it.rutina) } ?: Entrevista()) },
            alAbrirPruebas = { pantalla = Pantalla.Pruebas },
            alRevisarPermisos = { pantalla = Pantalla.Permisos },
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
            alGuardar = {
                val nuevo = TratamientoGuardado(p.lectura.carga, Instant.now().epochSecond, p.lectura.kid)
                pantalla = when (tratamientos.guardar(nuevo)) {
                    AlmacenDeTratamientos.Resultado.YaCargado -> Pantalla.YaCargado
                    AlmacenDeTratamientos.Resultado.Guardado -> {
                        lista = tratamientos.leer()
                        val conRutina = rutina
                        if (conRutina == null) {
                            // Con el primer tratamiento se pregunta la rutina (CU-P-02), y las
                            // alarmas se programan al terminar la entrevista.
                            porProgramar = nuevo
                            Pantalla.Entrevistar(Entrevista())
                        } else {
                            programarAlarmas(nuevo, conRutina)
                            pantalla
                        }
                    }
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
    rutina: RutinaGuardada?,
    alCargar: () -> Unit,
    alAjustarRutina: () -> Unit,
    alAbrirPruebas: () -> Unit,
    alRevisarPermisos: () -> Unit,
) {
    Marco("MiDosis") {
        if (tratamientos.isEmpty()) {
            Text("Te recordamos tus medicamentos a la hora que corresponde, aunque no tengas internet.")
            Text("No necesitas crear una cuenta ni dar tu nombre: todo queda en tu teléfono.")
        } else {
            val hoy = LocalDate.now()
            tratamientos.forEach { t ->
                t.carga.productos.forEach { p -> TarjetaDeMedicamento(Resumen.de(p, t.carga.emitidoEn, hoy)) }
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
        if (BuildConfig.DEBUG) TextButton(onClick = alAbrirPruebas) { Text("Herramientas de prueba") }
    }
}

/**
 * Las dos cifras de la HU-19 a la vista, sin navegar: cuánto dura el tratamiento indicado y
 * hasta cuándo alcanza lo retirado. Nunca dice «terminado» mientras falte retirar (RN-07).
 */
@Composable
private fun TarjetaDeMedicamento(r: Resumen) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(r.etiqueta, style = MaterialTheme.typography.titleLarge)
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

private val FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy")

private fun dias(n: Long): String = if (n == 1L) "1 día" else "$n días"

/** Lo que trae el código, para que el paciente lo revise antes de guardarlo. */
@Composable
private fun Confirmacion(carga: Carga, alGuardar: () -> Unit, alCancelar: () -> Unit) {
    Marco("Revisa tu tratamiento") {
        Text("Entregado por la farmacia ${carga.emisor}.")
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
