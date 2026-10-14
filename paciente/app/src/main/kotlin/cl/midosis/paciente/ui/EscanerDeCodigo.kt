package cl.midosis.paciente.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import cl.midosis.paciente.codigo.AnalizadorDeQr
import java.util.concurrent.Executors

/**
 * La cámara buscando el QR del comprobante (HU-11). Pide el permiso explicando para qué; si el
 * paciente lo niega, la aplicación sigue funcionando y lo dice.
 */
@Composable
fun EscanerDeCodigo(alLeer: (String) -> Unit) {
    val contexto = LocalContext.current
    var permitido by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var negado by remember { mutableStateOf(false) }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        permitido = concedido
        negado = !concedido
    }

    if (!permitido) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Para leer el código del comprobante, MiDosis necesita usar la cámara.")
            Text(
                "La imagen se analiza en tu teléfono y no se guarda ni se envía.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (negado) Text("No diste el permiso. Puedes darlo desde los ajustes del teléfono.", color = MaterialTheme.colorScheme.error)
            Button(onClick = { pedir.launch(Manifest.permission.CAMERA) }) { Text("Permitir la cámara") }
        }
        return
    }

    val dueno = LocalLifecycleOwner.current
    val analizador = remember { AnalizadorDeQr { texto -> ContextCompat.getMainExecutor(contexto).execute { alLeer(texto) } } }
    val hilo = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { hilo.shutdown() } }

    AndroidView(
        modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
        factory = { ctx ->
            val vista = PreviewView(ctx)
            val proveedor = ProcessCameraProvider.getInstance(ctx)
            proveedor.addListener({
                val camara = proveedor.get()
                val vistaPrevia = Preview.Builder().build().also { it.surfaceProvider = vista.surfaceProvider }
                val analisis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(hilo, analizador) }
                camara.unbindAll()
                camara.bindToLifecycle(dueno, CameraSelector.DEFAULT_BACK_CAMERA, vistaPrevia, analisis)
            }, ContextCompat.getMainExecutor(ctx))
            vista
        },
        onRelease = { ProcessCameraProvider.getInstance(it.context).get().unbindAll() },
    )
}
