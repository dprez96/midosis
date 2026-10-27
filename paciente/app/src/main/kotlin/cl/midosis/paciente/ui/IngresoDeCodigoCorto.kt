package cl.midosis.paciente.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cl.midosis.credencial.CodigoCorto
import cl.midosis.paciente.BuildConfig
import cl.midosis.paciente.codigo.ClienteDeCanje
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * El código corto y el PIN del comprobante (HU-10), para cuando el QR no se puede leer. Revisa
 * el código antes de enviarlo, así un error de tipeo no gasta uno de los intentos.
 */
@Composable
fun IngresoDeCodigoCorto(alObtener: (String) -> Unit, alCancelar: () -> Unit) {
    val cliente = remember { ClienteDeCanje(BuildConfig.URL_CORE) }
    val alcance = rememberCoroutineScope()
    var codigo by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var consultando by remember { mutableStateOf(false) }

    if (!cliente.disponible) {
        Text("La carga con código y PIN todavía no está disponible. Pide ayuda en la farmacia.")
        OutlinedButton(onClick = alCancelar, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Están impresos en el comprobante, bajo el código QR. Necesitas conexión a internet.")
        OutlinedTextField(
            value = codigo,
            onValueChange = { codigo = it.take(12) },
            label = { Text("Código (10 letras y números)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = pin,
            onValueChange = { nuevo -> pin = nuevo.filter(Char::isDigit).take(4) },
            label = { Text("PIN (4 números)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        mensaje?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        Button(
            enabled = !consultando,
            onClick = {
                val corto = CodigoCorto.de(codigo)
                when {
                    corto == null -> mensaje = "Revisa el código: tiene 10 caracteres y alguno no coincide."
                    pin.length != 4 -> mensaje = "El PIN tiene 4 números."
                    else -> {
                        consultando = true
                        mensaje = null
                        alcance.launch {
                            val respuesta = withContext(Dispatchers.IO) { cliente.canjear(corto, pin) }
                            consultando = false
                            when (respuesta) {
                                is ClienteDeCanje.Respuesta.Codigo -> alObtener(respuesta.texto)
                                is ClienteDeCanje.Respuesta.Rechazo -> mensaje = respuesta.mensaje
                                ClienteDeCanje.Respuesta.SinConexion ->
                                    mensaje = "No se pudo conectar con MiDosis. Revisa tu conexión a internet y vuelve a intentar en un momento."
                                ClienteDeCanje.Respuesta.NoDisponible -> mensaje = "La carga con código y PIN no está disponible."
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(if (consultando) "Buscando…" else "Cargar el tratamiento") }
        OutlinedButton(onClick = alCancelar, modifier = Modifier.fillMaxWidth()) { Text("Volver a la cámara") }
    }
}
