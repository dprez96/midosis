package cl.midosis.paciente.alarmas

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * Qué del teléfono puede impedir que suene una alarma (HU-24). La aplicación nunca debe
 * aparentar estar activa sin estarlo (CU-P-03): lo que falte se explica en lenguaje llano y con
 * el atajo al ajuste que lo resuelve.
 */
data class Diagnostico(
    val notificaciones: Boolean,
    val alarmasExactas: Boolean,
    /** Si el teléfono exime a MiDosis del ahorro de batería. */
    val fueraDelAhorroDeBateria: Boolean,
    val fabricante: String,
) {
    /**
     * Sin notificaciones o sin alarmas exactas la alarma no suena: no están garantizadas. El
     * ahorro de batería no las bloquea, porque setAlarmClock sale del ahorro profundo (ADR-015),
     * pero algunos fabricantes cierran las aplicaciones por su cuenta: se advierte.
     */
    val alarmasGarantizadas: Boolean get() = notificaciones && alarmasExactas

    val todoEnOrden: Boolean get() = alarmasGarantizadas && fueraDelAhorroDeBateria

    /** Consejo propio del fabricante, que suele cerrar aplicaciones aunque estén fuera del ahorro. */
    val consejoDelFabricante: String?
        get() = when (fabricante.lowercase()) {
            "samsung" ->
                "En Samsung, revisa Ajustes › Batería › Límites de uso en segundo plano: MiDosis no debe estar en «Aplicaciones en suspensión profunda»."
            "xiaomi", "redmi", "poco" ->
                "En Xiaomi, abre la información de MiDosis › Ahorro de batería y elige «Sin restricciones»."
            "huawei", "honor" ->
                "En Huawei, abre Ajustes › Batería › Inicio de aplicaciones y deja que MiDosis se gestione manualmente."
            else -> null
        }

    companion object {
        fun del(contexto: Context): Diagnostico {
            val energia = contexto.getSystemService(PowerManager::class.java)
            return Diagnostico(
                notificaciones = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
                alarmasExactas = ProgramadorDeAlarmas(contexto).puedeProgramarExactas(),
                fueraDelAhorroDeBateria = energia.isIgnoringBatteryOptimizations(contexto.packageName),
                fabricante = Build.MANUFACTURER.orEmpty(),
            )
        }
    }
}
