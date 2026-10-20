package app.sonyremote.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.sonyremote.tv.Route
import app.sonyremote.tv.SettingsStore
import app.sonyremote.tv.SonyIrRemote
import app.sonyremote.tv.SonyKey
import app.sonyremote.tv.TransportMode
import app.sonyremote.tv.TvDevice
import app.sonyremote.tv.TvDiscovery
import app.sonyremote.tv.TvRemote
import app.sonyremote.tv.TvSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class Tone { IDLE, OK, WARN, ERROR }

data class LogEntry(val time: String, val key: String, val ok: Boolean, val detail: String)

data class UiState(
    val settings: TvSettings = TvSettings(),
    val status: String = "Toque na engrenagem para conectar",
    val tone: Tone = Tone.IDLE,
    val discovering: Boolean = false,
    val devices: List<TvDevice> = emptyList(),
    val discoverMessage: String? = null,
    val log: List<LogEntry> = emptyList(),
    val showSettings: Boolean = false,
    val irAvailable: Boolean = false,
)

class RemoteViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SettingsStore(application)
    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()
    private val remote = TvRemote(application, http, SonyIrRemote(application))
    private val discovery = TvDiscovery(application, http)
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _state = MutableStateFlow(UiState(irAvailable = remote.irAvailable))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = store.settings.first()
            _state.update { it.copy(settings = saved) }
            if (saved.ip.isBlank()) {
                discover()
            } else if (saved.name.isBlank()) {
                refreshName()
            }
        }
    }

    fun press(key: SonyKey) {
        val settings = _state.value.settings
        if (remote.routeFor(key, settings.mode) == Route.WIFI && settings.ip.isBlank()) {
            _state.update {
                it.copy(
                    status = "Defina o IP da TV na engrenagem",
                    tone = Tone.WARN,
                    showSettings = true,
                )
            }
            return
        }

        viewModelScope.launch {
            val result = remote.send(key, settings)
            val entry = LogEntry(clock.format(Date()), key.name, result.ok, result.detail)
            _state.update { current ->
                val (status, tone) = when {
                    result.ok -> {
                        val name = current.settings.name
                        (if (name.isBlank()) "Conectado" else "Conectado · $name") to Tone.OK
                    }
                    result.detail.endsWith("403") ->
                        "TV recusou (403): confira a chave PSK na engrenagem." to Tone.ERROR
                    else ->
                        "Não foi possível enviar (${result.detail}). Confira a conexão na engrenagem." to Tone.ERROR
                }
                current.copy(
                    log = (listOf(entry) + current.log).take(LOG_LIMIT),
                    status = status,
                    tone = tone,
                )
            }
        }
    }

    fun discover() {
        if (_state.value.discovering) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    discovering = true,
                    devices = emptyList(),
                    discoverMessage = null,
                    status = "Procurando a TV na rede...",
                    tone = Tone.WARN,
                )
            }
            val result = discovery.discover()
            _state.update { it.copy(discovering = false) }
            when {
                result.devices.size == 1 -> choose(result.devices.first())
                result.devices.size > 1 -> _state.update {
                    it.copy(
                        devices = result.devices,
                        status = "Escolha a TV na lista",
                        tone = Tone.WARN,
                        showSettings = true,
                    )
                }
                else -> _state.update {
                    it.copy(
                        status = "TV não encontrada",
                        tone = Tone.ERROR,
                        showSettings = true,
                        discoverMessage = if (result.onWifi) {
                            "Nenhuma TV Sony respondeu. Confira se a TV está ligada, no mesmo Wi-Fi, e digite o IP manualmente se precisar."
                        } else {
                            "O celular não está conectado a uma rede Wi-Fi."
                        },
                    )
                }
            }
        }
    }

    fun choose(device: TvDevice) {
        updateSettings { it.copy(ip = device.ip, name = device.name) }
        _state.update {
            it.copy(
                devices = emptyList(),
                discoverMessage = null,
                status = "TV encontrada: ${device.name}",
                tone = Tone.OK,
            )
        }
    }

    // Looks up the name of a TV whose IP was saved before names were stored
    private fun refreshName() {
        viewModelScope.launch {
            val ip = _state.value.settings.ip
            val match = discovery.discover().devices.firstOrNull { it.ip == ip }
            if (match != null && _state.value.settings.ip == ip) {
                updateSettings { it.copy(name = match.name) }
            }
        }
    }

    // Typing another IP means it may be a different TV, so the found name is dropped
    fun setIp(value: String) = updateSettings { it.copy(ip = value.trim(), name = "") }

    fun setPsk(value: String) = updateSettings { it.copy(psk = value.trim()) }

    fun setMode(mode: TransportMode) = updateSettings { it.copy(mode = mode) }

    fun openSettings() = _state.update { it.copy(showSettings = true) }

    fun closeSettings() = _state.update { it.copy(showSettings = false) }

    private fun updateSettings(transform: (TvSettings) -> TvSettings) {
        val updated = transform(_state.value.settings)
        _state.update { it.copy(settings = updated) }
        viewModelScope.launch { store.save(updated) }
    }

    private companion object {
        const val LOG_LIMIT = 8
    }
}
