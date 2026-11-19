package cl.midosis.paciente.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cl.midosis.paciente.perfiles.ColorDePerfil
import cl.midosis.paciente.perfiles.Perfil
import cl.midosis.paciente.perfiles.Perfiles
import cl.midosis.paciente.tratamiento.TratamientoGuardado

/**
 * Las personas cuyos tratamientos se administran en este teléfono (HU-31, RN-21): hasta cuatro,
 * cada una con un apodo, un color y sus propios horarios. Solo un apodo: nunca el nombre
 * completo ni el RUT. Un perfil con tratamientos no se puede quitar.
 */
@Composable
fun PantallaDePerfiles(
    perfiles: List<Perfil>,
    tratamientos: List<TratamientoGuardado>,
    alGuardar: (List<Perfil>) -> Unit,
    alAjustarHorarios: (String) -> Unit,
    alVolver: () -> Unit,
) {
    // null: ninguno en edición; un id: ese perfil; NUEVO: uno que se está agregando.
    var editando by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Personas en este teléfono", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Si cuidas a otras personas, cada una tiene su perfil con su color. Al cargar un código eliges para quién es, " +
                "y cada alarma dice de quién es la toma.",
        )
        Text("Usa un apodo, como «Mamá» o «Juan». No escribas el nombre completo ni el RUT.", style = MaterialTheme.typography.bodySmall)

        perfiles.forEach { p ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editando == p.id) {
                        EdicionDePerfil(
                            inicial = p,
                            otros = perfiles - p,
                            alGuardar = { cambiado ->
                                alGuardar(perfiles.map { if (it.id == p.id) cambiado else it })
                                editando = null
                            },
                            alCancelar = { editando = null },
                        )
                    } else {
                        MarcaDePerfil(p)
                        val cuantos = tratamientos.count { it.perfil == p.id }
                        Text(if (cuantos == 1) "1 tratamiento" else "$cuantos tratamientos", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { editando = p.id }) { Text("Cambiar apodo o color") }
                            TextButton(onClick = { alAjustarHorarios(p.id) }) { Text("Sus horarios") }
                        }
                        if (cuantos == 0 && p.id != Perfiles.PRINCIPAL) {
                            TextButton(onClick = { alGuardar(perfiles - p) }) { Text("Quitar este perfil", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }

        if (editando == NUEVO) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    EdicionDePerfil(
                        inicial = Perfil(Perfiles.nuevoId(), "", Perfiles.colorLibre(perfiles)),
                        otros = perfiles,
                        alGuardar = { nuevo ->
                            alGuardar(perfiles + nuevo)
                            editando = null
                        },
                        alCancelar = { editando = null },
                    )
                }
            }
        } else if (perfiles.size < Perfiles.MAXIMO) {
            Button(onClick = { editando = NUEVO }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Agregar una persona") }
        } else {
            Text("Ya hay ${Perfiles.MAXIMO} perfiles, el máximo en un teléfono.")
        }
        OutlinedButton(onClick = alVolver, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }
}

private const val NUEVO = "nuevo"

@Composable
private fun EdicionDePerfil(inicial: Perfil, otros: List<Perfil>, alGuardar: (Perfil) -> Unit, alCancelar: () -> Unit) {
    var apodo by remember { mutableStateOf(inicial.apodo) }
    var color by remember { mutableStateOf(inicial.color) }
    val problema = Perfiles.problemaDelApodo(apodo, otros)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = apodo,
            onValueChange = { apodo = it.take(Perfiles.APODO_MAXIMO) },
            label = { Text("Apodo") },
            singleLine = true,
            isError = apodo.isNotEmpty() && problema != null,
            supportingText = { if (apodo.isNotEmpty() && problema != null) Text(problema) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Color", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ColorDePerfil.entries.forEach { c ->
                Box(
                    Modifier.size(48.dp)
                        .background(Color(c.argb), CircleShape)
                        .border(if (c == color) 4.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .selectable(selected = c == color, role = Role.RadioButton) { color = c }
                        .semantics { contentDescription = c.nombre },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { alGuardar(inicial.copy(apodo = apodo.trim(), color = color)) }, enabled = problema == null) { Text("Guardar") }
            OutlinedButton(onClick = alCancelar) { Text("Cancelar") }
        }
    }
}
