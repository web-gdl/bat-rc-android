package org.batrc.controller.protocol

import kotlin.math.abs

/** Conversión de la posición de un mando (-1..1) a la escala 0..200 del protocolo. */
object Axis {
    private const val DEADZONE = 0.08f

    /** Ignora movimientos mínimos alrededor del centro y reescala el resto a -1..1. */
    fun deadzone(value: Float): Float {
        val magnitude = abs(value)
        if (magnitude < DEADZONE) return 0f
        val scaled = (magnitude - DEADZONE) / (1f - DEADZONE)
        return if (value < 0f) -scaled else scaled
    }

    /**
     * Curva del acelerador de la app original (`Manager.UpdateThrottle`):
     * suave hasta el 75 % del recorrido y más agresiva en el último cuarto.
     * Marcha atrás queda limitada a ~67 %, igual que en el original.
     */
    fun throttleCurve(value: Float): Float =
        if (value < 0.75f) value * 0.6666f else (value - 0.75f) * 2f + 0.5f

    /** `Main.SetForwadBack` / `Main.SetLeftRight`: truncar(valor × 100) + 100. */
    fun toWire(value: Float): Int = ((value.coerceIn(-1f, 1f) * 100f).toInt() + 100).coerceIn(0, 200)
}
