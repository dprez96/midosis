package cl.midosis.paciente.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.midosis.paciente.alarmas.ReceptorDeAlarma
import cl.midosis.paciente.alarmas.ReceptorDeRespuesta
import cl.midosis.paciente.alarmas.Respuesta
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * La toma a pantalla completa (HU-16): aparece sola cuando suena la alarma, también con el
 * teléfono bloqueado, con botones grandes para contestar sin tener que desplegar la
 * notificación. Contestar anota la respuesta, apaga el aviso y cierra la pantalla.
 *
 * Salir sin contestar no cuenta como respuesta: el recordatorio se repite a los 10 y a los
 * 25 minutos.
 */
class PantallaDeToma : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(ReceptorDeAlarma.EXTRA_ID, 0)
        val programada = intent.getLongExtra(ReceptorDeAlarma.EXTRA_PROGRAMADA, 0)
        val lineas = intent.getStringArrayExtra(ReceptorDeAlarma.EXTRA_LINEAS)?.toList().orEmpty()

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Toma(programada, lineas) { respuesta ->
                        ReceptorDeRespuesta.responder(this, id, programada, lineas, respuesta)
                        finish()
                    }
                }
            }
        }
    }
}

@Composable
private fun Toma(programada: Long, lineas: List<String>, alResponder: (Respuesta) -> Unit) {
    Column(
        Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            if (lineas.size > 1) "Hora de tus medicamentos" else "Hora de tu medicamento",
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            "Programada para las ${Instant.ofEpochMilli(programada).atZone(ZoneId.systemDefault()).format(HORA)}",
            style = MaterialTheme.typography.titleMedium,
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lineas.forEach { Text(it, style = MaterialTheme.typography.titleLarge) }
            }
        }
        Button(onClick = { alResponder(Respuesta.TOMADA) }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
            Text("Tomé", style = MaterialTheme.typography.titleLarge)
        }
        OutlinedButton(onClick = { alResponder(Respuesta.POSPUESTA) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("Recuérdame en 10 minutos")
        }
        OutlinedButton(onClick = { alResponder(Respuesta.OMITIDA) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("No la tomaré")
        }
        Text(LEYENDA, style = MaterialTheme.typography.bodySmall)
    }
}

private val HORA = DateTimeFormatter.ofPattern("HH:mm")
