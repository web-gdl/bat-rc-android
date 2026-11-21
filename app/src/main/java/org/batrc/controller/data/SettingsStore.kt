package org.batrc.controller.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    /** Invierte adelante / atrás. */
    val invertThrottle: Boolean = false,
    /** Invierte izquierda / derecha. */
    val invertSteering: Boolean = false,
    /** Acelerador a la derecha y dirección a la izquierda. */
    val swapSides: Boolean = false,
    /** Potencia máxima del motor, de 30 a 100 %. */
    val maxPower: Int = 100,
    /** Fuerza RTP sobre TCP para el vídeo (más robusto, algo más de retardo). */
    val videoOverTcp: Boolean = false,
    val haptics: Boolean = true,
    /** Muestra el último datagrama enviado. */
    val showDiagnostics: Boolean = false,
    /** Fogonazos, estelas, explosiones y sacudida al disparar. */
    val visualEffects: Boolean = true,
    /** Sonidos de motor y disparos en el teléfono. */
    val phoneSounds: Boolean = true,
    /** Mira en el centro de la imagen. */
    val showCrosshair: Boolean = true,
    /** Blancos virtuales sobre el vídeo. */
    val targetMode: Boolean = false,
)

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_state.value)
        _state.value = updated
        prefs.edit()
            .putBoolean(KEY_INVERT_THROTTLE, updated.invertThrottle)
            .putBoolean(KEY_INVERT_STEERING, updated.invertSteering)
            .putBoolean(KEY_SWAP_SIDES, updated.swapSides)
            .putInt(KEY_MAX_POWER, updated.maxPower)
            .putBoolean(KEY_VIDEO_TCP, updated.videoOverTcp)
            .putBoolean(KEY_HAPTICS, updated.haptics)
            .putBoolean(KEY_DIAGNOSTICS, updated.showDiagnostics)
            .putBoolean(KEY_VISUAL_EFFECTS, updated.visualEffects)
            .putBoolean(KEY_PHONE_SOUNDS, updated.phoneSounds)
            .putBoolean(KEY_CROSSHAIR, updated.showCrosshair)
            .putBoolean(KEY_TARGET_MODE, updated.targetMode)
            .apply()
    }

    private fun load(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            invertThrottle = prefs.getBoolean(KEY_INVERT_THROTTLE, defaults.invertThrottle),
            invertSteering = prefs.getBoolean(KEY_INVERT_STEERING, defaults.invertSteering),
            swapSides = prefs.getBoolean(KEY_SWAP_SIDES, defaults.swapSides),
            maxPower = prefs.getInt(KEY_MAX_POWER, defaults.maxPower).coerceIn(30, 100),
            videoOverTcp = prefs.getBoolean(KEY_VIDEO_TCP, defaults.videoOverTcp),
            haptics = prefs.getBoolean(KEY_HAPTICS, defaults.haptics),
            showDiagnostics = prefs.getBoolean(KEY_DIAGNOSTICS, defaults.showDiagnostics),
            visualEffects = prefs.getBoolean(KEY_VISUAL_EFFECTS, defaults.visualEffects),
            phoneSounds = prefs.getBoolean(KEY_PHONE_SOUNDS, defaults.phoneSounds),
            showCrosshair = prefs.getBoolean(KEY_CROSSHAIR, defaults.showCrosshair),
            targetMode = prefs.getBoolean(KEY_TARGET_MODE, defaults.targetMode),
        )
    }

    private companion object {
        const val KEY_INVERT_THROTTLE = "invert_throttle"
        const val KEY_INVERT_STEERING = "invert_steering"
        const val KEY_SWAP_SIDES = "swap_sides"
        const val KEY_MAX_POWER = "max_power"
        const val KEY_VIDEO_TCP = "video_tcp"
        const val KEY_HAPTICS = "haptics"
        const val KEY_DIAGNOSTICS = "diagnostics"
        const val KEY_VISUAL_EFFECTS = "visual_effects"
        const val KEY_PHONE_SOUNDS = "phone_sounds"
        const val KEY_CROSSHAIR = "crosshair"
        const val KEY_TARGET_MODE = "target_mode"
    }
}
