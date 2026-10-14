package cl.midosis.paciente.codigo

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Busca un QR en cada cuadro de la cámara con ZXing, en el teléfono. Solo usa la luminancia
 * (el plano Y), que es lo que necesita un código en blanco y negro, y no guarda imágenes.
 *
 * Entrega el primer texto que lee y se detiene, para no verificar el mismo código diez veces
 * por segundo; [reanudar] lo vuelve a activar.
 */
class AnalizadorDeQr(private val alLeer: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val lector = QRCodeReader()
    private val pistas = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
    )
    private val activo = AtomicBoolean(true)

    fun reanudar() = activo.set(true)

    override fun analyze(imagen: ImageProxy) {
        imagen.use {
            if (!activo.get()) return
            val plano = it.planes[0]
            val datos = ByteArray(plano.buffer.remaining()).also { b -> plano.buffer.get(b) }
            val fuente = PlanarYUVLuminanceSource(datos, plano.rowStride, it.height, 0, 0, it.width, it.height, false)
            val texto = try {
                lector.decode(BinaryBitmap(HybridBinarizer(fuente)), pistas).text
            } catch (e: NotFoundException) {
                null
            } catch (e: ReaderException) {
                null
            } finally {
                lector.reset()
            }
            if (texto != null && activo.compareAndSet(true, false)) alLeer(texto)
        }
    }
}
