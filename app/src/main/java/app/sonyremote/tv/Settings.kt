package app.sonyremote.tv

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "sonyremote")

/** How key presses reach the TV. */
enum class TransportMode(val label: String) {
    AUTO("Automático"),
    IR("Infravermelho"),
    WIFI("Wi-Fi"),
}

data class TvSettings(
    val ip: String = "",
    val name: String = "",
    val psk: String = "0000",
    val mode: TransportMode = TransportMode.AUTO,
    /** TV MAC address, needed to wake it from standby (Wake-on-LAN). */
    val mac: String = "",
)

class SettingsStore(private val context: Context) {

    val settings: Flow<TvSettings> = context.dataStore.data.map { prefs ->
        TvSettings(
            ip = prefs[IP].orEmpty(),
            name = prefs[NAME].orEmpty(),
            psk = prefs[PSK] ?: "0000",
            mode = prefs[MODE]
                ?.let { stored -> TransportMode.entries.firstOrNull { it.name == stored } }
                ?: TransportMode.AUTO,
            mac = prefs[MAC].orEmpty(),
        )
    }

    suspend fun save(value: TvSettings) {
        context.dataStore.edit { prefs ->
            prefs[IP] = value.ip
            prefs[NAME] = value.name
            prefs[PSK] = value.psk
            prefs[MODE] = value.mode.name
            prefs[MAC] = value.mac
        }
    }

    private companion object {
        val IP = stringPreferencesKey("ip")
        val NAME = stringPreferencesKey("name")
        val PSK = stringPreferencesKey("psk")
        val MODE = stringPreferencesKey("mode")
        val MAC = stringPreferencesKey("mac")
    }
}
