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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import cl.midosis.motor.rutina.Rutina
import cl.midosis.paciente.rutina.Entrevista
import cl.midosis.paciente.rutina.Entrevista.Hora
import cl.midosis.paciente.rutina.Entrevista.Paso
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * La entrevista de rutina (HU-13): despertar, comidas, acostarse, alarmas nocturnas y
 * resumen. Cada paso trae ya puesta la rutina habitual; quien la acepta solo toca
 * «Siguiente». Se puede omitir en cualquier paso: se aplica la rutina por defecto y se
 * ofrece ajustarla después.
 */
@Composable
fun EntrevistaDeRutina(inicial: Entrevista, alTerminar: (Rutina) -> Unit, alOmitir: () -> Unit) {
    var e by remember { mutableStateOf(inicial) }

    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Paso ${e.paso.numero} de ${Paso.entries.size}", style = MaterialTheme.typography.labelLarge)

        when (e.paso) {
            Paso.DESPERTAR -> {
                Titulo("¿A qué hora te levantas habitualmente?")
                SelectorDeHora("Me levanto a las", e.despertar) { e = e.mover(Hora.DESPERTAR, it) }
            }
            Paso.COMIDAS -> {
                Titulo("¿A qué hora comes?")
                SelectorDeHora("Desayuno", e.desayuno) { e = e.mover(Hora.DESAYUNO, it) }
                SelectorDeHora("Almuerzo", e.almuerzo) { e = e.mover(Hora.ALMUERZO, it) }
                SelectorDeHora("Cena", e.cena) { e = e.mover(Hora.CENA, it) }
            }
            Paso.ACOSTARSE -> {
                Titulo("¿A qué hora te acuestas?")
                SelectorDeHora("Me acuesto a las", e.acostarse) { e = e.mover(Hora.ACOSTARSE, it) }
            }
            Paso.NOCHE -> {
                Titulo("¿Te puede sonar una alarma mientras duermes?")
                Text("Si tu medicamento es cada 8 horas o menos, puede ser necesario igual.")
                Opcion("Sí, si mi tratamiento lo necesita", e.toleraAlarmasNocturnas) { e = e.copy(toleraAlarmasNocturnas = true) }
                Opcion("Prefiero que no", !e.toleraAlarmasNocturnas) { e = e.copy(toleraAlarmasNocturnas = false) }
            }
            Paso.RESUMEN -> {
                Titulo("Revisa tus horarios")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Te levantas: ${hora(e.despertar)}")
                        Text("Desayuno: ${hora(e.desayuno)}")
                        Text("Almuerzo: ${hora(e.almuerzo)}")
                        Text("Cena: ${hora(e.cena)}")
                        Text("Te acuestas: ${hora(e.acostarse)}")
                        Text("Alarmas de noche: ${if (e.toleraAlarmasNocturnas) "sí, si hace falta" else "prefieres que no"}")
                    }
                }
                Text("Estos horarios se quedan en tu teléfono. No se envían a nadie.", style = MaterialTheme.typography.bodySmall)
            }
        }

        e.problema?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (e.paso != Paso.DESPERTAR) {
                OutlinedButton(onClick = { e = e.anterior() }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    Text("Atrás")
                }
            }
            Button(
                onClick = {
                    if (e.paso == Paso.RESUMEN) e.rutina()?.let(alTerminar) else e = e.siguiente()
                },
                enabled = e.problema == null,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) {
                Text(if (e.paso == Paso.RESUMEN) "Guardar" else "Siguiente")
            }
        }
        TextButton(onClick = alOmitir, modifier = Modifier.fillMaxWidth()) {
            Text("Omitir y usar horarios habituales")
        }
    }
}

@Composable
private fun Titulo(texto: String) {
    Text(texto, style = MaterialTheme.typography.headlineSmall)
}

/**
 * Botones grandes de más y menos en vez de un reloj: más fáciles de tocar para un adulto mayor.
 * La hora va arriba y los botones abajo, repartidos en el ancho: con letra grande o en una
 * pantalla angosta, cuatro botones al lado de la hora no caben y los números quedaban en
 * vertical.
 */
@Composable
private fun SelectorDeHora(etiqueta: String, valor: LocalTime, alMover: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(etiqueta, style = MaterialTheme.typography.titleMedium)
            Text(
                hora(valor),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                BotonDeAjuste("−1 h", "$etiqueta: una hora antes", Modifier.weight(1f)) { alMover(-60) }
                BotonDeAjuste("−15", "$etiqueta: 15 minutos antes", Modifier.weight(1f)) { alMover(-15) }
                BotonDeAjuste("+15", "$etiqueta: 15 minutos después", Modifier.weight(1f)) { alMover(15) }
                BotonDeAjuste("+1 h", "$etiqueta: una hora después", Modifier.weight(1f)) { alMover(60) }
            }
        }
    }
}

/** El lector de pantalla dice qué hace el botón, no «menos quince». */
@Composable
private fun BotonDeAjuste(texto: String, descripcion: String, modifier: Modifier, alTocar: () -> Unit) {
    OutlinedButton(
        onClick = alTocar,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        modifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = descripcion },
    ) {
        Text(texto, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun Opcion(texto: String, elegida: Boolean, alElegir: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        RadioButton(selected = elegida, onClick = alElegir)
        TextButton(onClick = alElegir) { Text(texto) }
    }
}

private val FORMATO = DateTimeFormatter.ofPattern("HH:mm")

private fun hora(h: LocalTime): String = h.format(FORMATO)
