package app.sonyremote.tv

import android.content.Context
import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

enum class PowerState { ON, STANDBY }

/**
 * Power handling over Wi-Fi for the KDL-42W655A.
 *
 * The IRCC "toggle" code is ignored by this TV, it only obeys the discrete POWER_OFF code,
 * and in standby it only wakes up with a Wake-on-LAN magic packet. So the power key asks the
 * TV for its state (getPowerStatus); when the TV cannot say, no answer on the network means
 * standby, and otherwise we trust the last thing we sent.
 */
class TvPower(
    private val context: Context,
    private val http: OkHttpClient,
) {
    /** ip -> what we last asked the TV to do. */
    private val lastAction = mutableMapOf<String, PowerState>()

    /** Power key: turn the TV off when it is on, wake it when it is in standby. */
    suspend fun toggle(settings: TvSettings): SendResult = withContext(Dispatchers.IO) {
        val network = WifiNetworkHelper(context).wifiNetwork()
        if (stateOf(settings, network) == PowerState.ON) {
            val result = SonyTvClient(pinned(network), settings.ip, settings.psk).send(SonyKey.POWER_OFF)
            if (result.ok) lastAction[settings.ip] = PowerState.STANDBY
            result
        } else {
            wake(settings, network)
        }
    }

    /** Called when the app opens: wakes the TV only if it is in standby. Null = nothing to do. */
    suspend fun wakeIfStandby(settings: TvSettings): SendResult? = withContext(Dispatchers.IO) {
        val network = WifiNetworkHelper(context).wifiNetwork()
            ?: return@withContext null
        if (stateOf(settings, network) == PowerState.ON) null else wake(settings, network)
    }

    private fun pinned(network: Network?): OkHttpClient =
        if (network != null) http.newBuilder().socketFactory(network.socketFactory).build() else http

    private fun stateOf(settings: TvSettings, network: Network?): PowerState {
        queryStatus(settings, network)?.let { return it }
        if (!reachable(settings.ip, network)) return PowerState.STANDBY
        return lastAction[settings.ip] ?: PowerState.ON
    }

    private fun queryStatus(settings: TvSettings, network: Network?): PowerState? = runCatching {
        val body = """{"method":"getPowerStatus","id":50,"params":[],"version":"1.0"}"""
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("http://${settings.ip}/sony/system")
            .apply { if (settings.psk.isNotBlank()) addHeader("X-Auth-PSK", settings.psk) }
            .post(body)
            .build()
        pinned(network).newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val status = JSONObject(text).getJSONArray("result").getJSONObject(0).getString("status")
            when (status) {
                "active" -> PowerState.ON
                "standby" -> PowerState.STANDBY
                else -> null
            }
        }
    }.getOrNull()

    private fun reachable(ip: String, network: Network?): Boolean = runCatching {
        val socket = network?.socketFactory?.createSocket() ?: Socket()
        socket.use { it.connect(InetSocketAddress(ip, 80), 1500) }
        true
    }.getOrDefault(false)

    private fun wake(settings: TvSettings, network: Network?): SendResult {
        val mac = parseMac(settings.mac)
            ?: return SendResult(false, "informe o MAC da TV na engrenagem")
        sendMagicPacket(mac, settings.ip, network)
        lastAction[settings.ip] = PowerState.ON
        return SendResult(true, "Wake-on-LAN")
    }

    private fun sendMagicPacket(mac: ByteArray, tvIp: String, network: Network?) {
        val packet = ByteArray(6) { 0xFF.toByte() } + ByteArray(16 * 6) { mac[it % 6] }
        val targets = listOf("255.255.255.255", tvIp.substringBeforeLast('.') + ".255")
        DatagramSocket().use { socket ->
            network?.bindSocket(socket)
            socket.broadcast = true
            for (target in targets) {
                for (port in intArrayOf(9, 7)) {
                    runCatching {
                        socket.send(DatagramPacket(packet, packet.size, InetAddress.getByName(target), port))
                    }
                }
            }
        }
    }

    companion object {
        /** Accepts "A4:17:31:A6:52:42", "a4-17-31-a6-52-42" or plain hex. */
        fun parseMac(text: String): ByteArray? {
            val hex = text.filter { it.isLetterOrDigit() }
            if (hex.length != 12 || !hex.all { it in "0123456789abcdefABCDEF" }) return null
            return ByteArray(6) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }

        /** Sony UPnP identifiers usually end with the MAC: uuid:00000000-0000-1010-8000-a41731a65242. */
        fun macFromUdn(udn: String): String {
            val tail = udn.substringAfterLast('-')
            val bytes = parseMac(tail) ?: return ""
            return bytes.joinToString(":") { "%02X".format(it) }
        }
    }
}
