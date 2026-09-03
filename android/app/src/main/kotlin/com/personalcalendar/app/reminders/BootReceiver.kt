package com.personalcalendar.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personalcalendar.app.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = Repository(context.applicationContext)
                ReminderScheduler.rescheduleAll(context.applicationContext, repository)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
