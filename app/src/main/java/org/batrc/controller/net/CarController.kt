package org.batrc.controller.net

import android.net.Network
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.batrc.controller.protocol.CommandEncoder
import org.batrc.controller.protocol.Protocol
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Envía el estado de los mandos al vehículo por UDP cada 100 ms,
 * igual que el bucle `socketRun` de la app original.
 */
class CarController {
    val encoder = CommandEncoder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var network: Network? = null

    private val _lastPacket = MutableStateFlow("")
    /** Último datagrama enviado, para la vista de diagnóstico. */
    val lastPacket: StateFlow<String> = _lastPacket.asStateFlow()

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError.asStateFlow()

    @Synchronized
    fun start(target: Network) {
        if (job?.isActive == true && network == target) return
        job?.cancel()
        encoder.reset()
        network = target
        job = scope.launch { sendLoop(target) }
    }

    /**
     * Detiene el envío. Antes manda durante unos instantes la orden de parar
     * el motor, centrar la dirección y apagar el humo, para no dejar el
     * vehículo en marcha si la app pasa a segundo plano.
     */
    @Synchronized
    fun stop() {
        val running = job ?: return
        job = null
        network = null
        encoder.stopThrottle()
        encoder.centerSteering()
        encoder.setSmoke(false)
        scope.launch {
            delay(STOP_FLUSH_MS)
            running.cancel()
        }
    }

    private suspend fun sendLoop(target: Network) {
        val address = InetAddress.getByAddress(TOY_ADDRESS)
        var socket: DatagramSocket? = null
        var nextTick = SystemClock.elapsedRealtime()
        try {
            while (currentCoroutineContext().isActive) {
                try {
                    val active = socket ?: openSocket(target).also { socket = it }
                    val text = encoder.next()
                    val payload = text.toByteArray(Charsets.US_ASCII)
                    active.send(DatagramPacket(payload, payload.size, address, Protocol.CONTROL_PORT))
                    _lastPacket.value = text
                    _sendError.value = null
                } catch (e: IOException) {
                    _sendError.value = e.message ?: e.javaClass.simpleName
                    socket?.close()
                    socket = null
                }

                nextTick += Protocol.PERIOD_MS
                val wait = nextTick - SystemClock.elapsedRealtime()
                if (wait > 0) {
                    delay(wait)
                } else {
                    nextTick = SystemClock.elapsedRealtime()
                    delay(1) // punto de cancelación
                }
            }
        } finally {
            socket?.close()
        }
    }

    /** Crea un socket atado al Wi-Fi del vehículo, aunque cambie la red por defecto. */
    private fun openSocket(target: Network): DatagramSocket {
        val created = DatagramSocket()
        try {
            target.bindSocket(created)
        } catch (e: IOException) {
            created.close()
            throw e
        }
        return created
    }

    private companion object {
        const val STOP_FLUSH_MS = 350L
        val TOY_ADDRESS = byteArrayOf(192.toByte(), 168.toByte(), 201.toByte(), 1)
    }
}
