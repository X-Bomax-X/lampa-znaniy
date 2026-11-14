package ru.ogemarshrut

import android.app.Application
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StudyFlowTest {
    @Test
    fun completesLessonReopensProgressAndCorrectsAMistakeInAirplaneMode() {
        val application: Application = RuntimeEnvironment.getApplication()
        Settings.Global.putInt(application.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 1)
        assertFalse("android.permission.INTERNET" in (application.packageManager
            .getPackageInfo(application.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
            .requestedPermissions?.toList().orEmpty()))

        val model = StudyModel(application)
        assertEquals(15, model.subjects.getOrThrow().size)
        val subject = model.subjects.getOrThrow().first { it.id == "test" }
        model.open(subject)
        model.start(subject, 1)
        assertNull(model.session)
        model.start(subject, 0)
        val answers = listOf(
            Answer(selected = setOf(0)), Answer(selected = setOf(0, 3)), Answer(text = "сумма"),
            Answer(text = "1,5"), Answer(pairs = mapOf(0 to 1, 1 to 2, 2 to 0))
        )
        for (answer in answers) {
            model.answer(answer)
            model.advance()
            assertTrue(model.session!!.checked)
            model.advance()
        }
        assertTrue(model.session!!.finished)
        assertEquals(1, model.progress.studyDays.size)
        assertEquals(4, model.session!!.correctCount)
        assertEquals(40, model.progress.xp)

        val restarted = StudyModel(application)
        assertTrue(restarted.progress.unlocks(subject, 1))
        assertEquals(40, restarted.progress.xp)
        assertEquals(1, restarted.mistakes.size)
        restarted.selectTab(Tab.MISTAKES)
        restarted.review()
        restarted.answer(Answer(selected = setOf(1)))
        restarted.advance()
        restarted.advance()
        assertTrue(restarted.session!!.finished)
        assertTrue(restarted.mistakes.isEmpty())
        assertEquals(50, restarted.progress.xp)
        assertEquals(1, restarted.progress.studyDays.size)
        restarted.advance()

        restarted.open(subject)
        restarted.start(subject, 0)
        restarted.answer(Answer(selected = setOf(1)))
        restarted.advance()
        assertEquals(50, StudyModel(application).progress.xp)
    }

    @Test
    fun answerFeedbackIsEmittedOnlyWhenAnAnswerIsChecked() {
        val model = StudyModel(RuntimeEnvironment.getApplication())
        val subject = model.subjects.getOrThrow().first { it.id == "test" }
        model.start(subject, 0)
        assertNull(model.advance())
        model.answer(Answer(selected = setOf(0)))
        assertEquals(AnswerOutcome.INCORRECT, model.advance())
        assertNull(model.advance())
        model.answer(Answer(selected = setOf(0, 3)))
        assertEquals(AnswerOutcome.CORRECT, model.advance())
    }

    @Test
    fun firstLaunchSavesSubjectSelectionAndLaterLaunchSkipsWelcome() {
        val application: Application = RuntimeEnvironment.getApplication()
        val model = StudyModel(application)
        assertEquals(StartupScreen.WELCOME, model.startup)
        assertFalse(model.saveSubjects(emptySet()))
        assertTrue(model.saveSubjects(setOf("math", "russian")))
        assertEquals(StartupScreen.LOADING, model.startup)
        assertEquals(listOf("math", "russian"), model.visibleSubjects.map { it.id })
        model.finishLoading()
        assertEquals(StartupScreen.STUDY, model.startup)
        val restarted = StudyModel(application)
        assertEquals(StartupScreen.LOADING, restarted.startup)
        assertEquals(setOf("math", "russian"), restarted.selectedSubjects)
    }

    @Test
    fun hiddenSubjectKeepsItsProgressAndCanBeRestored() {
        val model = StudyModel(RuntimeEnvironment.getApplication())
        val math = model.subjects.getOrThrow().first { it.id == "math" }
        model.start(math, 0)
        model.answer(Answer(text = "-0.75"))
        assertEquals(AnswerOutcome.CORRECT, model.advance())
        assertTrue(model.saveSubjects(setOf("russian")))
        model.finishLoading()
        assertEquals(listOf("russian"), model.visibleSubjects.map { it.id })
        assertTrue(model.saveSubjects(setOf("math")))
        assertEquals(StartupScreen.STUDY, model.startup)
        assertEquals(10, model.progress.xp)
    }

    @Test
    fun soundPreferenceIsSavedAcrossLaunches() {
        val application: Application = RuntimeEnvironment.getApplication()
        val model = StudyModel(application)
        assertTrue(model.soundEnabled)
        assertTrue(model.setSound(false))
        assertFalse(StudyModel(application).soundEnabled)
        assertTrue(model.setSound(true))
        assertTrue(StudyModel(application).soundEnabled)
    }

    @Test
    fun themePreferenceIsSavedAcrossLaunches() {
        val application: Application = RuntimeEnvironment.getApplication()
        val model = StudyModel(application)
        assertFalse(model.darkTheme)
        assertTrue(model.setDarkTheme(true))
        assertTrue(model.darkTheme)
        val restarted = StudyModel(application)
        assertTrue(restarted.darkTheme)
        assertTrue(restarted.setDarkTheme(false))
        assertFalse(StudyModel(application).darkTheme)
    }

    @Test
    fun unlockingLessonsIsReversibleAndDoesNotAwardProgress() {
        val application: Application = RuntimeEnvironment.getApplication()
        val model = StudyModel(application)
        val subject = model.subjects.getOrThrow().first { it.id == "math" }
        val last = subject.lessons.lastIndex
        assertFalse(model.canStart(subject, last))
        assertTrue(model.setUnlockAll(true))
        val restarted = StudyModel(application)
        assertTrue(restarted.canStart(subject, last))
        restarted.start(subject, last)
        assertEquals(subject.lessons.last().id, restarted.session?.lessonId)
        assertEquals(Progress(), restarted.progress)
        restarted.back()
        assertTrue(restarted.setUnlockAll(false))
        restarted.start(subject, last)
        assertNull(restarted.session)
        assertTrue(restarted.canStart(subject, 0))
    }

    @Test
    fun resetClearsProgressAndPreferencesAndReopensFirstLaunch() {
        val application: Application = RuntimeEnvironment.getApplication()
        val model = StudyModel(application)
        val math = model.subjects.getOrThrow().first { it.id == "math" }
        assertTrue(model.saveSubjects(setOf("math")))
        model.finishLoading()
        assertTrue(model.setSound(false))
        assertTrue(model.setUnlockAll(true))
        assertTrue(model.setDarkTheme(true))
        model.open(math)
        model.start(math, 0)
        model.answer(Answer(text = "1"))
        assertEquals(AnswerOutcome.INCORRECT, model.advance())
        assertEquals(1, model.mistakes.size)
        model.advance()
        model.answer(Answer(text = "0.875"))
        assertEquals(AnswerOutcome.CORRECT, model.advance())
        assertEquals(10, model.progress.xp)
        assertEquals(1, model.mistakes.size)
        assertTrue(model.reset())
        assertEquals(StartupScreen.WELCOME, model.startup)
        assertNull(model.session)
        assertNull(model.subject)
        assertEquals(Tab.SUBJECTS, model.tab)
        val restarted = StudyModel(application)
        assertEquals(StartupScreen.WELCOME, restarted.startup)
        assertEquals(Progress(), restarted.progress)
        assertTrue(restarted.selectedSubjects.isEmpty())
        assertTrue(restarted.soundEnabled)
        assertFalse(restarted.unlockAll)
        assertFalse(model.darkTheme)
        assertFalse(restarted.darkTheme)
    }

    @Test
    fun startsTheRealActivityAndLoadsTheCatalog() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            shadowOf(Looper.getMainLooper()).idle()
            val factory = ViewModelProvider(controller.get())
            val model = factory[StudyModel::class.java]
            assertEquals("Тестовый предмет", model.subjects.getOrThrow().first().title)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun tabButtonsKeepTheirPositionWhileThePageSlidesInBothDirections() {
        val application: Application = RuntimeEnvironment.getApplication()
        assertTrue(ProgressStore(application).saveSubjects(setOf("math")))
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            val model = ViewModelProvider(activity)[StudyModel::class.java]
            assertEquals("Лампа знаний", activity.applicationInfo.loadLabel(activity.packageManager).toString())
            val looper = shadowOf(Looper.getMainLooper())
            val decor = activity.window.decorView
            val root = checkNotNull(composeRoot(decor))
            fun layout() {
                decor.measure(
                    View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY)
                )
                decor.layout(0, 0, 540, 960)
                root.measureAndLayoutForTest()
            }
            fun tab(label: String) = root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true).single { node ->
                node.config.getOrNull(SemanticsActions.OnClick) != null &&
                    node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true
            }
            layout()
            model.finishLoading()
            looper.idleFor(Duration.ofMillis(720))
            layout()
            assertTrue(root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true).any { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Лампа знаний" } == true
            })
            val subjects = tab("Предметы").let { it.id to it.boundsInRoot }
            val mistakes = tab("Ошибки").let { it.id to it.boundsInRoot }
            assertTrue(subjects.second.width > 0f && mistakes.second.width > 0f)
            for (destination in listOf("Ошибки", "Предметы")) {
                assertTrue(tab(destination).config[SemanticsActions.OnClick].action!!.invoke())
                looper.idleFor(Duration.ofMillis(150))
                layout()
                assertEquals(subjects, tab("Предметы").let { it.id to it.boundsInRoot })
                assertEquals(mistakes, tab("Ошибки").let { it.id to it.boundsInRoot })
                assertEquals(if (destination == "Ошибки") Tab.MISTAKES else Tab.SUBJECTS, model.tab)
                looper.idleFor(Duration.ofMillis(400))
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun composeRoot(view: View): RootForTest? {
        if (view is RootForTest) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                composeRoot(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }
}
