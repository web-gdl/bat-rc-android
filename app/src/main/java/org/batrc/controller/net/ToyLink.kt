package org.batrc.controller.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address

sealed interface LinkState {
    /** Todavía no hay ningún Wi-Fi conectado. */
    data object Searching : LinkState

    /** Hay Wi-Fi, pero no es el del vehículo. */
    data class OtherWifi(val localAddress: String?) : LinkState

    /** El teléfono está en el Wi-Fi del vehículo y el proceso está enlazado a él. */
    data class Connected(val network: Network) : LinkState
}

/**
 * Localiza el Wi-Fi del vehículo y enlaza todo el tráfico de la app a esa red.
 *
 * El Wi-Fi del juguete no tiene internet, así que Android moderno sigue usando
 * los datos móviles como red por defecto y los paquetes hacia 192.168.201.1
 * nunca llegarían. [ConnectivityManager.requestNetwork] pide la red Wi-Fi
 * aunque no tenga internet y [ConnectivityManager.bindProcessToNetwork] fuerza
 * a usarla.
 *
 * La red se reconoce por la dirección IP que el vehículo asigna al teléfono
 * (192.168.201.x), que se lee de [LinkProperties]. Así no hace falta leer el
 * nombre de la red (SSID) y la app no necesita permisos de ubicación ni de
 * dispositivos cercanos.
 */
class ToyLink(context: Context) {
    private val connectivity =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _state = MutableStateFlow<LinkState>(LinkState.Searching)
    val state: StateFlow<LinkState> = _state.asStateFlow()

    private var registered = false
    private var toyNetwork: Network? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            evaluate(network, connectivity.getLinkProperties(network))
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            evaluate(network, linkProperties)
        }

        override fun onLost(network: Network) {
            lost(network)
        }
    }

    @Synchronized
    fun start() {
        if (registered) return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivity.requestNetwork(request, callback)
        } catch (e: SecurityException) {
            // Android 6.0.0 exigía un permiso especial para requestNetwork.
            connectivity.registerNetworkCallback(request, callback)
        }
        registered = true
    }

    @Synchronized
    fun stop() {
        if (!registered) return
        registered = false
        try {
            connectivity.unregisterNetworkCallback(callback)
        } catch (e: IllegalArgumentException) {
            // Ya no estaba registrado.
        }
        connectivity.bindProcessToNetwork(null)
        toyNetwork = null
        _state.value = LinkState.Searching
    }

    @Synchronized
    private fun evaluate(network: Network, properties: LinkProperties?) {
        if (!registered) return
        val addresses = properties?.linkAddresses.orEmpty()
            .map { it.address }
            .filterIsInstance<Inet4Address>()
        val address = addresses.firstOrNull { isToySubnet(it.address) } ?: addresses.firstOrNull()

        if (address != null && isToySubnet(address.address)) {
            if (toyNetwork != network) {
                toyNetwork = network
                connectivity.bindProcessToNetwork(network)
                _state.value = LinkState.Connected(network)
            }
        } else if (toyNetwork == null || toyNetwork == network) {
            if (toyNetwork != null) {
                toyNetwork = null
                connectivity.bindProcessToNetwork(null)
            }
            // Sin dirección IPv4 todavía: el Wi-Fi sigue negociando.
            _state.value = if (address == null) LinkState.Searching else LinkState.OtherWifi(address.hostAddress)
        }
    }

    @Synchronized
    private fun lost(network: Network) {
        if (!registered) return
        if (toyNetwork == network) {
            toyNetwork = null
            connectivity.bindProcessToNetwork(null)
            _state.value = LinkState.Searching
        } else if (toyNetwork == null) {
            _state.value = LinkState.Searching
        }
    }

    private fun isToySubnet(bytes: ByteArray): Boolean =
        bytes.size == 4 &&
            bytes[0] == 192.toByte() &&
            bytes[1] == 168.toByte() &&
            bytes[2] == 201.toByte()
}
