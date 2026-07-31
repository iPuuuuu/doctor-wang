package com.ipuuuuu.doctorwang.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "doctor_wang_session")

const val DEFAULT_SERVER_URL = "http://10.0.2.2:8080/"

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val Token = stringPreferencesKey("token")
        val UserId = stringPreferencesKey("user_id")
        val LoggedIn = booleanPreferencesKey("logged_in")
        val ServerUrl = stringPreferencesKey("server_url")
        val ServerGuideSeen = booleanPreferencesKey("server_guide_seen")
    }

    suspend fun saveSession(token: String, userId: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.Token] = token
            preferences[Keys.UserId] = userId
            preferences[Keys.LoggedIn] = true
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            val serverUrl = preferences[Keys.ServerUrl]
            preferences.clear()
            serverUrl?.let { preferences[Keys.ServerUrl] = it }
        }
    }

    suspend fun isLoggedIn(): Boolean {
        return context.dataStore.data.first()[Keys.LoggedIn] == true
    }

    suspend fun getServerUrl(): String {
        return normalizeServerUrl(context.dataStore.data.first()[Keys.ServerUrl] ?: DEFAULT_SERVER_URL)
    }

    suspend fun saveServerUrl(value: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.ServerUrl] = normalizeServerUrl(value)
        }
    }

    suspend fun hasSeenServerGuide(): Boolean {
        return context.dataStore.data.first()[Keys.ServerGuideSeen] == true
    }

    suspend fun markServerGuideSeen() {
        context.dataStore.edit { preferences ->
            preferences[Keys.ServerGuideSeen] = true
        }
    }

    private fun normalizeServerUrl(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return DEFAULT_SERVER_URL
        return if (trimmed.endsWith('/')) trimmed else "$trimmed/"
    }
}
