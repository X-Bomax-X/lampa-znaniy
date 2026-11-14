package ru.ogemarshrut

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.util.GregorianCalendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@Suppress("DEPRECATION")
class ReminderTest {
    @Test
    fun restoresThreeAlarmsAfterBootAndCancelsThemWhenDisabled() {
        val context = RuntimeEnvironment.getApplication()
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        assertTrue(StudyModel(context).remindersEnabled)
        ReminderReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(3, alarms.scheduledAlarms.size)
        val now = System.currentTimeMillis()
        for (reminder in Reminder.entries) {
            val alarm = alarms.scheduledAlarms.single { it.triggerAtMs == reminder.nextAt(now) }
            assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
            assertTrue(alarm.isAllowWhileIdle)
            assertEquals(reminder == Reminder.STREAK, alarm.windowLengthMs == ShadowAlarmManager.WINDOW_EXACT)
        }
        assertTrue(StudyModel(context).setReminders(false))
        assertTrue(alarms.scheduledAlarms.isEmpty())
        assertFalse(StudyModel(context).remindersEnabled)
        ReminderReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test
    fun warningIsPostedOnceWhenTheStreakIsAtRisk() {
        val context = RuntimeEnvironment.getApplication()
        val store = ProgressStore(context)
        val receiver = ReminderReceiver()
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
        val before = GregorianCalendar(2026, 10, 14, 22, 30).timeInMillis
        assertTrue(store.saveReminders(true))
        assertTrue(store.save(Progress(studyDays = setOf(studyDay(before) - 1))))
        refreshReminders(context, before)
        val warning = alarms.scheduledAlarms.single { it.triggerAtMs == Reminder.STREAK.nextAt(before) }
        val intent = Intent(shadowOf(warning.operation).savedIntent)
        assertTrue(allowsStudyNotifications(context))
        receiver.receive(context, intent, warning.triggerAtMs)
        assertEquals(studyDay(warning.triggerAtMs), store.readReminderDay(Reminder.STREAK))
        assertNotNull(notifications.getNotification(Reminder.STREAK.notificationId))
        context.getSystemService(NotificationManager::class.java).cancel(Reminder.STREAK.notificationId)
        receiver.receive(context, intent, warning.triggerAtMs)
        assertNull(notifications.getNotification(Reminder.STREAK.notificationId))
        assertTrue(alarms.scheduledAlarms.any { it.triggerAtMs == Reminder.STREAK.nextAt(warning.triggerAtMs) })
    }

}
