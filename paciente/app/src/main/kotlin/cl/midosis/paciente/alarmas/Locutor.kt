package cl.midosis.paciente.alarmas

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * La locución de la toma (HU-22). Usa la síntesis de voz del sistema, que funciona sin conexión
 * cuando la voz en español está instalada en el teléfono.
 *
 * La preferencia se guarda como un archivo vacío en la carpeta de la aplicación: así la borra
 * también la supresión de datos (HU-25), que vacía esa carpeta entera.
 */
object Locutor {

    /** Español de Chile si está; si no, cualquier español instalado. */
    private val IDIOMAS = listOf(Locale.forLanguageTag("es-CL"), Locale.forLanguageTag("es-419"), Locale.forLanguageTag("es"))

    /** Deja que suene primero la alarma y que el paciente alcance a tomar el teléfono. */
    private const val ESPERA_MS = 2_000L

    /** La recepción de la alarma no puede quedar abierta indefinidamente. */
    private const val LIMITE_MS = 45_000L

    private fun archivo(contexto: Context) = File(contexto.noBackupFilesDir, "locucion-activada")

    fun activada(contexto: Context): Boolean = archivo(contexto).exists()

    fun activar(contexto: Context, activa: Boolean) {
        val f = archivo(contexto)
        if (activa) f.createNewFile() else f.delete()
    }

    /** Lee [texto] en voz alta y llama a [alTerminar] al acabar, al fallar o al vencer el plazo. */
    fun hablar(contexto: Context, texto: String, alTerminar: () -> Unit = {}) {
        val principal = Handler(Looper.getMainLooper())
        val terminado = AtomicBoolean(false)
        var voz: TextToSpeech? = null

        fun fin() {
            if (terminado.compareAndSet(false, true)) {
                voz?.shutdown()
                alTerminar()
            }
        }

        principal.postDelayed(::fin, LIMITE_MS)
        voz = TextToSpeech(contexto.applicationContext) { estado ->
            val tts = voz
            if (estado != TextToSpeech.SUCCESS || tts == null || !elegirIdioma(tts)) {
                fin()
                return@TextToSpeech
            }
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) = fin()

                @Deprecated("Requerido por la API anterior a la 21")
                override fun onError(id: String?) = fin()
                override fun onError(id: String?, codigo: Int) = fin()
            })
            principal.postDelayed({ tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "toma") }, ESPERA_MS)
        }
    }

    /** Si el teléfono tiene una voz en español lista para usar. Responde en el hilo principal. */
    fun comprobarVoz(contexto: Context, alSaber: (Boolean) -> Unit) {
        var voz: TextToSpeech? = null
        voz = TextToSpeech(contexto.applicationContext) { estado ->
            val tts = voz
            val hay = estado == TextToSpeech.SUCCESS && tts != null && elegirIdioma(tts)
            tts?.shutdown()
            alSaber(hay)
        }
    }

    private fun elegirIdioma(tts: TextToSpeech): Boolean = IDIOMAS.any { idioma ->
        tts.isLanguageAvailable(idioma) >= TextToSpeech.LANG_AVAILABLE &&
            tts.setLanguage(idioma) >= TextToSpeech.LANG_AVAILABLE
    }
}
