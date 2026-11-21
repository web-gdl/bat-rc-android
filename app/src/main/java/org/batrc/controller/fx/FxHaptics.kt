package org.batrc.controller.fx

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Vibración de los disparos, con el ritmo de la app original
 * (`FXController.*Vibrate`): un pulso por misil o cañonazo y, mientras la
 * ametralladora dispara, un pulso cada segundo.
 */
class FxHaptics(context: Context) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private val handler = Handler(Looper.getMainLooper())

    var enabled = true
        set(value) {
            field = value
            if (!value) stopGun()
        }

    private val gunPulse = object : Runnable {
        override fun run() {
            pulse(GUN_PULSE_MS)
            handler.postDelayed(this, GUN_INTERVAL_MS)
        }
    }

    fun shot() = pulse(SHOT_PULSE_MS)

    fun startGun() {
        if (!enabled) return
        handler.removeCallbacks(gunPulse)
        gunPulse.run()
    }

    fun stopGun() {
        handler.removeCallbacks(gunPulse)
    }

    fun pulse(milliseconds: Long) {
        val target = vibrator ?: return
        if (!enabled || !target.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            target.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            target.vibrate(milliseconds)
        }
    }

    private companion object {
        const val SHOT_PULSE_MS = 180L
        const val GUN_PULSE_MS = 90L
        const val GUN_INTERVAL_MS = 1000L
    }
}
