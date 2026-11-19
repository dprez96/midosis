package cl.midosis.paciente.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cl.midosis.paciente.perfiles.Perfil

/**
 * La inicial en un círculo del color del perfil, junto al apodo (RN-21). El color nunca va solo:
 * siempre lo acompañan la inicial y el apodo, para quien no distingue colores.
 */
@Composable
fun MarcaDePerfil(perfil: Perfil, grande: Boolean = false) {
    val lado = if (grande) 48.dp else 32.dp
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Perfil de ${perfil.apodo}" },
    ) {
        Box(
            Modifier.size(lado).background(Color(perfil.color.argb), CircleShape).clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Text(perfil.inicial.toString(), color = Color.White, style = if (grande) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall)
        }
        Text(perfil.apodo, style = if (grande) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium)
    }
}
