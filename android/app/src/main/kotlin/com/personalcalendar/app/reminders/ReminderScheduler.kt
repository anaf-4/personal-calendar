package com.personalcalendar.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.personalcalendar.app.data.CalendarEvent
import com.personalcalendar.app.data.Repository
import com.personalcalendar.app.data.nextOccurrenceOnOrAfter
import java.time.LocalDate
import java.time.LocalDateTime

object ReminderScheduler {

    private fun pendingIntentFor(context: Context, eventId: String, flags: Int): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            data = Uri.parse("calendarapp://reminder/$eventId")
            putExtra(ReminderReceiver.EXTRA_EVENT_ID, eventId)
        }
        return PendingIntent.getBroadcast(context, eventId.hashCode(), intent, flags)
    }

    private fun parseTime(hhmm: String): Pair<Int, Int>? {
        val parts = hhmm.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h to m
    }

    /** Finds the next (occurrenceDate, triggerDateTime) pair whose trigger hasn't passed yet. */
    private fun computeNextTrigger(event: CalendarEvent, now: LocalDateTime): Pair<LocalDate, LocalDateTime>? {
        val reminderMinutes = event.reminderMinutes ?: return null
        val (h, m) = parseTime(event.start) ?: return null
        var searchFrom = now.toLocalDate()
        repeat(50) {
            val occDate = nextOccurrenceOnOrAfter(event, searchFrom) ?: return null
            val startDateTime = occDate.atTime(h, m)
            val triggerDateTime = startDateTime.minusMinutes(reminderMinutes.toLong())
            if (!triggerDateTime.isBefore(now)) {
                return occDate to triggerDateTime
            }
            searchFrom = occDate.plusDays(1)
        }
        return null
    }

    fun scheduleForEvent(context: Context, event: CalendarEvent, now: LocalDateTime = LocalDateTime.now()) {
        cancelForEvent(context, event.id)
        if (event.reminderMinutes == null || event.start.isBlank()) return

        val (occDate, triggerDateTime) = computeNextTrigger(event, now) ?: return
        val triggerMillis = triggerDateTime
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            data = Uri.parse("calendarapp://reminder/${event.id}")
            putExtra(ReminderReceiver.EXTRA_EVENT_ID, event.id)
            putExtra(ReminderReceiver.EXTRA_OCCURRENCE_DATE, occDate.toString())
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            event.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        }
    }

    fun cancelForEvent(context: Context, eventId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = pendingIntentFor(
            context,
            eventId,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    suspend fun rescheduleAll(context: Context, repository: Repository) {
        val now = LocalDateTime.now()
        repository.loadEvents().forEach { event ->
            scheduleForEvent(context, event, now)
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        return alarmManager.canScheduleExactAlarms()
    }
}
