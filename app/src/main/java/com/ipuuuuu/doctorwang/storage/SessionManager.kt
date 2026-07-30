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

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val Token = stringPreferencesKey("token")
        val UserId = stringPreferencesKey("user_id")
        val LoggedIn = booleanPreferencesKey("logged_in")
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
            preferences.clear()
        }
    }

    suspend fun isLoggedIn(): Boolean {
        return context.dataStore.data.first()[Keys.LoggedIn] == true
    }
}
