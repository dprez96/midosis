package cl.midosis.paciente.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp

/**
 * Qué guarda MiDosis y cómo borrarlo (HU-25). Borrar no pide motivo ni conexión: basta con
 * confirmarlo una vez, porque no se puede deshacer.
 */
@Composable
fun Privacidad(alBorrar: () -> Unit, alVolver: () -> Unit) {
    var confirmando by remember { mutableStateOf(false) }

    Column(
        Modifier.safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Privacidad y mis datos", style = MaterialTheme.typography.headlineMedium)
        Text("MiDosis guarda en este teléfono, cifrados, tus tratamientos, tus horarios y lo que respondes a cada recordatorio.")
        Text("Nada de eso se envía a la farmacia ni a nadie: MiDosis no tiene una copia.")
        Text("Puedes borrarlo todo cuando quieras, sin dar un motivo y sin internet. Se borran también tus alarmas.")
        Button(
            onClick = { confirmando = true },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text("Borrar todos mis datos") }
        OutlinedButton(onClick = alVolver, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }

    if (confirmando) {
        AlertDialog(
            onDismissRequest = { confirmando = false },
            title = { Text("¿Borrar todos tus datos?") },
            text = {
                Text(
                    "Se borran tus tratamientos, tus horarios y tus alarmas. No se puede deshacer: para volver a " +
                        "usar MiDosis tendrás que cargar de nuevo el código de la farmacia, si todavía está vigente.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmando = false; alBorrar() }) {
                    Text("Borrar todo", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmando = false }) { Text("Cancelar") } },
        )
    }
}
