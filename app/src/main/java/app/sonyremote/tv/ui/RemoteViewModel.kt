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
import app.sonyremote.tv.UpdateChecker
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

/** Outcome of "Verificar atualização": a message and, optionally, a link to open. */
data class UpdateUi(val message: String, val linkLabel: String? = null, val linkUrl: String? = null)

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
    val version: String = "",
    val checkingUpdate: Boolean = false,
    val update: UpdateUi? = null,
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

    private val updates = UpdateChecker(http)
    private val installedVersion = runCatching {
        application.packageManager.getPackageInfo(application.packageName, 0).versionName
    }.getOrNull().orEmpty()

    private val _state = MutableStateFlow(
        UiState(irAvailable = remote.irAvailable, version = installedVersion),
    )
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

    /** Opening the app wakes the TV when it is in standby (at most once a minute). */
    fun wakeIfStandby() {
        val now = System.currentTimeMillis()
        if (now - lastWakeAt < WAKE_COOLDOWN_MS) return
        lastWakeAt = now
        viewModelScope.launch {
            val settings = store.settings.first()
            if (settings.ip.isBlank()) return@launch
            val result = remote.wakeIfStandby(settings) ?: return@launch
            _state.update {
                if (result.ok) {
                    it.copy(status = "Ligando a TV...", tone = Tone.WARN)
                } else {
                    it.copy(
                        status = "TV em standby: informe o MAC da TV na engrenagem para ligar ao abrir.",
                        tone = Tone.WARN,
                    )
                }
            }
        }
    }

    fun choose(device: TvDevice) {
        updateSettings {
            it.copy(
                ip = device.ip,
                name = device.name,
                mac = device.mac.ifBlank { if (it.ip == device.ip) it.mac else "" },
            )
        }
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

    fun setMac(value: String) = updateSettings { it.copy(mac = value.trim()) }

    fun checkUpdate() {
        if (_state.value.checkingUpdate) return
        viewModelScope.launch {
            _state.update { it.copy(checkingUpdate = true, update = null) }
            val info = updates.check(installedVersion)
            val result = when {
                info == null -> UpdateUi(
                    "Não foi possível verificar agora. Abra a página no GitHub para conferir.",
                    "Abrir no GitHub",
                    UpdateChecker.RELEASES_URL,
                )
                info.isNewer -> UpdateUi(
                    "Versão ${info.latestVersion} disponível.",
                    "Baixar versão nova",
                    UpdateChecker.APK_URL,
                )
                else -> UpdateUi("Você está na versão mais recente (${info.latestVersion}).")
            }
            _state.update { it.copy(checkingUpdate = false, update = result) }
        }
    }

    fun openSettings() = _state.update { it.copy(showSettings = true) }

    fun closeSettings() = _state.update { it.copy(showSettings = false) }

    private fun updateSettings(transform: (TvSettings) -> TvSettings) {
        val updated = transform(_state.value.settings)
        _state.update { it.copy(settings = updated) }
        viewModelScope.launch { store.save(updated) }
    }

    private var lastWakeAt = 0L

    private companion object {
        const val LOG_LIMIT = 8
        const val WAKE_COOLDOWN_MS = 60_000L
    }
}
