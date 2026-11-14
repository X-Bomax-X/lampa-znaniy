package ru.ogemarshrut

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

class StreakTest {
    private val zone = TimeZone.getTimeZone("Asia/Irkutsk")

    private fun time(month: Int, date: Int, hour: Int = 0, minute: Int = 0): Long =
        GregorianCalendar(zone).apply {
            clear()
            set(2026, month - 1, date, hour, minute)
        }.timeInMillis

    private fun day(month: Int, date: Int): Long = studyDay(time(month, date), zone)

    @Test
    fun countsPracticeDaysAndAppliesOneFreezePerMissedDay() {
        val history = setOf(day(11, 10), day(11, 11), day(11, 14))
        val streak = studyStreak(history, day(11, 14))
        assertEquals(3, streak.count)
        assertEquals(setOf(day(11, 12), day(11, 13)), streak.frozenDays)
        assertEquals(3, streak.freezesLeft)
        assertTrue(streak.practicedToday)
    }

    @Test
    fun streakBreaksOnSixthMissAndDoesNotConsumeMoreFreezesUntilPracticeResumes() {
        val history = setOf(day(11, 1))
        val lastChance = studyStreak(history, day(11, 7))
        assertEquals(1, lastChance.count)
        assertEquals(0, lastChance.freezesLeft)
        assertFalse(lastChance.practicedToday)
        val broken = studyStreak(history + day(11, 25), day(11, 25))
        assertEquals(1, broken.count)
        assertEquals((2..6).map { day(11, it) }.toSet(), broken.frozenDays)
        assertEquals(0, broken.freezesLeft)
    }

    @Test
    fun monthlyRefillRespectsTheDateOfTheMissAndDoesNotCarryOver() {
        val history = setOf(day(11, 27), day(12, 3))
        val streak = studyStreak(history, day(12, 3))
        assertEquals(2, streak.count)
        assertEquals(3, streak.freezesLeft)
        assertEquals(5, streak.frozenDays.size)
        assertEquals(5, studyStreak(history, day(11, 27)).freezesLeft)
        assertEquals(0, studyStreak(emptySet(), day(12, 3)).count)
    }

    @Test
    fun remindersUseLocalTimeAndRollForwardToTheNextDay() {
        for ((reminder, hour) in listOf(Reminder.DAY to 12, Reminder.EVENING to 19, Reminder.STREAK to 23)) {
            assertEquals(time(11, 14, hour), reminder.nextAt(time(11, 14, 8), zone))
            assertEquals(time(11, 15, hour), reminder.nextAt(time(11, 14, hour), zone))
        }
        val warning = Reminder.STREAK
        assertNull(warning.message(studyStreak(emptySet(), day(11, 14))))
        assertNull(warning.message(studyStreak(setOf(day(11, 14)), day(11, 14))))
        assertNotNull(warning.message(studyStreak(setOf(day(11, 13)), day(11, 14))))
    }
}
