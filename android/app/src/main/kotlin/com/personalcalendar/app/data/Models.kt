package com.personalcalendar.app.data

import kotlinx.serialization.Serializable

enum class RecurrenceFreq {
    NONE, DAILY, WEEKLY, MONTHLY, YEARLY
}

@Serializable
data class Recurrence(
    val freq: String = "none", // none | daily | weekly | monthly | yearly
    val interval: Int = 1,
    val until: String? = null // yyyy-MM-dd
) {
    fun freqEnum(): RecurrenceFreq = when (freq) {
        "daily" -> RecurrenceFreq.DAILY
        "weekly" -> RecurrenceFreq.WEEKLY
        "monthly" -> RecurrenceFreq.MONTHLY
        "yearly" -> RecurrenceFreq.YEARLY
        else -> RecurrenceFreq.NONE
    }
}

@Serializable
data class CalendarEvent(
    val id: String,
    val title: String,
    val date: String, // yyyy-MM-dd, recurrence anchor date
    val start: String = "", // HH:mm or empty
    val end: String = "", // HH:mm or empty
    val categoryId: String = "",
    val memo: String = "",
    val recurrence: Recurrence = Recurrence(),
    val reminderMinutes: Int? = null
)

@Serializable
data class EventCategory(
    val id: String,
    val name: String,
    val color: Long // ARGB packed as 0xFFRRGGBB
)

@Serializable
data class AppSettings(
    val theme: String = "dark", // dark | light
    val closeBehaviorAsked: Boolean = false
)

/** A concrete occurrence of an event expanded from its recurrence rule. */
data class EventOccurrence(
    val event: CalendarEvent,
    val occurrenceDate: String // yyyy-MM-dd
)

val DEFAULT_CATEGORIES = listOf(
    EventCategory("work", "업무", 0xFF5B8DEFL),
    EventCategory("personal", "개인", 0xFF3EA36FL),
    EventCategory("important", "중요", 0xFFE0596BL),
    EventCategory("etc", "기타", 0xFF9B6CE0L)
)
