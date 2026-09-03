package com.personalcalendar.app.reminders

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.personalcalendar.app.MainActivity
import com.personalcalendar.app.R
import com.personalcalendar.app.REMINDER_CHANNEL_ID
import com.personalcalendar.app.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_OCCURRENCE_DATE = "occurrence_date"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = Repository(context.applicationContext)
                val event = repository.loadEvents().find { it.id == eventId }
                if (event != null) {
                    showNotification(context, eventId, event.title, event.start, event.end, event.memo)
                    ReminderScheduler.scheduleForEvent(context.applicationContext, event)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(
        context: Context,
        eventId: String,
        title: String,
        start: String,
        end: String,
        memo: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val timeText = listOfNotNull(
            start.takeIf { it.isNotBlank() }?.let { if (end.isNotBlank()) "$it - $end" else it }
        ).joinToString()
        val bodyLines = listOfNotNull(timeText.takeIf { it.isNotBlank() }, memo.takeIf { it.isNotBlank() })
        val body = bodyLines.joinToString("\n")

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = android.app.PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            contentIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🔔 $title")
            .setContentText(body.ifBlank { null })
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(eventId.hashCode(), notification)
        }
    }
}
