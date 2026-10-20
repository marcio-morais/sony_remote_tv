package app.sonyremote.tv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Outcome of one key press. [detail] is short text for the history ("IR", "HTTP 200", ...). */
data class SendResult(val ok: Boolean, val detail: String)

/**
 * Sends IRCC commands to a Sony Bravia over Wi-Fi (HTTP SOAP).
 * Needs android:usesCleartextTraffic="true" because the TV speaks plain HTTP.
 */
class SonyTvClient(
    private val http: OkHttpClient,
    private val tvIp: String,
    private val preSharedKey: String,
) {
    suspend fun send(key: SonyKey): SendResult = withContext(Dispatchers.IO) {
        val body = envelope(key.irccCode).toRequestBody("text/xml; charset=UTF-8".toMediaType())

        try {
            // url() throws IllegalArgumentException for a malformed IP, so it stays inside the try
            val builder = Request.Builder()
                .url("http://$tvIp/sony/IRCC")
                .addHeader("SOAPACTION", "\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"")
                .post(body)
            if (preSharedKey.isNotBlank()) builder.addHeader("X-Auth-PSK", preSharedKey)

            http.newCall(builder.build()).execute().use { response ->
                SendResult(response.isSuccessful, "HTTP ${response.code}")
            }
        } catch (error: IOException) {
            SendResult(false, "sem resposta")
        } catch (error: IllegalArgumentException) {
            SendResult(false, "IP inválido")
        }
    }

    private fun envelope(code: String) = """<?xml version="1.0"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body><u:X_SendIRCC xmlns:u="urn:schemas-sony-com:service:IRCC:1">
<IRCCCode>$code</IRCCCode>
</u:X_SendIRCC></s:Body></s:Envelope>"""
}
