package cl.midosis.paciente.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Los colores de la aplicación, con contraste WCAG 2.1 AAA (RNF-19, criterio 1.4.6): todo texto
 * llega al menos a 7:1 contra su fondo. Los de Material por omisión se quedan en 6,4:1 (primario
 * con blanco) y 6,4:1 (error sobre la superficie), suficientes para AA pero no para AAA.
 *
 * Solo se cambian los colores que llevan texto; el resto queda como en Material, que ya pasa
 * 7:1 (texto sobre la superficie, 9,1:1 el secundario y 7,2:1 sobre las tarjetas).
 */
private val COLORES = lightColorScheme(
    // Con blanco encima: 9,3:1. Sobre el contenedor secundario: 7,1:1.
    primary = Color(0xFF4F378B),
    onPrimary = Color.White,
    // Sobre la superficie: 8,9:1; sobre su contenedor: 7,2:1.
    error = Color(0xFF8C1D18),
    onError = Color.White,
    onErrorContainer = Color(0xFF410E0B),
)

@Composable
fun TemaMiDosis(contenido: @Composable () -> Unit) {
    MaterialTheme(colorScheme = COLORES, content = contenido)
}
