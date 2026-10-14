package cl.midosis.paciente.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import cl.midosis.paciente.alarmas.ProgramadorDeAlarmas
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Faltan permisos para que suenen las alarmas (CU-P-03, flujo 5a): se explica en lenguaje llano
 * qué pasa sin cada uno y se ofrece el atajo a su ajuste. Las alarmas quedan guardadas y se
 * programan apenas el paciente los concede.
 */
@Composable
fun PermisosDeAlarmas(alListo: () -> Unit) {
    val contexto = LocalContext.current
    val programador = remember { ProgramadorDeAlarmas(contexto) }
    var notificaciones by remember { mutableStateOf(notificacionesPermitidas(contexto)) }
    var exactas by remember { mutableStateOf(programador.puedeProgramarExactas()) }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificaciones = it }

    // Al volver de Ajustes, el permiso puede haber cambiado.
    LifecycleResumeEffect(Unit) {
        notificaciones = notificacionesPermitidas(contexto)
        exactas = programador.puedeProgramarExactas()
        onPauseOrDispose { }
    }

    Column(Modifier.safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Falta un permiso para tus alarmas", style = MaterialTheme.typography.headlineMedium)
        Text("Tu tratamiento quedó guardado, pero las alarmas no sonarán hasta que des estos permisos.")
        if (!notificaciones) {
            Permiso("Sin este permiso la alarma no se ve ni suena.", "Permitir notificaciones") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) pedir.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (!exactas) {
            Permiso("Sin este permiso las alarmas pueden sonar tarde o no sonar.", "Abrir el ajuste de alarmas") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    contexto.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${contexto.packageName}")),
                    )
                }
            }
        }
        if (notificaciones && exactas) {
            Button(onClick = alListo, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Programar mis alarmas") }
        } else {
            OutlinedButton(onClick = alListo, modifier = Modifier.fillMaxWidth()) { Text("Seguir sin alarmas por ahora") }
        }
    }
}

@Composable
private fun Permiso(explicacion: String, accion: String, alPedir: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(explicacion)
            Button(onClick = alPedir) { Text(accion) }
        }
    }
}

/**
 * La próxima toma programada (CU-P-03, paso 7), o un aviso visible y persistente si las alarmas
 * no están garantizadas.
 */
@Composable
fun ProximaToma() {
    val contexto = LocalContext.current
    val programador = remember { ProgramadorDeAlarmas(contexto) }
    var proxima by remember { mutableStateOf(programador.proxima()) }
    var garantizadas by remember { mutableStateOf(notificacionesPermitidas(contexto) && programador.puedeProgramarExactas()) }
    LifecycleResumeEffect(Unit) {
        proxima = programador.proxima()
        garantizadas = notificacionesPermitidas(contexto) && programador.puedeProgramarExactas()
        onPauseOrDispose { }
    }
    when {
        !garantizadas -> Text(
            "Alarmas no garantizadas: falta un permiso. Ábrelo desde «Cargar otro tratamiento» o desde los ajustes del teléfono.",
            color = MaterialTheme.colorScheme.error,
        )
        proxima != null -> Text(
            "Próxima toma: ${Instant.ofEpochMilli(proxima!!).atZone(ZoneId.systemDefault()).format(FECHA_Y_HORA)}",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

private val FECHA_Y_HORA = DateTimeFormatter.ofPattern("dd-MM HH:mm")

internal fun notificacionesPermitidas(contexto: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
