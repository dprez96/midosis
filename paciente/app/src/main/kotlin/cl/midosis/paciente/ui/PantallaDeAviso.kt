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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cl.midosis.paciente.aviso.AvisoDeUso
import cl.midosis.paciente.aviso.Seccion

/**
 * El aviso de uso y responsabilidad. La primera vez se cierra solo con [AvisoDeUso.BOTON]
 * ([alEntender]); al releerlo desde la pantalla principal, con «Volver» ([alVolver]).
 *
 * Las secciones urgentes van destacadas: piden actuar de inmediato.
 */
@Composable
fun PantallaDeAviso(alEntender: (() -> Unit)?, alVolver: (() -> Unit)?) {
    Column(
        Modifier.safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(AvisoDeUso.TITULO, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(AvisoDeUso.INTRODUCCION)

        AvisoDeUso.SECCIONES.forEach { s ->
            Card(
                colors = if (s.urgente) {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                } else {
                    CardDefaults.cardColors()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { ContenidoDeSeccion(s) }
        }

        alEntender?.let {
            Button(onClick = it, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(AvisoDeUso.BOTON) }
        }
        alVolver?.let {
            OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Volver") }
        }
    }
}

@Composable
private fun ContenidoDeSeccion(s: Seccion) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(s.titulo, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        s.puntos.forEach { punto ->
            // La viñeta es solo visual: el lector de pantalla lee cada punto como un párrafo.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", modifier = Modifier.clearAndSetSemantics { })
                Text(punto)
            }
        }
    }
}
