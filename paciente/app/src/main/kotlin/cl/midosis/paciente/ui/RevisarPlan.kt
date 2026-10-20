package cl.midosis.paciente.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * se corre de a 15 minutos o de a una hora, y con ella todas las del mismo medicamento: así el
 * intervalo de la posología se mantiene siempre.
 */
@Composable
fun RevisarPlan(inicial: RevisionDelPlan, alConfirmar: (RevisionDelPlan) -> Unit, alUsarPropuesto: () -> Unit) {
    var revision by remember { mutableStateOf(inicial) }

    Column(
        modifier = Modifier.safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Revisa las horas de tus tomas", style = MaterialTheme.typography.headlineMedium)
        Text("Las propusimos según tus horarios. Si no te acomodan, muévelas antes de activar las alarmas.")
        Text("Al mover una toma se mueven todas las de ese medicamento, para que el tiempo entre ellas no cambie.")

        revision.planes.forEachIndexed { i, p ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.etiqueta, style = MaterialTheme.typography.titleLarge)
                    Text(p.dosis)
                    revision.visibles(i).forEach { t ->
                        HoraDeToma(p.plan.tomas[t], p.etiqueta) { minutos ->
                            revision = revision.mover(i, minutos, LocalDateTime.now())
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
    // La hora arriba y los botones abajo, repartidos en el ancho: con letra grande, cuatro
    // botones al lado de la hora no caben en un teléfono.
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "${dia(momento.toLocalDate())}, ${momento.format(HORA)}",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Ajuste("−1 h", "$nombre: una hora antes", Modifier.weight(1f)) { alMover(-60) }
            Ajuste("−15", "$nombre: 15 minutos antes", Modifier.weight(1f)) { alMover(-15) }
            Ajuste("+15", "$nombre: 15 minutos después", Modifier.weight(1f)) { alMover(15) }
            Ajuste("+1 h", "$nombre: una hora después", Modifier.weight(1f)) { alMover(60) }
        }
    }
}

/** El lector de pantalla dice qué hace el botón, no «más quince». */
@Composable
private fun Ajuste(texto: String, descripcion: String, modifier: Modifier, alTocar: () -> Unit) {
    OutlinedButton(
        onClick = alTocar,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        modifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = descripcion },
    ) {
        Text(texto, maxLines = 1, softWrap = false)
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
