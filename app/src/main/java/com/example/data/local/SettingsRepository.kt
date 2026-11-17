package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.brstiDataStore: DataStore<Preferences> by preferencesDataStore(name = "brsti_settings")

data class BrstiSettings(
    val ownerName: String = "Swapnil",
    val assistantVoice: String = "Leda",
    val languageMode: String = "auto",
    val wakeWordEnabled: Boolean = false,
    val wakeWordPhrase: String = "Hey Brsti",
    val memoryConsentGranted: Boolean = true,
    val liveSubtitlesEnabled: Boolean = true
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val OWNER_NAME = stringPreferencesKey("owner_name")
        val ASSISTANT_VOICE = stringPreferencesKey("assistant_voice")
        val LANGUAGE_MODE = stringPreferencesKey("language_mode")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val WAKE_WORD_PHRASE = stringPreferencesKey("wake_word_phrase")
        val MEMORY_CONSENT = booleanPreferencesKey("memory_consent")
        val LIVE_SUBTITLES = booleanPreferencesKey("live_subtitles")
    }

    val settingsFlow: Flow<BrstiSettings> = context.brstiDataStore.data.map { prefs ->
        BrstiSettings(
            ownerName = prefs[Keys.OWNER_NAME] ?: "Swapnil",
            assistantVoice = prefs[Keys.ASSISTANT_VOICE] ?: "Leda",
            languageMode = prefs[Keys.LANGUAGE_MODE] ?: "auto",
            wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: false,
            wakeWordPhrase = prefs[Keys.WAKE_WORD_PHRASE] ?: "Hey Brsti",
            memoryConsentGranted = prefs[Keys.MEMORY_CONSENT] ?: true,
            liveSubtitlesEnabled = prefs[Keys.LIVE_SUBTITLES] ?: true
        )
    }

    suspend fun getSnapshot(): BrstiSettings = settingsFlow.first()

    suspend fun setOwnerName(name: String) {
        val sanitized = name.trim().ifEmpty { "Swapnil" }
        context.brstiDataStore.edit { it[Keys.OWNER_NAME] = sanitized }
    }

    suspend fun setAssistantVoice(voice: String) {
        context.brstiDataStore.edit { it[Keys.ASSISTANT_VOICE] = voice }
    }

    suspend fun setLanguageMode(mode: String) {
        context.brstiDataStore.edit { it[Keys.LANGUAGE_MODE] = mode }
    }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        context.brstiDataStore.edit { it[Keys.WAKE_WORD_ENABLED] = enabled }
    }

    suspend fun setMemoryConsent(granted: Boolean) {
        context.brstiDataStore.edit { it[Keys.MEMORY_CONSENT] = granted }
    }

    suspend fun setLiveSubtitles(enabled: Boolean) {
        context.brstiDataStore.edit { it[Keys.LIVE_SUBTITLES] = enabled }
    }
}
