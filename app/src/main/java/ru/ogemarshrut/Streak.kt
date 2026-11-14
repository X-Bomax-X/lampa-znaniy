package ru.ogemarshrut

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

internal const val MonthlyFreezes = 5
internal const val DayMillis = 86_400_000L
private val Utc = TimeZone.getTimeZone("UTC")

internal fun studyDay(now: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): Long {
    val local = now + zone.getOffset(now)
    return (local - (local % DayMillis + DayMillis) % DayMillis) / DayMillis
}

internal fun calendarForDay(day: Long) = GregorianCalendar(Utc).apply {
    timeInMillis = day * DayMillis
}

internal fun nextMidnight(now: Long): Long = GregorianCalendar().apply {
    timeInMillis = now
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    add(Calendar.DAY_OF_MONTH, 1)
}.timeInMillis

internal data class StudyStreak(
    val count: Int,
    val freezesLeft: Int,
    val practicedToday: Boolean,
    val frozenDays: Set<Long>
)

internal fun studyStreak(studyDays: Set<Long>, today: Long): StudyStreak {
    var count = 0
    var freezes = MonthlyFreezes
    var month = Long.MIN_VALUE
    var previous: Long? = null
    val frozen = mutableSetOf<Long>()

    fun refreshBudget(day: Long) {
        val date = calendarForDay(day)
        val key = date.get(Calendar.YEAR) * 12L + date.get(Calendar.MONTH)
        if (key != month) {
            month = key
            freezes = MonthlyFreezes
        }
    }

    fun processGap(until: Long) {
        var missed = (previous ?: return) + 1
        // A gap ends the series as soon as a missed day cannot be covered.
        while (missed < until && count > 0) {
            refreshBudget(missed)
            if (freezes > 0) {
                freezes--
                frozen.add(missed)
            } else {
                count = 0
            }
            missed++
        }
    }

    for (day in studyDays.filter { it <= today }.sorted()) {
        processGap(day)
        refreshBudget(day)
        count++
        previous = day
    }
    processGap(today)
    refreshBudget(today)
    return StudyStreak(count, freezes, today in studyDays, frozen)
}
