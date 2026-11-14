package ru.ogemarshrut

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.text.Normalizer
import java.util.Locale

data class Subject(val id: String, val title: String, val lessons: List<Lesson>)
data class Lesson(val id: String, val title: String, val questions: List<Question>)

enum class QuestionType(val label: String) {
    SINGLE("Выбери один вариант"),
    MULTIPLE("Выбери все подходящие варианты"),
    TEXT("Введи короткий ответ"),
    NUMBER("Введи число"),
    MATCH("Сопоставь элементы")
}

data class Question(
    val id: String,
    val type: QuestionType,
    val prompt: String,
    val choices: List<String>,
    val left: List<String>,
    val correctChoices: Set<Int>,
    val accepted: List<String>,
    val matches: List<Int>,
    val explanation: String
) {
    val solution: String
        get() = when (type) {
            QuestionType.SINGLE, QuestionType.MULTIPLE ->
                correctChoices.sorted().joinToString("; ") { choices[it] }
            QuestionType.MATCH -> left.indices.joinToString("\n") { "${left[it]} → ${choices[matches[it]]}" }
            else -> accepted.first()
        }

    fun isReady(answer: Answer): Boolean = when (type) {
        QuestionType.SINGLE -> answer.selected.size == 1
        QuestionType.MULTIPLE -> answer.selected.isNotEmpty()
        QuestionType.TEXT -> answer.text.isNotBlank()
        QuestionType.NUMBER -> parseNumber(answer.text) != null
        QuestionType.MATCH -> left.indices.all { it in answer.pairs }
    }

    fun isCorrect(answer: Answer): Boolean = when (type) {
        QuestionType.SINGLE, QuestionType.MULTIPLE -> answer.selected == correctChoices
        QuestionType.TEXT -> accepted.any { normalizeText(it) == normalizeText(answer.text) }
        QuestionType.NUMBER -> {
            val input = parseNumber(answer.text)
            input != null && accepted.any { parseNumber(it)?.compareTo(input) == 0 }
        }
        QuestionType.MATCH -> matches.withIndex().all { (index, choice) -> answer.pairs[index] == choice }
    }
}

data class Answer(
    val text: String = "",
    val selected: Set<Int> = emptySet(),
    val pairs: Map<Int, Int> = emptyMap()
) {
    fun link(left: Int, right: Int): Answer =
        copy(pairs = pairs.filterValues { it != right } + (left to right))
}

private fun normalizeText(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFC).trim()
        .replace(Regex("[\\p{Z}\\s]+"), " ")
        .replace('’', '\'')
        .lowercase(Locale.ROOT)

private fun parseNumber(value: String): BigDecimal? =
    value.trim().replace('−', '-').replace(',', '.').toBigDecimalOrNull()

fun readSubject(json: String): Subject {
    val subject = JSONObject(json)
    val id = subject.getString("id")
    val lessons = subject.getJSONArray("lessons").objects().map { lesson ->
        val lessonId = "$id/${lesson.getString("id")}"
        Lesson(lessonId, lesson.getString("title"), lesson.getJSONArray("questions").objects().map { item ->
            Question(
                id = "$lessonId/${item.getString("id")}",
                type = QuestionType.valueOf(item.getString("type").uppercase(Locale.ROOT)),
                prompt = item.getString("prompt"),
                choices = item.optJSONArray("choices").strings(),
                left = item.optJSONArray("left").strings(),
                correctChoices = item.optJSONArray("correct").ints().toSet(),
                accepted = item.optJSONArray("accepted").strings(),
                matches = item.optJSONArray("matches").ints(),
                explanation = item.getString("explanation")
            )
        })
    }
    return Subject(id, subject.getString("title"), lessons)
}

private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
private fun JSONArray?.strings() = if (this == null) emptyList() else List(length()) { getString(it) }
private fun JSONArray?.ints() = if (this == null) emptyList() else List(length()) { getInt(it) }
