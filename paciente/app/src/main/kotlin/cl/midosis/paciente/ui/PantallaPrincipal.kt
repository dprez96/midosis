package cl.midosis.paciente.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import cl.midosis.paciente.alarmas.PlanDeAlarmas
import cl.midosis.paciente.alarmas.ProgramadorDeAlarmas
import cl.midosis.paciente.alarmas.RegistroDeDisparos
import cl.midosis.paciente.alarmas.SerieDePrueba
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pantalla de prueba de alarmas (HU-15). La pantalla del paciente llega con la carga de
 * tratamientos desde el código (HU-08); esta sirve para verificar en teléfonos reales que
 * las alarmas suenan a tiempo y para alimentar el banco de dispositivos.
 */
class PantallaPrincipal : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface { PruebaDeAlarmas() }
            }
        }
    }
}

@Composable
private fun PruebaDeAlarmas() {
    val contexto = LocalContext.current
    val programador = remember { ProgramadorDeAlarmas(contexto) }
    val registro = remember { RegistroDeDisparos(contexto) }

    var notificaciones by remember { mutableStateOf(notificacionesPermitidas(contexto)) }
    var exactas by remember { mutableStateOf(programador.puedeProgramarExactas()) }
    var vigentes by remember { mutableIntStateOf(programador.cantidadVigentes()) }
    var mensaje by remember { mutableStateOf("") }
    var actualizar by remember { mutableIntStateOf(0) }

    // Al volver de Ajustes, el permiso puede haber cambiado.
    LifecycleResumeEffect(Unit) {
        notificaciones = notificacionesPermitidas(contexto)
        exactas = programador.puedeProgramarExactas()
        vigentes = programador.cantidadVigentes()
        actualizar++
        onPauseOrDispose { }
    }

    val pedirNotificaciones = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { concedido -> notificaciones = concedido }

    fun programar(tomas: List<cl.midosis.motor.planificacion.TomaProgramada>) {
        val plan = PlanDeAlarmas.construir(tomas, LocalDateTime.now())
        val n = try {
            programador.programar(plan)
        } catch (e: Exception) {
            // Si falla guardar el plan, la aplicación no se cierra: lo dice. Las alarmas ya
            // entregadas suenan igual, pero no sobrevivirían a un reinicio.
            mensaje = "No se pudo guardar el plan: las alarmas no sobrevivirán a un reinicio."
            return
        }
        vigentes = programador.cantidadVigentes()
        mensaje = if (n == 0) "No se programó nada: falta el permiso de alarmas exactas."
        else "Programadas: $n. Primera: ${plan.first().momento.format(HORA)}."
    }

    val disparos = remember(actualizar) { registro.disparos() }

    // Desde Android 15 la aplicación dibuja bajo las barras del sistema: el contenido
    // reserva ese espacio para no quedar tapado por la barra de estado.
    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Prueba de alarmas", style = MaterialTheme.typography.headlineMedium)

        Estado(
            titulo = "Notificaciones",
            ok = notificaciones,
            explicacion = "Sin este permiso la alarma no se ve ni suena.",
            accion = "Permitir notificaciones",
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pedirNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        Estado(
            titulo = "Alarmas exactas",
            ok = exactas,
            explicacion = "Sin este permiso las alarmas pueden sonar tarde o no sonar.",
            accion = "Abrir el ajuste de alarmas",
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                contexto.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${contexto.packageName}")),
                )
            }
        }

        Button(onClick = { programar(SerieDePrueba.alarmaUnica(LocalDateTime.now())) }, modifier = Modifier.fillMaxWidth()) {
            Text("Probar una alarma en 1 minuto")
        }
        Button(
            onClick = { programar(SerieDePrueba.serie(LocalDateTime.now(), Duration.ofMinutes(30), Duration.ofHours(24))) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Serie de banco: cada 30 min por 24 h")
        }
        OutlinedButton(
            onClick = {
                programador.cancelarTodas()
                vigentes = 0
                mensaje = "Alarmas de prueba canceladas."
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Cancelar las alarmas programadas")
        }

        if (mensaje.isNotEmpty()) Text(mensaje)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Registro del banco", style = MaterialTheme.typography.titleMedium)
                Text("Alarmas programadas: $vigentes")
                programador.proxima()?.let {
                    Text("Próxima: ${Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(HORA)}")
                }
                Text("Disparos registrados: ${disparos.size}")
                if (disparos.isNotEmpty()) {
                    val aTiempo = disparos.count { it.desfaseSegundos in 0..300 }
                    Text("Último desfase: ${disparos.last().desfaseSegundos} s")
                    Text("Dentro de 5 minutos: $aTiempo de ${disparos.size} (${aTiempo * 100 / disparos.size} %)")
                }
            }
        }
    }
}

@Composable
private fun Estado(titulo: String, ok: Boolean, explicacion: String, accion: String, alPedir: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$titulo: ${if (ok) "permitido" else "no permitido"}", style = MaterialTheme.typography.titleMedium)
            if (!ok) {
                Text(explicacion)
                Button(onClick = alPedir) { Text(accion) }
            }
        }
    }
}

private fun notificacionesPermitidas(contexto: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private val HORA = DateTimeFormatter.ofPattern("HH:mm")
