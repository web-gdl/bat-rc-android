package org.batrc.controller

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.batrc.controller.data.SettingsStore
import org.batrc.controller.fx.FxController
import org.batrc.controller.fx.FxEvent
import org.batrc.controller.fx.FxHaptics
import org.batrc.controller.fx.FxSound
import org.batrc.controller.net.CarController
import org.batrc.controller.net.LinkState
import org.batrc.controller.net.ToyHttp
import org.batrc.controller.net.ToyLink
import org.batrc.controller.protocol.Axis
import org.batrc.controller.protocol.Protocol
import org.batrc.controller.video.VideoStatus

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val settings = SettingsStore(application)

    private val link = ToyLink(application)
    val linkState: StateFlow<LinkState> = link.state

    private val car = CarController()
    val lastPacket: StateFlow<String> = car.lastPacket
    val sendError: StateFlow<String?> = car.sendError

    private val _foreground = MutableStateFlow(false)
    /** Verdadero mientras la app está visible. El vídeo y el control solo funcionan entonces. */
    val foreground: StateFlow<Boolean> = _foreground.asStateFlow()

    private val _headlights = MutableStateFlow(false)
    val headlights: StateFlow<Boolean> = _headlights.asStateFlow()

    private val _smoke = MutableStateFlow(false)
    val smoke: StateFlow<Boolean> = _smoke.asStateFlow()

    private val _videoStatus = MutableStateFlow<VideoStatus>(VideoStatus.Idle)
    val videoStatus: StateFlow<VideoStatus> = _videoStatus.asStateFlow()

    private val _deviceInfo = MutableStateFlow<String?>(null)
    /** Respuesta en bruto de getDeviceInformation.cgi (incluye la versión de firmware). */
    val deviceInfo: StateFlow<String?> = _deviceInfo.asStateFlow()

    private var engineSound = Protocol.SOUND_ENGINE_FIRST

    // --- Efectos ------------------------------------------------------------

    /** Estado de los efectos visuales (fogonazos, misiles, mira, blancos). */
    val fx = FxController()
    private val sound = FxSound(application)
    private val haptics = FxHaptics(application)

    private val _cannonUp = MutableStateFlow(false)
    /** Posición supuesta del cañón; la app no recibe el estado real del vehículo. */
    val cannonUp: StateFlow<Boolean> = _cannonUp.asStateFlow()

    private val _demo = MutableStateFlow<Double?>(null)
    /** Instante congelado de los efectos en el modo demostración (capturas), o null. */
    val demo: StateFlow<Double?> = _demo.asStateFlow()

    init {
        viewModelScope.launch {
            link.state.collect { applyState() }
        }
        viewModelScope.launch {
            settings.state.collect { config ->
                sound.enabled = config.phoneSounds
                haptics.enabled = config.haptics
                fx.targetMode = config.targetMode && config.visualEffects
            }
        }
        fx.listener = { event ->
            if (event == FxEvent.TARGET_DESTROYED) {
                sound.play(FxSound.Clip.EXPLOSION, 0.9f)
                haptics.shot()
            }
        }
    }

    /** Reloj común con el de la capa de efectos (`withFrameNanos` usa System.nanoTime). */
    private fun now(): Double = System.nanoTime() / 1_000_000_000.0

    fun onForeground(visible: Boolean) {
        _foreground.value = visible
        if (visible) {
            link.start()
            applyState()
        } else {
            car.stop()
            link.stop()
            resetToggles()
            stopMachineGun()
        }
    }

    private fun applyState() {
        val state = link.state.value
        if (_foreground.value && state is LinkState.Connected) {
            car.start(state.network)
            viewModelScope.launch {
                // Igual que la app original al arrancar el vídeo.
                ToyHttp.get(state.network, Protocol.CGI_AUTO_WHITE_BALANCE)
                _deviceInfo.value = ToyHttp.get(state.network, Protocol.CGI_DEVICE_INFO)
            }
        } else {
            car.stop()
            resetToggles()
        }
    }

    private fun resetToggles() {
        _headlights.value = false
        _smoke.value = false
        _deviceInfo.value = null
    }

    // --- Conducción -------------------------------------------------------

    /** [value] va de -1 (atrás) a 1 (adelante). */
    fun setThrottle(value: Float) {
        val config = settings.state.value
        val signed = if (config.invertThrottle) -value else value
        val shaped = Axis.throttleCurve(signed) * (config.maxPower / 100f)
        car.encoder.setThrottle(Axis.toWire(shaped))
        throttleInput = value
        fx.setAim(steeringInput, throttleInput)
    }

    private var throttleInput = 0f
    private var steeringInput = 0f

    /** [value] va de -1 (izquierda) a 1 (derecha). */
    fun setSteering(value: Float) {
        val config = settings.state.value
        val signed = if (config.invertSteering) -value else value
        car.encoder.setSteering(Axis.toWire(signed))
        steeringInput = value
        fx.setAim(steeringInput, throttleInput)
    }

    /** Ajuste fino de la dirección: -1, 0 (restablecer) o +1. */
    fun calibrateSteering(direction: Int) = car.encoder.calibrate(direction)

    // --- Funciones del vehículo -------------------------------------------

    fun toggleHeadlights() {
        val on = !_headlights.value
        _headlights.value = on
        car.encoder.setHeadlights(on)
    }

    fun toggleSmoke() {
        val on = !_smoke.value
        _smoke.value = on
        car.encoder.setSmoke(on)
    }

    fun toggleSuspension() = car.encoder.toggleBodyShape()

    fun toggleCannonLift() {
        car.encoder.toggleGunUpDown()
        val up = !_cannonUp.value
        _cannonUp.value = up
        fx.cannonUp = up
    }

    /**
     * Ametralladora, al pulsar: un pulso de rotación (`WeaponActivated(0)`) y
     * fogonazos, sonido y vibración mientras se mantenga pulsado.
     */
    fun startMachineGun() {
        car.encoder.rotateGun()
        fx.startGun(now())
        sound.startGun()
        haptics.startGun()
    }

    /** Al soltar la ametralladora (`WeaponDeactivated(0)`). */
    fun stopMachineGun() {
        fx.stopGun()
        sound.stopGun()
        haptics.stopGun()
    }

    /** Cañón: sonido 102 en el vehículo (`Main.FireCannon`) y efectos. */
    fun fireCannon() {
        car.encoder.playSound(Protocol.SOUND_CANNON)
        fx.fireCannon(now())
        sound.play(FxSound.Clip.CANNON)
        haptics.shot()
    }

    /** Misiles: sonido 101 en el vehículo (`Main.FireMissle`) y efectos. */
    fun fireMissiles() {
        car.encoder.playSound(Protocol.SOUND_MISSILE)
        fx.fireMissile(now())
        sound.play(FxSound.Clip.MISSILE)
        haptics.shot()
        viewModelScope.launch {
            // El misil tarda 2,0 s en llegar, como `RocketFX.lifeTime`.
            delay((FxController.ROCKET_LIFE * 1000).toLong())
            sound.play(FxSound.Clip.EXPLOSION, 0.8f)
        }
    }

    /** Acelerón: recorre los sonidos de motor 1, 2 y 3 del vehículo. */
    fun revEngine() {
        car.encoder.playSound(engineSound)
        engineSound = if (engineSound >= Protocol.SOUND_ENGINE_LAST) Protocol.SOUND_ENGINE_FIRST else engineSound + 1
        sound.play(FxSound.Clip.ENGINE)
    }

    /**
     * Modo demostración para generar capturas sin vehículo: dispara los
     * efectos de [scene] y congela la imagen en el segundo [at].
     */
    fun startDemo(scene: String, at: Double) {
        val frame = 1.0 / 60
        var time = 0.0
        fun runUntil(limit: Double) {
            while (time < limit) {
                fx.step(time)
                time += frame
            }
        }
        when (scene) {
            "gun" -> fx.startGun(0.0)
            "cannon" -> fx.fireCannon(0.0)
            "missiles" -> {
                fx.fireMissile(0.0)
                runUntil(0.45)
                fx.fireMissile(0.45)
            }
            "targets" -> {
                fx.targetMode = true
                runUntil(2.6)
                fx.targets.firstOrNull { it.alive }?.let { target ->
                    val aimX = ((target.at.x - FxController.CENTER.x) / FxController.AIM_RANGE_X).coerceIn(-1f, 1f)
                    val aimY = ((FxController.CENTER.y - target.at.y) / FxController.AIM_RANGE_Y).coerceIn(-1f, 1f)
                    fx.setAim(aimX, aimY)
                }
                runUntil(2.9)
                fx.fireMissile(time)
                runUntil(3.4)
                fx.startGun(time)
            }
        }
        runUntil(at)
        _demo.value = at
    }

    fun playSound(number: Int) = car.encoder.playSound(number)

    fun onVideoStatus(status: VideoStatus) {
        _videoStatus.value = status
    }

    override fun onCleared() {
        car.stop()
        link.stop()
        stopMachineGun()
        sound.release()
    }
}
