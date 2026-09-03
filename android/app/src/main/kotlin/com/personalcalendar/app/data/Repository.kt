package com.personalcalendar.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class Repository(context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val eventsFile = File(context.filesDir, "events.json")
    private val categoriesFile = File(context.filesDir, "categories.json")

    suspend fun loadEvents(): List<CalendarEvent> = withContext(Dispatchers.IO) {
        runCatching {
            if (!eventsFile.exists()) return@runCatching emptyList()
            json.decodeFromString<List<CalendarEvent>>(eventsFile.readText())
        }.getOrElse { emptyList() }
    }

    suspend fun saveEvents(events: List<CalendarEvent>) = withContext(Dispatchers.IO) {
        eventsFile.writeText(json.encodeToString(events))
    }

    suspend fun loadCategories(): List<EventCategory> = withContext(Dispatchers.IO) {
        runCatching {
            if (!categoriesFile.exists()) return@runCatching DEFAULT_CATEGORIES
            json.decodeFromString<List<EventCategory>>(categoriesFile.readText())
        }.getOrElse { DEFAULT_CATEGORIES }
    }

    suspend fun saveCategories(categories: List<EventCategory>) = withContext(Dispatchers.IO) {
        categoriesFile.writeText(json.encodeToString(categories))
    }
}
