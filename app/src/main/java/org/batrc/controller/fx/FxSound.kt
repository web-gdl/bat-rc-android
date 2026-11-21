package org.batrc.controller.fx

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import org.batrc.controller.R

/** Sonidos de los efectos en el teléfono (además de los que reproduce el propio vehículo). */
class FxSound(context: Context) {

    enum class Clip(val resource: Int) {
        ENGINE(R.raw.fx_engine),
        MACHINE_GUN(R.raw.fx_machine_gun),
        CANNON(R.raw.fx_cannon),
        MISSILE(R.raw.fx_missile),
        EXPLOSION(R.raw.fx_explosion),
    }

    private val pool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids = Clip.entries.associateWith { pool.load(context.applicationContext, it.resource, 1) }
    private var gunStream = 0

    var enabled = true
        set(value) {
            field = value
            if (!value) stopGun()
        }

    fun play(clip: Clip, volume: Float = 1f) {
        if (!enabled) return
        pool.play(ids.getValue(clip), volume, volume, 1, 0, 1f)
    }

    /** La ametralladora suena en bucle mientras se mantiene pulsada. */
    fun startGun() {
        if (!enabled || gunStream != 0) return
        gunStream = pool.play(ids.getValue(Clip.MACHINE_GUN), 0.8f, 0.8f, 2, -1, 1f)
    }

    fun stopGun() {
        if (gunStream != 0) {
            pool.stop(gunStream)
            gunStream = 0
        }
    }

    fun release() {
        stopGun()
        pool.release()
    }
}
