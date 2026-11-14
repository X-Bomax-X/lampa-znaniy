package ru.ogemarshrut

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Progress(
    val completedLessons: Set<String> = emptySet(),
    val solved: Set<String> = emptySet(),
    val mistakes: Set<String> = emptySet(),
    val studyDays: Set<Long> = emptySet()
) {
    val xp get() = solved.size * 10

    fun record(questionId: String, correct: Boolean): Progress =
        if (correct) copy(solved = solved + questionId, mistakes = mistakes - questionId)
        else copy(mistakes = mistakes + questionId)

    fun unlocks(subject: Subject, index: Int): Boolean =
        index in subject.lessons.indices && (
            index == 0 || subject.lessons[index].id in completedLessons ||
                subject.lessons[index - 1].id in completedLessons
        )

    fun finish(lessonId: String?): Progress =
        if (lessonId == null) this else copy(completedLessons = completedLessons + lessonId)

    fun practice(day: Long): Progress = copy(studyDays = studyDays + day)
}

internal fun Progress.encode(): String = JSONObject()
    .put("completed", JSONArray(completedLessons.toList()))
    .put("solved", JSONArray(solved.toList()))
    .put("mistakes", JSONArray(mistakes.toList()))
    .put("studyDays", JSONArray(studyDays.sorted())).toString()

internal fun lessonAvailable(subject: Subject, index: Int, progress: Progress, unlockAll: Boolean): Boolean =
    if (unlockAll) index in subject.lessons.indices else progress.unlocks(subject, index)

internal fun decodeProgress(value: String): Progress {
    val json = JSONObject(value)
    fun entries(key: String): Set<String> {
        val array = json.optJSONArray(key) ?: return emptySet()
        return List(array.length()) { array.getString(it) }.toSet()
    }
    val studyDays = json.optJSONArray("studyDays")?.let { array ->
        List(array.length()) { array.getLong(it) }.toSet()
    }.orEmpty()
    return Progress(entries("completed"), entries("solved"), entries("mistakes"), studyDays)
}

internal class ProgressStore(context: Context) {
    private val storage = context.getSharedPreferences("oge_progress", Context.MODE_PRIVATE)

    fun read(): Progress = storage.getString("state", null)?.let {
        runCatching { decodeProgress(it) }.getOrDefault(Progress())
    } ?: Progress()

    fun save(progress: Progress): Boolean =
        storage.edit().putString("state", progress.encode()).commit()

    fun readSubjects(): Set<String> =
        storage.getStringSet("selected_subjects", emptySet()).orEmpty().toSet()

    fun saveSubjects(ids: Set<String>): Boolean =
        storage.edit().putStringSet("selected_subjects", ids).commit()

    fun readSoundEnabled(): Boolean = storage.getBoolean("sound_enabled", true)

    fun saveSoundEnabled(enabled: Boolean): Boolean =
        storage.edit().putBoolean("sound_enabled", enabled).commit()

    fun readDarkTheme(): Boolean = storage.getBoolean("dark_theme", false)

    fun saveDarkTheme(enabled: Boolean): Boolean =
        storage.edit().putBoolean("dark_theme", enabled).commit()

    fun readUnlockAll(): Boolean = storage.getBoolean("unlock_all", false)

    fun saveUnlockAll(enabled: Boolean): Boolean =
        storage.edit().putBoolean("unlock_all", enabled).commit()

    fun readReminders(): Boolean = storage.getBoolean("reminders", true)

    fun saveReminders(enabled: Boolean): Boolean =
        storage.edit().putBoolean("reminders", enabled).commit()

    fun markNotificationPromptShown(): Boolean =
        !storage.getBoolean("notification_prompt_shown", false) &&
            storage.edit().putBoolean("notification_prompt_shown", true).commit()

    fun readReminderDay(reminder: Reminder): Long =
        storage.getLong("reminder_day_${reminder.name}", Long.MIN_VALUE)

    fun saveReminderDay(reminder: Reminder, day: Long) {
        storage.edit().putLong("reminder_day_${reminder.name}", day).apply()
    }

    fun reset(): Boolean = storage.edit().clear().commit()
}
