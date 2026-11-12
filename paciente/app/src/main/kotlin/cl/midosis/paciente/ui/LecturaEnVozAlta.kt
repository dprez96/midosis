package cl.midosis.paciente.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import cl.midosis.paciente.alarmas.Locutor
import cl.midosis.paciente.alarmas.TextoDeLocucion

/**
 * Activar la lectura en voz alta de la toma (HU-22). Antes de activarla revisa que el teléfono
 * tenga una voz en español; si no la tiene, guía al paciente a instalarla.
 */
@Composable
fun LecturaEnVozAlta(alVolver: () -> Unit) {
    val contexto = LocalContext.current
    var activa by remember { mutableStateOf(Locutor.activada(contexto)) }
    var sinVoz by remember { mutableStateOf(false) }
    var revisando by remember { mutableStateOf(false) }

    // Al volver de instalar la voz, se revisa de nuevo.
    LifecycleResumeEffect(sinVoz) {
        if (sinVoz) Locutor.comprobarVoz(contexto) { hay -> if (hay) sinVoz = false }
        onPauseOrDispose { }
    }

    Column(
        Modifier.safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Lectura en voz alta", style = MaterialTheme.typography.headlineMedium)
        Text("Cuando suene una toma, el teléfono leerá en voz alta el medicamento, la dosis y cómo tomarlo.")
        Text("Funciona sin internet, con la voz del teléfono.")
        Text("Ten en cuenta que quienes estén cerca escucharán el nombre de tus medicamentos.")
        Text(
            if (activa) "Está activada." else "Está desactivada.",
            style = MaterialTheme.typography.titleMedium,
        )

        if (sinVoz) {
            Text(
                "Tu teléfono no tiene una voz en español. Instálala para poder escuchar tus tomas; después vuelve aquí.",
                color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = { instalarVoz(contexto) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("Instalar la voz en español")
            }
        }

        if (activa) {
            Button(
                onClick = { Locutor.hablar(contexto, TextoDeLocucion.armar(listOf(EJEMPLO))) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text("Escuchar un ejemplo") }
            OutlinedButton(
                onClick = { Locutor.activar(contexto, false); activa = false },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Desactivar") }
        } else {
            Button(
                enabled = !revisando,
                onClick = {
                    revisando = true
                    Locutor.comprobarVoz(contexto) { hay ->
                        revisando = false
                        sinVoz = !hay
                        if (hay) {
                            Locutor.activar(contexto, true)
                            activa = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(if (revisando) "Revisando la voz del teléfono…" else "Activar la lectura en voz alta") }
        }
        OutlinedButton(onClick = alVolver, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }
}

/** Abre el instalador de voces del sistema; si el teléfono no lo tiene, sus ajustes de voz. */
private fun instalarVoz(contexto: Context) {
    val intentos = listOf(
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
        Intent("com.android.settings.TTS_SETTINGS"),
    )
    for (intento in intentos) {
        try {
            contexto.startActivity(intento.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            // Se prueba la siguiente opción.
        }
    }
}

/** Un medicamento sintético para el ejemplo: no es un tratamiento del paciente. */
private const val EJEMPLO = "Paracetamol 500 mg · 1 comprimido"
