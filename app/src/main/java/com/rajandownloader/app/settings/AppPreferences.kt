package com.rajandownloader.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "rajan_downloader_prefs")

class AppPreferences(private val context: Context) {

    companion object {
        val KEY_DEST_URI = stringPreferencesKey("destination_tree_uri")
        val KEY_DEST_DISPLAY = stringPreferencesKey("destination_display_name")
        val KEY_CONTAINER = stringPreferencesKey("preferred_container")
        val KEY_CUSTOM_COOKIES = stringPreferencesKey("custom_cookies_path")
        val KEY_AUTO_CLEAN = booleanPreferencesKey("auto_clean_staging")
    }

    val destinationUri: Flow<String?> = context.dataStore.data.map { it[KEY_DEST_URI] }
    val destinationDisplayName: Flow<String?> = context.dataStore.data.map { it[KEY_DEST_DISPLAY] }
    val preferredContainer: Flow<String> = context.dataStore.data.map { it[KEY_CONTAINER] ?: "mp4" }
    val customCookiesPath: Flow<String?> = context.dataStore.data.map { it[KEY_CUSTOM_COOKIES] }
    val autoCleanStaging: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_CLEAN] ?: true }

    suspend fun setDestination(uriString: String, displayName: String) {
        context.dataStore.edit {
            it[KEY_DEST_URI] = uriString
            it[KEY_DEST_DISPLAY] = displayName
        }
    }

    suspend fun setPreferredContainer(format: String) {
        context.dataStore.edit { it[KEY_CONTAINER] = format }
    }

    suspend fun setCustomCookiesPath(path: String?) {
        context.dataStore.edit {
            if (path == null) it.remove(KEY_CUSTOM_COOKIES)
            else it[KEY_CUSTOM_COOKIES] = path
        }
    }
}

