package cl.midosis.paciente.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cl.midosis.credencial.Lectura
import cl.midosis.paciente.codigo.ClavesEmpaquetadas
import cl.midosis.paciente.codigo.CodigosDeEjemplo
import cl.midosis.paciente.codigo.MensajeDeRechazo
import java.time.Instant

/**
 * Prueba de la verificación de códigos (HU-09), solo en la compilación de depuración.
 * Genera cada caso en el teléfono con las claves de prueba y lo pasa por el mismo lector
 * que usará la carga real. No guarda nada.
 */
@Composable
fun PruebaDeCodigos() {
    val contexto = LocalContext.current
    val preparado = remember {
        runCatching {
            val claves = ClavesEmpaquetadas.cargar(contexto)
            val firmantes = contexto.assets.open("firmantes-de-prueba.json").bufferedReader().use { it.readText() }
            val contenido = claves.contenido("prueba-c1") ?: error("falta la clave de contenido de prueba")
            ClavesEmpaquetadas.lector(contexto) to CodigosDeEjemplo.desdeJson(firmantes, contenido)
        }.getOrNull()
    } ?: return
    val (lector, ejemplos) = preparado

    var caso by remember { mutableStateOf<CodigosDeEjemplo.Caso?>(null) }
    var lectura by remember { mutableStateOf<Lectura?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Prueba de códigos", style = MaterialTheme.typography.titleMedium)
            Text("Cada botón genera un código con las claves de prueba y lo verifica sin conexión.")
            CodigosDeEjemplo.Caso.entries.forEach { c ->
                OutlinedButton(
                    onClick = {
                        caso = c
                        lectura = lector.leer(ejemplos.generar(c, Instant.now().epochSecond))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(c.nombre) }
            }
            caso?.let { Text("Caso: ${it.nombre}", style = MaterialTheme.typography.labelLarge) }
            when (val l = lectura) {
                is Lectura.Aceptado -> {
                    Text("Código auténtico", style = MaterialTheme.typography.titleMedium)
                    Text("Emitido por ${l.carga.emisor} con la clave ${l.kid}.")
                    l.carga.productos.forEach { p ->
                        Text("${p.etiqueta}: ${p.dosis}, ${p.duracionIndicadaDias} días indicados, ${p.coberturaDias} cubiertos.")
                    }
                }
                is Lectura.Rechazado -> {
                    val mensaje = MensajeDeRechazo.de(l.motivo)
                    Text(stringResource(mensaje.titulo), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(mensaje.explicacion))
                    Text("Motivo: ${l.motivo.codigo} · regla: ${l.regla}", style = MaterialTheme.typography.bodySmall)
                }
                null -> Unit
            }
        }
    }
}
