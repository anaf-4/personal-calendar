package com.personalcalendar.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

const val REMINDER_CHANNEL_ID = "event_reminders"

class CalendarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "일정 알림",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "예정된 일정에 대한 알림을 표시합니다."
            enableVibration(true)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
