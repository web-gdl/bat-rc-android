package org.batrc.controller.protocol

/**
 * Construye el texto de cada datagrama de control exactamente igual que
 * `AndroidMain.getSendCmd()` en la app original.
 *
 * Formato: tokens hexadecimales en ASCII separados por `_`.
 *
 *     [hasta 3 comandos ID_VALOR] 50_F0 [relleno 00_00] LATIDO
 *
 * - Acelerador (41) y dirección (31) son persistentes: se repiten en cada
 *   paquete mientras tengan valor.
 * - El resto de comandos se envía una sola vez y se borra.
 * - Si hay más de 3 comandos pendientes, los que no caben esperan al
 *   siguiente paquete, por orden de prioridad.
 * - LATIDO vale `01` en uno de cada diez paquetes y `00` en los demás.
 *
 * Esta clase no depende de Android, así que se puede probar en la JVM.
 */
class CommandEncoder {
    private var throttle = ""
    private var steering = ""
    private var calibration = ""
    private var led = ""
    private var smoke = ""
    private var shape = ""
    private var gunUpDown = ""
    private var voice = ""
    private var rotate = false
    private var beat = 0

    /** Vuelve al estado inicial de la app original (sin comandos pendientes). */
    @Synchronized
    fun reset() {
        throttle = ""
        steering = ""
        calibration = ""
        led = ""
        smoke = ""
        shape = ""
        gunUpDown = ""
        voice = ""
        rotate = false
    }

    /** [position] va de 0 a 200; 100 es neutro. Equivale a `SetForwadBack`. */
    @Synchronized
    fun setThrottle(position: Int) {
        throttle = axis("41", position)
    }

    /** [position] va de 0 a 200; 100 es centro. Equivale a `SetLeftRight`. */
    @Synchronized
    fun setSteering(position: Int) {
        steering = axis("31", position)
    }

    /** Parada explícita del motor. Equivale a `SetForwadBackStopTouch`. */
    @Synchronized
    fun stopThrottle() {
        throttle = "41_80"
    }

    /** Centrado explícito de la dirección. Equivale a `SetLeftRightStopTouch`. */
    @Synchronized
    fun centerSteering() {
        steering = "31_80"
    }

    @Synchronized
    fun setHeadlights(on: Boolean) {
        led = if (on) "A1_10" else "A1_01"
    }

    @Synchronized
    fun setSmoke(on: Boolean) {
        smoke = if (on) "91_10" else "91_01"
    }

    /** Conmuta la forma de la carrocería (suspensión). Equivale a `SetBodyShapeOn`. */
    @Synchronized
    fun toggleBodyShape() {
        shape = "21_64"
    }

    /** Sube o baja el cañón. Equivale a `SetGunUpDownOn`. */
    @Synchronized
    fun toggleGunUpDown() {
        gunUpDown = "21_E4"
    }

    /** Un pulso de rotación de la ametralladora. Equivale a `SetGunRotationOn`. */
    @Synchronized
    fun rotateGun() {
        rotate = true
    }

    /** Reproduce el sonido [number] (a partir de 1). Equivale a `SetVoice`. */
    @Synchronized
    fun playSound(number: Int) {
        voice = "01_" + Integer.toHexString(number - 1)
    }

    /** Ajuste de la dirección: -1, 0 o +1. Equivale a `SetCalibration`. */
    @Synchronized
    fun calibrate(direction: Int) {
        when (direction) {
            0 -> calibration = "B1_00"
            1 -> calibration = "B1_81"
            -1 -> calibration = "B1_01"
        }
    }

    /** Devuelve el texto del siguiente datagrama y consume los comandos de un solo uso. */
    @Synchronized
    fun next(): String {
        val parts = ArrayList<String>(MAX_COMMANDS)

        if (throttle.isNotEmpty()) parts += throttle
        if (steering.isNotEmpty() && parts.size < MAX_COMMANDS) parts += steering
        if (calibration.isNotEmpty() && parts.size < MAX_COMMANDS) {
            parts += calibration
            calibration = ""
        }
        if (led.isNotEmpty() && parts.size < MAX_COMMANDS) {
            parts += led
            led = ""
        }
        if (smoke.isNotEmpty() && parts.size < MAX_COMMANDS) {
            parts += smoke
            smoke = ""
        }
        if (shape.isNotEmpty() && parts.size < MAX_COMMANDS) {
            parts += shape
            shape = ""
        }
        if (gunUpDown.isNotEmpty() && parts.size < MAX_COMMANDS) {
            parts += gunUpDown
            gunUpDown = ""
        }
        if (voice.isNotEmpty() && parts.size < MAX_COMMANDS) {
            parts += voice
            voice = ""
        }
        if (rotate && parts.size < MAX_COMMANDS) {
            parts += "11_E4"
            rotate = false
        }

        beat = (beat + 1) % 10
        val beatToken = if (beat == 0) "01" else "00"

        if (parts.isEmpty()) return "41_80_31_80_50_F0_00_00_$beatToken"

        val text = StringBuilder(parts.joinToString("_")).append("_50_F0")
        repeat(MAX_COMMANDS - parts.size) { text.append("_00_00") }
        return text.append('_').append(beatToken).toString()
    }

    private fun axis(id: String, position: Int): String {
        val p = position.coerceIn(0, 200)
        return when {
            p < 100 -> id + "_" + Integer.toHexString(100 - p)
            p > 100 -> id + "_" + Integer.toHexString(p - 100 + 128)
            else -> ""
        }
    }

    private companion object {
        const val MAX_COMMANDS = 3
    }
}
