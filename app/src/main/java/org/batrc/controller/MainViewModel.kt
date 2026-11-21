package org.batrc.controller

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.batrc.controller.data.SettingsStore
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

    init {
        viewModelScope.launch {
            link.state.collect { applyState() }
        }
    }

    fun onForeground(visible: Boolean) {
        _foreground.value = visible
        if (visible) {
            link.start()
            applyState()
        } else {
            car.stop()
            link.stop()
            resetToggles()
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
    }

    /** [value] va de -1 (izquierda) a 1 (derecha). */
    fun setSteering(value: Float) {
        val config = settings.state.value
        val signed = if (config.invertSteering) -value else value
        car.encoder.setSteering(Axis.toWire(signed))
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

    fun toggleCannonLift() = car.encoder.toggleGunUpDown()

    /** Ametralladora: un pulso de rotación, como `WeaponActivated(0)`. */
    fun fireMachineGun() = car.encoder.rotateGun()

    /** Cañón: sonido 102, como `Main.FireCannon`. */
    fun fireCannon() = car.encoder.playSound(Protocol.SOUND_CANNON)

    /** Misiles: sonido 101, como `Main.FireMissle`. */
    fun fireMissiles() = car.encoder.playSound(Protocol.SOUND_MISSILE)

    /** Acelerón: recorre los sonidos de motor 1, 2 y 3. */
    fun revEngine() {
        car.encoder.playSound(engineSound)
        engineSound = if (engineSound >= Protocol.SOUND_ENGINE_LAST) Protocol.SOUND_ENGINE_FIRST else engineSound + 1
    }

    fun playSound(number: Int) = car.encoder.playSound(number)

    fun onVideoStatus(status: VideoStatus) {
        _videoStatus.value = status
    }

    override fun onCleared() {
        car.stop()
        link.stop()
    }
}
