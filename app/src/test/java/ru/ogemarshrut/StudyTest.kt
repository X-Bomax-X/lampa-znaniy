package ru.ogemarshrut

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class StudyTest {
    private val testCourse = readSubject(File("src/debug/assets/subjects/test.json").readText())

    @Test
    fun checksEveryExerciseFormat() {
        val (single, multiple, text, number, match) = testCourse.lessons.first().questions
        assertTrue(single.isCorrect(Answer(selected = setOf(1))))
        assertFalse(single.isCorrect(Answer(selected = setOf(0))))
        assertFalse(single.isReady(Answer()))
        assertTrue(multiple.isCorrect(Answer(selected = setOf(0, 3))))
        assertFalse(multiple.isCorrect(Answer(selected = setOf(0))))
        assertTrue(text.isCorrect(Answer(text = " СУММА ")))
        assertFalse(text.isCorrect(Answer(text = "сумм")))
        assertTrue(number.isCorrect(Answer(text = "1,50")))
        assertFalse(number.isReady(Answer(text = "один")))
        assertTrue(testCourse.lessons[1].questions[0].isCorrect(Answer(text = "−3")))

        val pairs = Answer().link(0, 2).link(1, 2)
        assertEquals(mapOf(1 to 2), pairs.pairs)
        assertFalse(match.isReady(pairs))
        assertTrue(match.isCorrect(pairs.link(0, 1).link(2, 0)))
        assertFalse(match.isCorrect(Answer(pairs = mapOf(0 to 0, 1 to 1, 2 to 2))))
    }

    @Test
    fun progressUnlocksPersistsAndDoesNotAwardExperienceTwice() {
        val first = testCourse.lessons.first()
        val question = first.questions.first()
        var progress = Progress()
        assertTrue(progress.unlocks(testCourse, 0))
        assertFalse(progress.unlocks(testCourse, 1))

        progress = progress.record(question.id, false)
        assertEquals(0, progress.xp)
        assertTrue(question.id in progress.mistakes)
        progress = progress.finish(first.id).record(question.id, true)
        assertTrue(progress.unlocks(testCourse, 1))
        assertFalse(question.id in progress.mistakes)
        assertEquals(10, progress.xp)

        progress = progress.record(question.id, false).record(question.id, true).finish(first.id)
        assertEquals(10, progress.xp)
        assertEquals(progress, decodeProgress(progress.encode()))
    }

    @Test
    fun allBundledQuestionsHaveAnAnswerThatCanBeChecked() {
        val catalog = File("src/main/assets/subjects").listFiles { file -> file.extension == "json" }!!
            .map { readSubject(it.readText()) }
        assertEquals(14, catalog.size)
        assertEquals(14, catalog.map { it.id }.toSet().size)
        assertTrue(catalog.all { it.lessons.size == 10 })
        assertTrue(catalog.flatMap { it.lessons }.all { it.questions.size == 5 })
        val questions = catalog.flatMap { it.lessons }.flatMap { it.questions }
        assertEquals(700, questions.map { it.id }.toSet().size)
        for (item in questions) {
            val answer = when (item.type) {
                QuestionType.SINGLE, QuestionType.MULTIPLE -> Answer(selected = item.correctChoices)
                QuestionType.TEXT, QuestionType.NUMBER -> Answer(text = item.accepted.first())
                QuestionType.MATCH -> Answer(pairs = item.matches.withIndex().associate { it.index to it.value })
            }
            assertTrue(item.id, item.isCorrect(answer))
            assertTrue(item.id, item.explanation.isNotBlank())
            assertTrue(item.id, item.solution.isNotBlank())
        }
    }
}
