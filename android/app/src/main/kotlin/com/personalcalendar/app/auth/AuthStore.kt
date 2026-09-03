package com.personalcalendar.app.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.authDataStore by preferencesDataStore(name = "calendar_auth")

data class AuthUser(
    val id: String,
    val email: String?,
    val displayName: String?,
    val discordUsername: String?
)

class AuthStore(private val context: Context) {
    private val tokenKey = stringPreferencesKey("token")
    private val userIdKey = stringPreferencesKey("user_id")
    private val userEmailKey = stringPreferencesKey("user_email")
    private val userDisplayNameKey = stringPreferencesKey("user_display_name")
    private val userDiscordKey = stringPreferencesKey("user_discord_username")
    private val pinHashKey = stringPreferencesKey("pin_hash")

    val token: Flow<String?> = context.authDataStore.data.map { it[tokenKey] }
    val pinHash: Flow<String?> = context.authDataStore.data.map { it[pinHashKey] }

    val user: Flow<AuthUser?> = context.authDataStore.data.map { prefs ->
        val id = prefs[userIdKey] ?: return@map null
        AuthUser(
            id = id,
            email = prefs[userEmailKey],
            displayName = prefs[userDisplayNameKey],
            discordUsername = prefs[userDiscordKey]
        )
    }

    suspend fun currentToken(): String? = token.first()

    suspend fun saveSession(newToken: String, authUser: AuthUser) {
        context.authDataStore.edit { prefs ->
            prefs[tokenKey] = newToken
            prefs[userIdKey] = authUser.id
            authUser.email?.let { prefs[userEmailKey] = it } ?: prefs.remove(userEmailKey)
            authUser.displayName?.let { prefs[userDisplayNameKey] = it } ?: prefs.remove(userDisplayNameKey)
            authUser.discordUsername?.let { prefs[userDiscordKey] = it } ?: prefs.remove(userDiscordKey)
        }
    }

    suspend fun clearSession() {
        context.authDataStore.edit { prefs ->
            prefs.remove(tokenKey)
            prefs.remove(userIdKey)
            prefs.remove(userEmailKey)
            prefs.remove(userDisplayNameKey)
            prefs.remove(userDiscordKey)
        }
    }

    suspend fun setPinHash(hash: String?) {
        context.authDataStore.edit { prefs ->
            if (hash == null) prefs.remove(pinHashKey) else prefs[pinHashKey] = hash
        }
    }
}
