package com.personalcalendar.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "calendar_settings")

class SettingsStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val activeCategoriesKey = stringPreferencesKey("active_categories")
    private val exactAlarmHintShownKey = booleanPreferencesKey("exact_alarm_hint_shown")

    val theme: Flow<String> = context.dataStore.data.map { it[themeKey] ?: "dark" }

    suspend fun setTheme(value: String) {
        context.dataStore.edit { it[themeKey] = value }
    }

    /** Comma-separated category ids that are active in the filter; null/empty means "all". */
    val activeCategoryIds: Flow<String?> = context.dataStore.data.map { it[activeCategoriesKey] }

    suspend fun setActiveCategoryIds(ids: Set<String>) {
        context.dataStore.edit { it[activeCategoriesKey] = ids.joinToString(",") }
    }

    val exactAlarmHintShown: Flow<Boolean> = context.dataStore.data.map { it[exactAlarmHintShownKey] ?: false }

    suspend fun setExactAlarmHintShown(shown: Boolean) {
        context.dataStore.edit { it[exactAlarmHintShownKey] = shown }
    }
}
