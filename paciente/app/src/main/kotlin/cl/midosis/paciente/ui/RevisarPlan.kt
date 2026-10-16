package cl.midosis.paciente.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cl.midosis.paciente.tratamiento.RevisionDelPlan
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Revisión del calendario propuesto antes de programar las alarmas (HU-14, CU-P-03). Cada toma
 * se corre de a 15 minutos o de a una hora. Por omisión se mueven también las siguientes, que
 * es lo que casi siempre se quiere; mover una sola respeta el intervalo de la posología y, si
 * no se puede, el motor explica por qué.
 */
@Composable
fun RevisarPlan(inicial: RevisionDelPlan, alConfirmar: (RevisionDelPlan) -> Unit, alUsarPropuesto: () -> Unit) {
    var revision by remember { mutableStateOf(inicial) }
    var conLasSiguientes by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Revisa las horas de tus tomas", style = MaterialTheme.typography.headlineMedium)
        Text("Las propusimos según tus horarios. Si alguna no te acomoda, muévela antes de activar las alarmas.")

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Mover también las tomas siguientes", modifier = Modifier.weight(1f))
            Switch(checked = conLasSiguientes, onCheckedChange = { conLasSiguientes = it })
        }

        revision.planes.forEachIndexed { i, p ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.etiqueta, style = MaterialTheme.typography.titleLarge)
                    Text(p.dosis)
                    revision.visibles(i).forEach { t ->
                        HoraDeToma(p.plan.tomas[t], p.etiqueta) { minutos ->
                            revision = revision.mover(i, t, minutos, conLasSiguientes, LocalDateTime.now())
                        }
                    }
                    revision.avisos[i]?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
        }

        Text(LEYENDA, style = MaterialTheme.typography.bodySmall)
        Button(onClick = { alConfirmar(revision) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("Confirmar y activar alarmas")
        }
        OutlinedButton(onClick = alUsarPropuesto, modifier = Modifier.fillMaxWidth()) { Text("Usar las horas propuestas") }
    }
}

@Composable
private fun HoraDeToma(momento: LocalDateTime, etiqueta: String, alMover: (Long) -> Unit) {
    val nombre = "$etiqueta, ${dia(momento.toLocalDate())} a las ${momento.format(HORA)}"
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Ajuste("−1 h", "$nombre: una hora antes") { alMover(-60) }
        Ajuste("−15", "$nombre: 15 minutos antes") { alMover(-15) }
        Column(Modifier.weight(1f)) {
            Text(
                dia(momento.toLocalDate()),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                momento.format(HORA),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Ajuste("+15", "$nombre: 15 minutos después") { alMover(15) }
        Ajuste("+1 h", "$nombre: una hora después") { alMover(60) }
    }
}

/** El lector de pantalla dice qué hace el botón, no «más quince». */
@Composable
private fun Ajuste(texto: String, descripcion: String, alTocar: () -> Unit) {
    OutlinedButton(onClick = alTocar, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = descripcion }) {
        Text(texto)
    }
}

private val HORA = DateTimeFormatter.ofPattern("HH:mm")

private fun dia(fecha: LocalDate): String {
    val hoy = LocalDate.now()
    return when (fecha) {
        hoy -> "Hoy"
        hoy.plusDays(1) -> "Mañana"
        else -> fecha.format(DateTimeFormatter.ofPattern("dd-MM"))
    }
}
