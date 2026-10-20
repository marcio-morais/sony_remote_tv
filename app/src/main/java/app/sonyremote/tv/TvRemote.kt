package app.sonyremote.tv

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

enum class Route { IR, WIFI }

/**
 * Sends a key over infrared or Wi-Fi depending on the chosen mode.
 * In AUTO mode IR is used when the phone has an emitter and the key has a SIRC code;
 * everything else goes over Wi-Fi.
 */
class TvRemote(
    private val context: Context,
    private val http: OkHttpClient,
    private val ir: SonyIrRemote,
) {
    val irAvailable: Boolean get() = ir.isAvailable

    fun routeFor(key: SonyKey, mode: TransportMode): Route = when (mode) {
        TransportMode.WIFI -> Route.WIFI
        TransportMode.IR -> Route.IR
        TransportMode.AUTO ->
            if (ir.isAvailable && key.sircCommand != null) Route.IR else Route.WIFI
    }

    suspend fun send(key: SonyKey, settings: TvSettings): SendResult =
        when (routeFor(key, settings.mode)) {
            Route.IR -> sendIr(key)
            Route.WIFI -> sendWifi(key, settings)
        }

    private suspend fun sendIr(key: SonyKey): SendResult = withContext(Dispatchers.Default) {
        val command = key.sircCommand
            ?: return@withContext SendResult(false, "sem código IR para esta tecla")
        if (!ir.isAvailable) return@withContext SendResult(false, "celular sem emissor IR")
        ir.send(command)
        SendResult(true, "IR")
    }

    private suspend fun sendWifi(key: SonyKey, settings: TvSettings): SendResult {
        // Pin the request to the Wi-Fi network so it still works when mobile data is on
        val network = WifiNetworkHelper(context).wifiNetwork()
        val client = if (network != null) {
            http.newBuilder().socketFactory(network.socketFactory).build()
        } else {
            http
        }
        return SonyTvClient(client, settings.ip, settings.psk).send(key)
    }
}
