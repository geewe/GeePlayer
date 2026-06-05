package com.geeplayer.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dlna_settings")

class AppPreferences(private val context: Context) {

    companion object {
        private val DEVICE_NAME = stringPreferencesKey("device_name")
        private val HTTP_PORT = intPreferencesKey("http_port")
        private val DARK_MODE = booleanPreferencesKey("dark_mode")
        private val VOLUME_NORMALIZATION = booleanPreferencesKey("volume_normalization")
        private val CROSSFADE_ENABLED = booleanPreferencesKey("crossfade_enabled")
        private val CROSSFADE_DURATION = intPreferencesKey("crossfade_duration")
        private val BOOT_START = booleanPreferencesKey("boot_start")
        private val EQUALIZER_PRESET = stringPreferencesKey("equalizer_preset")
        private val FONT_FAMILY = stringPreferencesKey("font_family")
    }

    val deviceName: Flow<String> = context.dataStore.data.map { it[DEVICE_NAME] ?: "Geeplayer" }
    val httpPort: Flow<Int> = context.dataStore.data.map { it[HTTP_PORT] ?: 49820 }
    val darkMode: Flow<Boolean> = context.dataStore.data.map { it[DARK_MODE] ?: true }
    val volumeNormalization: Flow<Boolean> = context.dataStore.data.map { it[VOLUME_NORMALIZATION] ?: false }
    val crossfadeEnabled: Flow<Boolean> = context.dataStore.data.map { it[CROSSFADE_ENABLED] ?: false }
    val crossfadeDuration: Flow<Int> = context.dataStore.data.map { it[CROSSFADE_DURATION] ?: 3 }
    val bootStart: Flow<Boolean> = context.dataStore.data.map { it[BOOT_START] ?: false }
    val fontFamily: Flow<String> = context.dataStore.data.map { it[FONT_FAMILY] ?: "jiang_cheng_lyric_song" }

    suspend fun setDeviceName(name: String) {
        context.dataStore.edit { it[DEVICE_NAME] = name }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[DARK_MODE] = enabled }
    }

    suspend fun setVolumeNormalization(enabled: Boolean) {
        context.dataStore.edit { it[VOLUME_NORMALIZATION] = enabled }
    }

    suspend fun setCrossfadeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[CROSSFADE_ENABLED] = enabled }
    }

    suspend fun setFontFamily(font: String) {
        context.dataStore.edit { it[FONT_FAMILY] = font }
    }
    suspend fun setBootStart(enabled: Boolean) {
        context.dataStore.edit { it[BOOT_START] = enabled }
    }

}
