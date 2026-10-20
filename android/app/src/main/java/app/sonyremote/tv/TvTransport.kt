package app.sonyremote.tv

/** A way of delivering a key press to the TV (infrared or Wi-Fi). */
interface TvTransport {
    val isAvailable: Boolean
    suspend fun send(key: SonyKey): Boolean
}

class IrTransport(private val ir: SonyIrRemote) : TvTransport {
    override val isAvailable: Boolean get() = ir.isAvailable

    override suspend fun send(key: SonyKey): Boolean {
        val command = key.sircCommand ?: return false // key has no SIRC mapping
        ir.send(command)
        return true // IR is fire-and-forget: there is no delivery feedback
    }
}

class WifiTransport(private val client: SonyTvClient) : TvTransport {
    override val isAvailable: Boolean get() = true // the real check is the HTTP result

    override suspend fun send(key: SonyKey): Boolean = client.send(key)
}

/**
 * Tries the primary transport (IR) first and falls back to the secondary (Wi-Fi)
 * when the phone has no IR emitter or the key has no IR mapping.
 */
class TvRemote(
    private val primary: TvTransport,
    private val fallback: TvTransport,
) {
    suspend fun send(key: SonyKey): Boolean {
        if (primary.isAvailable && primary.send(key)) return true
        return fallback.send(key)
    }
}
