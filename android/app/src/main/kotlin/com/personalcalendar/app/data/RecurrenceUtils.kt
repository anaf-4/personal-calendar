package com.personalcalendar.app.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

fun String.toLocalDateOrNull(): LocalDate? =
    if (isBlank()) null else runCatching { LocalDate.parse(this, DATE_FMT) }.getOrNull()

fun LocalDate.toKey(): String = format(DATE_FMT)

/**
 * Expands [event]'s recurrence rule into concrete occurrence dates within
 * [rangeStart, rangeEnd] (inclusive). Mirrors the desktop app's expansion logic.
 */
fun expandOccurrences(event: CalendarEvent, rangeStart: LocalDate, rangeEnd: LocalDate): List<EventOccurrence> {
    val anchor = event.date.toLocalDateOrNull() ?: return emptyList()
    val rec = event.recurrence

    if (rec.freqEnum() == RecurrenceFreq.NONE) {
        return if (!anchor.isBefore(rangeStart) && !anchor.isAfter(rangeEnd)) {
            listOf(EventOccurrence(event, anchor.toKey()))
        } else {
            emptyList()
        }
    }

    if (anchor.isAfter(rangeEnd)) return emptyList()

    val interval = rec.interval.coerceAtLeast(1)
    val until = rec.until?.toLocalDateOrNull()
    if (until != null && until.isBefore(anchor)) return emptyList()
    if (until != null && until.isBefore(rangeStart)) return emptyList()

    val cap = rangeEnd.plusYears(3)
    val hardEnd = if (until != null && until.isBefore(cap)) until else cap

    val results = mutableListOf<EventOccurrence>()
    val maxOcc = 3000
    var step = 0
    while (step < maxOcc) {
        val occ: LocalDate = when (rec.freqEnum()) {
            RecurrenceFreq.DAILY -> anchor.plusDays(step.toLong() * interval)
            RecurrenceFreq.WEEKLY -> anchor.plusWeeks(step.toLong() * interval)
            RecurrenceFreq.MONTHLY -> anchor.plusMonths(step.toLong() * interval)
            RecurrenceFreq.YEARLY -> anchor.plusYears(step.toLong() * interval)
            RecurrenceFreq.NONE -> break
        }
        if (occ.isAfter(hardEnd)) break
        if (!occ.isBefore(rangeStart) && !occ.isAfter(rangeEnd)) {
            results.add(EventOccurrence(event, occ.toKey()))
        }
        step++
    }
    return results
}

/** Finds the next occurrence at or after [from] (inclusive), or null if the series has ended. */
fun nextOccurrenceOnOrAfter(event: CalendarEvent, from: LocalDate): LocalDate? {
    val anchor = event.date.toLocalDateOrNull() ?: return null
    val rec = event.recurrence

    if (rec.freqEnum() == RecurrenceFreq.NONE) {
        return if (!anchor.isBefore(from)) anchor else null
    }

    val interval = rec.interval.coerceAtLeast(1)
    val until = rec.until?.toLocalDateOrNull()
    if (until != null && until.isBefore(from) && until.isBefore(anchor)) return null

    val searchHorizon = from.plusYears(5)
    val hardEnd = if (until != null && until.isBefore(searchHorizon)) until else searchHorizon

    val maxOcc = 5000
    var step = 0
    while (step < maxOcc) {
        val occ: LocalDate = when (rec.freqEnum()) {
            RecurrenceFreq.DAILY -> anchor.plusDays(step.toLong() * interval)
            RecurrenceFreq.WEEKLY -> anchor.plusWeeks(step.toLong() * interval)
            RecurrenceFreq.MONTHLY -> anchor.plusMonths(step.toLong() * interval)
            RecurrenceFreq.YEARLY -> anchor.plusYears(step.toLong() * interval)
            RecurrenceFreq.NONE -> return null
        }
        if (occ.isAfter(hardEnd)) return null
        if (!occ.isBefore(from)) return occ
        step++
    }
    return null
}
