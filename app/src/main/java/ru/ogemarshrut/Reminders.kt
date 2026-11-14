package ru.ogemarshrut

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

private const val ChannelId = "study_reminders"
private const val ReminderAction = "ru.ogemarshrut.STUDY_REMINDER"
private const val ReminderKind = "reminder_kind"
private const val ScheduledAt = "scheduled_at"

internal enum class Reminder(val hour: Int, val notificationId: Int) {
    DAY(12, 101),
    EVENING(19, 102),
    STREAK(23, 103);

    fun nextAt(now: Long, zone: TimeZone = TimeZone.getDefault()): Long =
        GregorianCalendar(zone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis

    fun message(streak: StudyStreak, eveningMessage: () -> String = { "Реши несколько заданий для подготовки к ОГЭ." }): String? = when (this) {
        DAY -> "Есть несколько минут? Пройди короткий урок и подготовься к ОГЭ."
        EVENING -> eveningMessage()
        STREAK -> when {
            streak.practicedToday || streak.count == 0 -> null
            streak.freezesLeft > 0 ->
                "До конца дня остался час. Пройди урок, чтобы сохранить серию и не тратить заморозку."
            else -> "Через час ты можешь потерять серию. Пройди урок до полуночи, чтобы её сохранить."
        }
    }
}

internal fun allowsExactReminders(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

internal fun allowsStudyNotifications(context: Context): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
        context.getSystemService(NotificationManager::class.java).getNotificationChannel(ChannelId)?.importance !=
        NotificationManager.IMPORTANCE_NONE
}

internal fun createStudyNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(ChannelId, "Напоминания о занятиях", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}

internal fun refreshReminders(context: Context, now: Long = System.currentTimeMillis()) {
    val alarms = context.getSystemService(AlarmManager::class.java)
    if (ProgressStore(context).readReminders()) {
        createStudyNotificationChannel(context)
        Reminder.entries.forEach { scheduleReminder(context, alarms, it, now) }
    } else {
        val notifications = NotificationManagerCompat.from(context)
        for (reminder in Reminder.entries) {
            reminderIntent(context, reminder, 0L, PendingIntent.FLAG_NO_CREATE)?.let(alarms::cancel)
            notifications.cancel(reminder.notificationId)
        }
    }
}

private fun reminderIntent(context: Context, reminder: Reminder, at: Long, flag: Int): PendingIntent? =
    PendingIntent.getBroadcast(
        context,
        reminder.notificationId,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderAction)
            .putExtra(ReminderKind, reminder.name)
            .putExtra(ScheduledAt, at),
        flag or PendingIntent.FLAG_IMMUTABLE
    )

@SuppressLint("ScheduleExactAlarm")
private fun scheduleReminder(context: Context, alarms: AlarmManager, reminder: Reminder, now: Long) {
    val at = reminder.nextAt(now)
    val operation = reminderIntent(context, reminder, at, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
    if (reminder == Reminder.STREAK && allowsExactReminders(context)) {
        try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            return
        } catch (_: SecurityException) {
            // The user can revoke access between the check and the call.
        }
    }
    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        receive(context, intent, System.currentTimeMillis())
    }

    internal fun receive(context: Context, intent: Intent, now: Long) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED ->
                refreshReminders(context, now)
            ReminderAction -> {
                val reminder = Reminder.entries.firstOrNull { it.name == intent.getStringExtra(ReminderKind) } ?: return
                val store = ProgressStore(context)
                if (!store.readReminders()) return
                val today = studyDay(now)
                val scheduled = intent.getLongExtra(ScheduledAt, 0L)
                if (scheduled in 1..now && studyDay(scheduled) == today) {
                    val streak = studyStreak(store.read().studyDays, today)
                    val message = reminder.message(streak) {
                        context.getString(R.string.evening_reminder, context.getString(R.string.app_name))
                    }
                    if (message != null && store.readReminderDay(reminder) != today && post(context, reminder, message)) {
                        store.saveReminderDay(reminder, today)
                    }
                }
                val alarms = context.getSystemService(AlarmManager::class.java)
                scheduleReminder(context, alarms, reminder, now)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun post(context: Context, reminder: Reminder, message: String): Boolean {
        if (!allowsStudyNotifications(context)) return false
        createStudyNotificationChannel(context)
        val launch = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (reminder == Reminder.STREAK) "Ударный режим" else "Время позаниматься")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(launch)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(reminder.notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
