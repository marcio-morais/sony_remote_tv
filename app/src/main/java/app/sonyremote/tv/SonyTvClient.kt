package app.sonyremote.tv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Sends IRCC commands to a Sony Bravia over Wi-Fi (HTTP SOAP).
 * Requires android:usesCleartextTraffic="true" because the TV speaks plain HTTP.
 */
class SonyTvClient(
    private val tvIp: String,
    private val preSharedKey: String = "0000",
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    suspend fun send(key: SonyKey): Boolean = withContext(Dispatchers.IO) {
        val body = envelope(key.irccCode).toRequestBody("text/xml; charset=UTF-8".toMediaType())
        val request = Request.Builder()
            .url("http://$tvIp/sony/IRCC")
            .addHeader("SOAPACTION", "\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"")
            .addHeader("X-Auth-PSK", preSharedKey)
            .post(body)
            .build()

        runCatching { http.newCall(request).execute().use { it.isSuccessful } }
            .getOrDefault(false)
    }

    private fun envelope(code: String) = """<?xml version="1.0"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body><u:X_SendIRCC xmlns:u="urn:schemas-sony-com:service:IRCC:1">
<IRCCCode>$code</IRCCCode>
</u:X_SendIRCC></s:Body></s:Envelope>"""
}
