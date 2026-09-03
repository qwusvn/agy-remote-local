package com.example.agyremote.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "agy_connection_prefs")

data class ConnectionConfig(
    val hostIp: String = "192.168.1.220",
    val port: Int = 4400,
    val autoReconnect: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val recentHosts: Set<String> = emptySet()
) {
    val httpUrl: String
        get() = "http://$hostIp:$port"

    val wsUrl: String
        get() = "ws://$hostIp:$port/connect-websocket"
}

class ConnectionPreferences(private val context: Context) {

    private object PreferencesKeys {
        val HOST_IP = stringPreferencesKey("host_ip")
        val PORT = intPreferencesKey("port")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val RECENT_HOSTS = stringSetPreferencesKey("recent_hosts")
    }

    val configFlow: Flow<ConnectionConfig> = context.dataStore.data.map { preferences ->
        val hostIp = preferences[PreferencesKeys.HOST_IP] ?: "192.168.1.220"
        val port = preferences[PreferencesKeys.PORT] ?: 4400
        val autoReconnect = preferences[PreferencesKeys.AUTO_RECONNECT] ?: true
        val notificationsEnabled = preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] ?: true
        val recentHosts = preferences[PreferencesKeys.RECENT_HOSTS] ?: setOf("192.168.1.220:4400")

        ConnectionConfig(
            hostIp = hostIp,
            port = port,
            autoReconnect = autoReconnect,
            notificationsEnabled = notificationsEnabled,
            recentHosts = recentHosts
        )
    }

    suspend fun saveHost(ip: String, port: Int) {
        val hostEntry = "$ip:$port"
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HOST_IP] = ip
            preferences[PreferencesKeys.PORT] = port

            val currentSet = preferences[PreferencesKeys.RECENT_HOSTS]?.toMutableSet() ?: mutableSetOf()
            currentSet.add(hostEntry)
            preferences[PreferencesKeys.RECENT_HOSTS] = currentSet
        }
    }

    suspend fun setAutoReconnect(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_RECONNECT] = enabled
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] = enabled
        }
    }
}
