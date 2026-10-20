package cl.midosis.paciente.ui

import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import cl.midosis.paciente.alarmas.Diagnostico
import cl.midosis.paciente.alarmas.ProgramadorDeAlarmas
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Lo que el teléfono puede usar para impedir que suenen las alarmas (HU-24, CU-P-03 flujo 5a):
 * cada cosa se explica en lenguaje llano, con el atajo directo a su ajuste. Las alarmas quedan
 * guardadas y se programan apenas se conceden los permisos.
 */
@Composable
fun PermisosDeAlarmas(alListo: () -> Unit) {
    val contexto = LocalContext.current
    var d by remember { mutableStateOf(Diagnostico.del(contexto)) }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { d = Diagnostico.del(contexto) }

    // Al volver de Ajustes, algo puede haber cambiado.
    LifecycleResumeEffect(Unit) {
        d = Diagnostico.del(contexto)
        onPauseOrDispose { }
    }

    Column(
        Modifier.safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            if (d.alarmasGarantizadas) "Revisa el ahorro de batería" else "Falta un permiso para tus alarmas",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            if (d.alarmasGarantizadas) {
                "Tus alarmas quedaron programadas, pero el teléfono podría retrasarlas para ahorrar batería."
            } else {
                "Tu tratamiento quedó guardado, pero las alarmas no sonarán hasta que des estos permisos."
            },
        )
        if (!d.notificaciones) {
            Ajuste("Sin este permiso la alarma no se ve ni suena.", "Permitir notificaciones") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) pedir.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (!d.alarmasExactas) {
            Ajuste("Sin este permiso las alarmas pueden sonar tarde o no sonar.", "Abrir el ajuste de alarmas") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    contexto.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${contexto.packageName}")),
                    )
                }
            }
        }
        if (!d.fueraDelAhorroDeBateria) {
            Ajuste(
                "El ahorro de batería puede retrasar las alarmas. En la lista, busca MiDosis y elige «No optimizar» o «Sin restricciones».",
                "Abrir el ahorro de batería",
            ) {
                contexto.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        d.consejoDelFabricante?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (d.alarmasGarantizadas) {
            Button(onClick = alListo, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Listo") }
        } else {
            OutlinedButton(onClick = alListo, modifier = Modifier.fillMaxWidth()) { Text("Seguir sin alarmas por ahora") }
        }
    }
}

@Composable
private fun Ajuste(explicacion: String, accion: String, alPedir: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(explicacion)
            Button(onClick = alPedir) { Text(accion) }
        }
    }
}

/**
 * La próxima toma programada (CU-P-03, paso 7), o el aviso visible y persistente de «alarmas no
 * garantizadas» si falta un permiso (HU-24).
 */
@Composable
fun ProximaToma(alRevisar: () -> Unit) {
    val contexto = LocalContext.current
    val programador = remember { ProgramadorDeAlarmas(contexto) }
    var proxima by remember { mutableStateOf(programador.proxima()) }
    var d by remember { mutableStateOf(Diagnostico.del(contexto)) }
    LifecycleResumeEffect(Unit) {
        proxima = programador.proxima()
        d = Diagnostico.del(contexto)
        onPauseOrDispose { }
    }
    if (!d.alarmasGarantizadas) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Alarmas no garantizadas", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Text("Falta un permiso para que suenen. Tus tratamientos siguen guardados.")
                Button(onClick = alRevisar) { Text("Revisar los permisos") }
            }
        }
        return
    }
    proxima?.let {
        Text(
            "Próxima toma: ${Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(FECHA_Y_HORA)}",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

private val FECHA_Y_HORA = DateTimeFormatter.ofPattern("dd-MM HH:mm")

internal fun notificacionesPermitidas(contexto: android.content.Context): Boolean = Diagnostico.del(contexto).notificaciones
