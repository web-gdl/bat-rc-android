package org.batrc.controller.net

import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.batrc.controller.protocol.Protocol
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Llamadas HTTP (CGI) al módulo Wi-Fi del vehículo. */
object ToyHttp {

    /** Hace un GET a [path] a través de [network]. Devuelve null si falla. */
    suspend fun get(network: Network, path: String): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = network.openConnection(URL(Protocol.HTTP_BASE + path)) as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 5000
            connection.useCaches = false
            connection.inputStream.bufferedReader().use { it.readText() }.trim()
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
