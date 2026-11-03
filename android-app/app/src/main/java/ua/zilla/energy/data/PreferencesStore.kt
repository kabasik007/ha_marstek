package ua.zilla.energy.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import ua.zilla.energy.model.AppSettings
import ua.zilla.energy.model.CameraDevice
import ua.zilla.energy.model.EnergyEvent
import ua.zilla.energy.model.EventKind

private val Context.preferences by preferencesDataStore(name = "zilla_energy")

class PreferencesStore(private val context: Context) {
    private val demoKey = booleanPreferencesKey("demo")
    private val hostKey = stringPreferencesKey("host")
    private val portKey = intPreferencesKey("port")
    private val intervalKey = intPreferencesKey("poll_seconds")
    private val camerasKey = stringPreferencesKey("cameras_json")
    private val eventsKey = stringPreferencesKey("events_json")

    val settings: Flow<AppSettings> = context.preferences.data.map { prefs ->
        AppSettings(
            demoMode = prefs[demoKey] ?: true,
            host = prefs[hostKey] ?: "",
            port = prefs[portKey] ?: 30000,
            pollSeconds = (prefs[intervalKey] ?: 30).coerceIn(15, 300),
            cameras = decodeCameras(prefs[camerasKey] ?: "[]")
        )
    }

    val events: Flow<List<EnergyEvent>> = context.preferences.data.map {
        decodeEvents(it[eventsKey] ?: "[]")
    }

    suspend fun saveEndpoint(host: String, port: Int) {
        require(LocalAddress.valid(host))
        require(port in 1..65535)
        context.preferences.edit {
            it[hostKey] = host
            it[portKey] = port
        }
    }

    suspend fun setDemo(enabled: Boolean) {
        context.preferences.edit { it[demoKey] = enabled }
    }

    suspend fun setPollSeconds(seconds: Int) {
        context.preferences.edit { it[intervalKey] = seconds.coerceIn(15, 300) }
    }

    suspend fun addCamera(name: String, url: String) {
        require(name.trim().isNotBlank() && name.length <= 40)
        require(LocalAddress.validRtsp(url))
        context.preferences.edit {
            val cameras = decodeCameras(it[camerasKey] ?: "[]")
            val next = (cameras + CameraDevice(
                id = java.util.UUID.randomUUID().toString(),
                name = name.trim(),
                rtspUrl = url.trim()
            )).takeLast(12)
            it[camerasKey] = encodeCameras(next)
        }
    }

    suspend fun removeCamera(id: String) {
        context.preferences.edit {
            it[camerasKey] = encodeCameras(
                decodeCameras(it[camerasKey] ?: "[]").filterNot { item -> item.id == id }
            )
        }
    }

    suspend fun addEvent(event: EnergyEvent) {
        context.preferences.edit { prefs ->
            val old = decodeEvents(prefs[eventsKey] ?: "[]")
            prefs[eventsKey] = encodeEvents((listOf(event) + old).take(250))
        }
    }

    suspend fun clearEvents() {
        context.preferences.edit { it[eventsKey] = "[]" }
    }

    private fun decodeCameras(raw: String): List<CameraDevice> = try {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val x = arr.optJSONObject(i) ?: return@mapNotNull null
            val url = x.optString("url")
            if (!LocalAddress.validRtsp(url)) return@mapNotNull null
            CameraDevice(x.optString("id"), x.optString("name"), url)
        }
    } catch (_: Exception) { emptyList() }

    private fun encodeCameras(items: List<CameraDevice>): String =
        JSONArray().apply {
            items.forEach { item ->
                put(JSONObject().put("id", item.id).put("name", item.name).put("url", item.rtspUrl))
            }
        }.toString()

    private fun decodeEvents(raw: String): List<EnergyEvent> = try {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val x = arr.optJSONObject(i) ?: return@mapNotNull null
            val kind = runCatching { EventKind.valueOf(x.optString("kind")) }.getOrNull()
                ?: return@mapNotNull null
            EnergyEvent(
                timestampMs = x.optLong("at"),
                kind = kind,
                title = x.optString("title"),
                detail = x.optString("detail")
            )
        }
    } catch (_: Exception) { emptyList() }

    private fun encodeEvents(items: List<EnergyEvent>): String =
        JSONArray().apply {
            items.forEach { item ->
                put(JSONObject().put("at", item.timestampMs).put("kind", item.kind.name)
                    .put("title", item.title).put("detail", item.detail))
            }
        }.toString()
}
