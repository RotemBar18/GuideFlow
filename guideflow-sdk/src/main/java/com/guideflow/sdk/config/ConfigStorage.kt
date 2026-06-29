package com.guideflow.sdk.config

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.guideflow.shared.TutorialConfig
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.guideFlowDataStore by preferencesDataStore(name = "guideflow")

/**
 * DataStore-backed cache for the last-known-good config (plus the hashed SDK user
 * id). Stores the config as JSON; a corrupt entry simply reads back as null.
 */
internal class ConfigStorage(context: Context) : ConfigCache {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun load(): TutorialConfig? {
        val prefs = appContext.guideFlowDataStore.data.first()
        val raw = prefs[CONFIG_JSON] ?: return null
        return runCatching { json.decodeFromString<TutorialConfig>(raw) }.getOrNull()
    }

    override suspend fun save(config: TutorialConfig) {
        appContext.guideFlowDataStore.edit { prefs ->
            prefs[CONFIG_JSON] = json.encodeToString(config)
            prefs[CONFIG_VERSION] = config.configVersion
            prefs[LAST_SYNC] = System.currentTimeMillis()
        }
    }

    suspend fun saveUserHash(hash: String?) {
        appContext.guideFlowDataStore.edit { prefs ->
            if (hash == null) prefs.remove(USER_HASH) else prefs[USER_HASH] = hash
        }
    }

    private companion object {
        val CONFIG_JSON = stringPreferencesKey("config_json")
        val CONFIG_VERSION = intPreferencesKey("config_version")
        val LAST_SYNC = longPreferencesKey("last_sync")
        val USER_HASH = stringPreferencesKey("user_hash")
    }
}
