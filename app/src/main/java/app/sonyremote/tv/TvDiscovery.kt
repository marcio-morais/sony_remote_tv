package app.sonyremote.tv

import android.content.Context
import android.net.Network
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.TimeUnit

data class TvDevice(val ip: String, val name: String, val model: String, val mac: String = "")

data class DiscoveryResult(val devices: List<TvDevice>, val onWifi: Boolean)

/**
 * Finds Sony TVs on the Wi-Fi: SSDP multicast (what the Sony app uses) plus a scan of the
 * local /24 for the UPnP description, as a backup for networks that block multicast.
 */
class TvDiscovery(
    private val context: Context,
    private val baseHttp: OkHttpClient,
) {
    suspend fun discover(): DiscoveryResult = withContext(Dispatchers.IO) {
        val helper = WifiNetworkHelper(context)
        val network = helper.wifiNetwork()
        val local = network?.let { helper.localIpv4(it) }
        if (network == null || local == null) {
            return@withContext DiscoveryResult(emptyList(), onWifi = false)
        }

        val http = baseHttp.newBuilder()
            .socketFactory(network.socketFactory)
            .connectTimeout(700, TimeUnit.MILLISECONDS)
            .readTimeout(1500, TimeUnit.MILLISECONDS)
            .build()

        val found = coroutineScope {
            val bySsdp = async { runCatching { ssdp(network, http) }.getOrDefault(emptyList()) }
            val byScan = async { scan(local, http) }
            bySsdp.await() + byScan.await()
        }
        DiscoveryResult(found.distinctBy { it.ip }, onWifi = true)
    }

    private fun ssdp(network: Network, http: OkHttpClient): List<TvDevice> {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("sonyremote-ssdp")
        lock.setReferenceCounted(false)
        lock.acquire()

        val found = LinkedHashMap<String, TvDevice>()
        val socket = DatagramSocket()
        try {
            network.bindSocket(socket)
            val group = InetAddress.getByName(SSDP_HOST)
            for (target in SSDP_TARGETS) {
                val message = ("M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: $SSDP_HOST:$SSDP_PORT\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\n" +
                    "ST: $target\r\n\r\n").toByteArray()
                socket.send(DatagramPacket(message, message.size, group, SSDP_PORT))
            }

            val deadline = System.currentTimeMillis() + SSDP_LISTEN_MS
            val buffer = ByteArray(2048)
            while (true) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) break
                socket.soTimeout = remaining.toInt()
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (timeout: SocketTimeoutException) {
                    break
                }

                val ip = packet.address.hostAddress ?: continue
                if (ip in found) continue
                val headers = parseHeaders(String(packet.data, 0, packet.length))
                val hint = "${headers["server"].orEmpty()} ${headers["st"].orEmpty()}"
                val location = headers["location"].orEmpty()
                // Only follow a description URL that points back at the responder itself
                val description = if (hostOf(location) == ip) fetch(http, location).orEmpty() else ""
                sonyDevice(ip, description, hint)?.let { found[ip] = it }
            }
        } finally {
            socket.close()
            if (lock.isHeld) lock.release()
        }
        return found.values.toList()
    }

    private suspend fun scan(local: Inet4Address, http: OkHttpClient): List<TvDevice> = coroutineScope {
        val probeHttp = http.newBuilder()
            .connectTimeout(600, TimeUnit.MILLISECONDS)
            .readTimeout(900, TimeUnit.MILLISECONDS)
            .build()
        val octets = local.address.map { it.toInt() and 0xFF }
        val prefix = "${octets[0]}.${octets[1]}.${octets[2]}"
        val gate = Semaphore(SCAN_PARALLELISM)

        (1..254)
            .filter { it != octets[3] }
            .map { host ->
                async {
                    gate.withPermit {
                        val ip = "$prefix.$host"
                        fetch(probeHttp, "http://$ip$SCAN_PATH")?.let { sonyDevice(ip, it) }
                    }
                }
            }
            .awaitAll()
            .filterNotNull()
    }

    private fun fetch(http: OkHttpClient, url: String): String? = runCatching {
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.isSuccessful) response.peekBody(MAX_BODY_BYTES).string() else null
        }
    }.getOrNull()

    private fun hostOf(location: String): String? =
        runCatching { URI(location).host }.getOrNull()

    private fun parseHeaders(text: String): Map<String, String> =
        text.lines().drop(1).mapNotNull { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) {
                null
            } else {
                line.substring(0, separator).trim().lowercase() to line.substring(separator + 1).trim()
            }
        }.toMap()

    private fun xmlField(text: String, tag: String): String =
        Regex("<$tag>([^<]*)</$tag>").find(text)?.groupValues?.get(1)?.trim().orEmpty()

    /** Returns the device when the UPnP description (or SSDP hint) says it is a Sony. */
    private fun sonyDevice(ip: String, description: String, hint: String = ""): TvDevice? {
        val manufacturer = xmlField(description, "manufacturer")
        if (!"$manufacturer $hint".contains("sony", ignoreCase = true)) return null
        return TvDevice(
            ip = ip,
            name = xmlField(description, "friendlyName").ifBlank { "Sony TV" },
            model = xmlField(description, "modelName"),
            mac = TvPower.macFromUdn(xmlField(description, "UDN")),
        )
    }

    private companion object {
        const val SSDP_HOST = "239.255.255.250"
        const val SSDP_PORT = 1900
        const val SSDP_LISTEN_MS = 3000L
        val SSDP_TARGETS = listOf(
            "urn:schemas-sony-com:service:IRCC:1",
            "urn:schemas-sony-com:service:ScalarWebAPI:1",
            "urn:schemas-upnp-org:device:MediaRenderer:1",
        )
        const val SCAN_PATH = ":52323/dmr.xml"
        const val SCAN_PARALLELISM = 48
        const val MAX_BODY_BYTES = 16_384L
    }
}
