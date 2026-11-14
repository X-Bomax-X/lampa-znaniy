package ru.ogemarshrut

import android.app.Application
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay

private val Round = RoundedCornerShape(17.dp)
private const val ScreenSlideMillis = 360
private val StudyTabsHeight = 80.dp

class MainActivity : ComponentActivity() {
    private var answerSounds: AnswerSounds? = null
    private lateinit var model: StudyModel
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshReminders(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        answerSounds = AnswerSounds(this)
        val factory = viewModelFactory { initializer { StudyModel(application) } }
        model = ViewModelProvider(this, factory)[StudyModel::class.java]
        val barStyle = if (model.darkTheme) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, 0x80222657.toInt())
        enableEdgeToEdge(
            statusBarStyle = barStyle,
            navigationBarStyle = barStyle
        )
        fun updateWindowTheme(dark: Boolean) {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(studyColors(dark).background.toArgb()))
        }
        updateWindowTheme(model.darkTheme)
        setContent {
            val dark = model.darkTheme
            LaunchedEffect(dark) { updateWindowTheme(dark) }
            LaunchedEffect(model.startup, model.remindersEnabled) {
                if (model.startup == StartupScreen.STUDY && model.remindersEnabled &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                    ProgressStore(this@MainActivity).markNotificationPromptShown()
                ) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            StudyApp(model, playAnswer = { answerSounds?.play(it) }, stopAnswer = { answerSounds?.stop() })
        }
    }

    override fun onResume() {
        super.onResume()
        model.refreshStudyDay()
        refreshReminders(this)
    }

    override fun onStop() {
        answerSounds?.stop()
        super.onStop()
    }
}

internal enum class Tab { SUBJECTS, MISTAKES }
internal enum class AnswerOutcome { CORRECT, INCORRECT }
internal enum class StartupScreen { WELCOME, LOADING, STUDY }

internal data class Session(
    val title: String,
    val questions: List<Question>,
    val lessonId: String? = null,
    val index: Int = 0,
    val answer: Answer = Answer(),
    val checked: Boolean = false,
    val correctCount: Int = 0,
    val startXp: Int,
    val finished: Boolean = false
) {
    val question get() = questions[index]
}

internal class StudyModel(application: Application) : AndroidViewModel(application) {
    private val store = ProgressStore(application)
    private val assets = application.assets
    val subjects = runCatching {
        assets.list("subjects").orEmpty().filter { it.endsWith(".json") }.map { name ->
            assets.open("subjects/$name").bufferedReader().use { readSubject(it.readText()) }
        }.sortedBy { order.indexOf(it.id).let { index -> if (index < 0) Int.MAX_VALUE else index } }
    }
    private val subjectIds = subjects.getOrNull().orEmpty().map { it.id }.toSet()
    var selectedSubjects by mutableStateOf(store.readSubjects().intersect(subjectIds))
        private set
    var startup by mutableStateOf(
        if (subjectIds.isNotEmpty() && selectedSubjects.isEmpty()) StartupScreen.WELCOME else StartupScreen.LOADING
    )
        private set
    var loadingStartedAt by mutableLongStateOf(SystemClock.elapsedRealtime())
        private set
    val visibleSubjects get() = subjects.getOrNull().orEmpty().filter { it.id in selectedSubjects }

    fun saveSubjects(ids: Set<String>): Boolean {
        if (ids.isEmpty() || !subjectIds.containsAll(ids) || !store.saveSubjects(ids)) return false
        selectedSubjects = ids.toSet()
        if (startup == StartupScreen.WELCOME) {
            loadingStartedAt = SystemClock.elapsedRealtime()
            startup = StartupScreen.LOADING
        }
        return true
    }

    fun finishLoading() {
        if (startup == StartupScreen.LOADING) startup = StartupScreen.STUDY
    }

    var progress by mutableStateOf(store.read())
        private set
    var tab by mutableStateOf(Tab.SUBJECTS)
        private set
    var subject by mutableStateOf<Subject?>(null)
        private set
    var session by mutableStateOf<Session?>(null)
        private set
    var storageError by mutableStateOf(false)
        private set
    var soundEnabled by mutableStateOf(store.readSoundEnabled())
        private set
    var darkTheme by mutableStateOf(store.readDarkTheme())
        private set
    var unlockAll by mutableStateOf(store.readUnlockAll())
        private set
    var remindersEnabled by mutableStateOf(store.readReminders())
        private set
    var today by mutableLongStateOf(studyDay())
        private set
    val streak by derivedStateOf { studyStreak(progress.studyDays, today) }

    fun refreshStudyDay() {
        today = studyDay()
    }

    fun setReminders(enabled: Boolean): Boolean {
        if (!store.saveReminders(enabled)) return false
        remindersEnabled = enabled
        refreshReminders(getApplication())
        return true
    }

    fun setSound(enabled: Boolean): Boolean {
        if (!store.saveSoundEnabled(enabled)) return false
        soundEnabled = enabled
        return true
    }

    fun setDarkTheme(enabled: Boolean): Boolean {
        if (!store.saveDarkTheme(enabled)) return false
        darkTheme = enabled
        return true
    }

    fun setUnlockAll(enabled: Boolean): Boolean {
        if (!store.saveUnlockAll(enabled)) return false
        unlockAll = enabled
        return true
    }

    fun reset(): Boolean {
        if (!store.reset()) return false
        progress = Progress()
        selectedSubjects = emptySet()
        soundEnabled = true
        darkTheme = false
        unlockAll = false
        remindersEnabled = store.readReminders()
        refreshReminders(getApplication())
        tab = Tab.SUBJECTS
        subject = null
        session = null
        storageError = false
        startup = StartupScreen.WELCOME
        return true
    }

    val mistakes get() = subjects.getOrNull().orEmpty().flatMap { subject ->
        subject.lessons.flatMap { lesson -> lesson.questions.filter { it.id in progress.mistakes } }
    }

    fun selectTab(value: Tab) {
        subject = null
        tab = value
    }

    fun open(subject: Subject) { this.subject = subject }

    fun canStart(subject: Subject, index: Int): Boolean =
        lessonAvailable(subject, index, progress, unlockAll)

    fun start(subject: Subject, index: Int) {
        if (!canStart(subject, index)) return
        val lesson = subject.lessons[index]
        session = Session(lesson.title, lesson.questions, lesson.id, startXp = progress.xp)
    }

    fun review() {
        val questions = mistakes.take(5)
        if (questions.isNotEmpty()) session = Session("Работа над ошибками", questions, startXp = progress.xp)
    }

    fun practiceFromStreak() {
        if (session?.finished == false) return
        val course = subject ?: visibleSubjects.firstOrNull() ?: return
        val next = course.lessons.indices.firstOrNull {
            canStart(course, it) && course.lessons[it].id !in progress.completedLessons
        } ?: 0
        selectTab(Tab.SUBJECTS)
        open(course)
        start(course, next)
    }

    fun answer(answer: Answer) {
        session = session?.takeUnless { it.checked }?.copy(answer = answer) ?: session
    }

    fun advance(): AnswerOutcome? {
        val current = session ?: return null
        if (current.finished) {
            session = null
            return null
        }
        if (!current.checked) {
            if (!current.question.isReady(current.answer)) return null
            val correct = current.question.isCorrect(current.answer)
            if (!save(progress.record(current.question.id, correct))) return null
            session = current.copy(checked = true, correctCount = current.correctCount + if (correct) 1 else 0)
            return if (correct) AnswerOutcome.CORRECT else AnswerOutcome.INCORRECT
        } else if (current.index == current.questions.lastIndex) {
            refreshStudyDay()
            if (!save(progress.finish(current.lessonId).practice(today))) return null
            androidx.core.app.NotificationManagerCompat.from(getApplication()).cancel(Reminder.STREAK.notificationId)
            session = current.copy(finished = true)
        } else {
            session = current.copy(index = current.index + 1, answer = Answer(), checked = false)
        }
        return null
    }

    fun back() {
        if (session != null) session = null
        else if (subject != null) subject = null
        else tab = Tab.SUBJECTS
    }

    private fun save(next: Progress): Boolean {
        val saved = store.save(next)
        storageError = !saved
        if (saved) progress = next
        return saved
    }

    companion object {
        private val order = listOf("test", "math", "russian", "informatics", "physics", "chemistry",
            "biology", "history", "geography", "social", "literature", "english", "german", "french", "spanish")
    }
}

@Composable
private fun StudyApp(model: StudyModel, playAnswer: (AnswerOutcome) -> Unit, stopAnswer: () -> Unit) {
    LaunchedEffect(model) {
        while (true) {
            model.refreshStudyDay()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    MaterialTheme(colorScheme = studyColors(model.darkTheme)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            SlidingScreen(model.startup, position = { it.ordinal }, screenKey = { it }) { screen, active ->
                when (screen) {
                    StartupScreen.WELCOME -> SubjectSelection(
                        model.subjects.getOrThrow(), emptySet(), save = { active && model.saveSubjects(it) }
                    )
                    StartupScreen.LOADING -> LoadingScreen(model.loadingStartedAt, model::finishLoading)
                    StartupScreen.STUDY -> StudyHome(model, playAnswer, stopAnswer, active)
                }
            }
        }
    }
}

private sealed class StudyPage(val key: String, val position: Int) {
    data object Catalog : StudyPage("catalog", 0)
    data object Mistakes : StudyPage("mistakes", 1)
    data object EditSubjects : StudyPage("edit-subjects", 2)
    data class Map(val subject: Subject) : StudyPage("map-${subject.id}", 2)
    data class Exercise(val session: Session) : StudyPage("exercise-${session.index}", 3 + session.index)
    data class Result(val session: Session) : StudyPage("result", 3 + session.questions.size)

    val showsTabs get() = this == Catalog || this == Mistakes
}

@Composable
private fun <T> SlidingScreen(
    target: T,
    position: (T) -> Int,
    screenKey: (T) -> Any,
    modifier: Modifier = Modifier,
    content: @Composable (T, Boolean) -> Unit
) {
    AnimatedContent(
        targetState = target,
        contentKey = screenKey,
        modifier = modifier.fillMaxSize().clipToBounds(),
        transitionSpec = {
            val forward = position(targetState) >= position(initialState)
            val direction = if (forward) SlideDirection.Left else SlideDirection.Right
            val motion = tween<IntOffset>(ScreenSlideMillis, easing = FastOutSlowInEasing)
            slideIntoContainer(direction, motion) togetherWith slideOutOfContainer(direction, motion)
        },
        label = "Переход между экранами"
    ) { screen ->
        val active = screenKey(screen) == screenKey(target)
        val inactive = if (active) Modifier else Modifier.semantics { hideFromAccessibility() }.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).then(inactive)) {
            content(screen, active)
        }
    }
}

@Composable
private fun StudyHome(
    model: StudyModel,
    playAnswer: (AnswerOutcome) -> Unit,
    stopAnswer: () -> Unit,
    active: Boolean
) {
    val session = model.session
    val subject = model.subject
    var editingSubjects by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var streakOpen by rememberSaveable { mutableStateOf(false) }
    val lists = rememberSaveableStateHolder()
    val focus = LocalFocusManager.current
    var reaction by remember { mutableStateOf<AnswerOutcome?>(null) }
    fun advance() {
        reaction = model.advance()
        if (model.soundEnabled) reaction?.let(playAnswer)
    }
    fun back() {
        focus.clearFocus()
        reaction = null
        if (editingSubjects) editingSubjects = false else model.back()
    }
    val page = when {
        editingSubjects -> StudyPage.EditSubjects
        session?.finished == true -> StudyPage.Result(session)
        session != null -> StudyPage.Exercise(session)
        subject != null -> StudyPage.Map(subject)
        model.tab == Tab.MISTAKES -> StudyPage.Mistakes
        else -> StudyPage.Catalog
    }
    BackHandler(active && !settingsOpen && !streakOpen && page != StudyPage.Catalog, onBack = ::back)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().then(
            if (streakOpen) Modifier.semantics { hideFromAccessibility() } else Modifier
        )) {
            SlidingScreen(page, StudyPage::position, StudyPage::key) { shown, isCurrent ->
                if (shown == StudyPage.EditSubjects) {
                    SubjectSelection(model.subjects.getOrThrow(), model.selectedSubjects, save = {
                        isCurrent && model.saveSubjects(it).also { saved -> if (saved) editingSubjects = false }
                    }, cancel = { if (isCurrent) back() })
                } else {
                    StudyScreen(
                        shown = shown,
                        model = model,
                        lists = lists,
                        reaction = if (isCurrent) reaction else null,
                        back = { if (isCurrent) back() },
                        settings = {
                            if (isCurrent && active) {
                                focus.clearFocus()
                                settingsOpen = true
                            }
                        },
                        edit = { if (isCurrent) editingSubjects = true },
                        openStreak = {
                            if (isCurrent && active) {
                                focus.clearFocus()
                                model.refreshStudyDay()
                                streakOpen = true
                            }
                        },
                        advance = { if (isCurrent) advance() }
                    )
                }
            }
            AnimatedVisibility(
                visible = page.showsTabs,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(ScreenSlideMillis, easing = FastOutSlowInEasing)) { it },
                exit = slideOutVertically(tween(ScreenSlideMillis, easing = FastOutSlowInEasing)) { it }
            ) {
                StudyTabs(model.tab) { if (active && page.showsTabs) model.selectTab(it) }
            }
        }
        AnimatedVisibility(
            visible = active && streakOpen,
            modifier = Modifier.fillMaxSize(),
            enter = slideInVertically(tween(400, easing = FastOutSlowInEasing)) { -it },
            exit = slideOutVertically(tween(360, easing = FastOutSlowInEasing)) { -it }
        ) {
            StreakScreen(
                streak = model.streak,
                studyDays = model.progress.studyDays,
                today = model.today,
                close = { streakOpen = false },
                study = { model.practiceFromStreak(); streakOpen = false },
                settings = {
                    streakOpen = false
                    settingsOpen = true
                }
            )
        }
    }
    BackHandler(active && streakOpen && !settingsOpen) { streakOpen = false }
    if (active && settingsOpen) {
        SettingsSheet(
            model = model,
            dismiss = { settingsOpen = false },
            stopAnswer = stopAnswer,
            reset = {
                model.reset().also { saved ->
                    if (saved) {
                        stopAnswer()
                        settingsOpen = false
                        streakOpen = false
                        editingSubjects = false
                        reaction = null
                        lists.removeState(StudyPage.Catalog.key)
                        model.subjects.getOrNull().orEmpty().forEach {
                            lists.removeState(StudyPage.Map(it).key)
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun StudyScreen(
    shown: StudyPage,
    model: StudyModel,
    lists: SaveableStateHolder,
    reaction: AnswerOutcome?,
    back: () -> Unit,
    settings: () -> Unit,
    edit: () -> Unit,
    openStreak: () -> Unit,
    advance: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().imePadding().padding(bottom = if (shown.showsTabs) StudyTabsHeight else 0.dp)
    ) {
        Header(
            title = when (shown) {
                is StudyPage.Exercise -> shown.session.title
                is StudyPage.Result -> shown.session.title
                is StudyPage.Map -> shown.subject.title
                else -> stringResource(R.string.app_name)
            },
            xp = model.progress.xp,
            streak = model.streak,
            back = if (shown.showsTabs) null else back,
            reaction = reaction,
            settings = settings,
            openStreak = openStreak
        )
        if (model.storageError) {
            Text("Не удалось сохранить прогресс. Попробуй ещё раз.",
                Modifier.padding(horizontal = 20.dp), color = colors.error, style = MaterialTheme.typography.bodyMedium)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (shown) {
                is StudyPage.Result -> ResultScreen(shown.session, model.progress.xp, advance)
                is StudyPage.Exercise -> ExerciseScreen(shown.session, model::answer, advance)
                is StudyPage.Map -> lists.SaveableStateProvider(shown.key) {
                    LessonMap(shown.subject, model.progress, model.unlockAll) { model.start(shown.subject, it) }
                }
                StudyPage.Mistakes -> MistakesScreen(model.mistakes.size, model::review)
                StudyPage.Catalog -> lists.SaveableStateProvider(shown.key) {
                    model.subjects.fold(
                        { Catalog(model.visibleSubjects, model.progress, model::open, edit) },
                        { Text("Не удалось открыть список предметов.", Modifier.padding(24.dp)) }
                    )
                }
                StudyPage.EditSubjects -> Unit
            }
        }
    }
}

@Composable
private fun StudyTabs(tab: Tab, select: (Tab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tabColors = NavigationBarItemDefaults.colors(
        selectedIconColor = colors.onPrimaryContainer,
        selectedTextColor = colors.onSurface,
        indicatorColor = colors.primaryContainer,
        unselectedIconColor = colors.onSurfaceVariant,
        unselectedTextColor = colors.onSurfaceVariant
    )
    NavigationBar(
        modifier = Modifier.height(StudyTabsHeight),
        containerColor = colors.background, tonalElevation = 0.dp, windowInsets = WindowInsets(0)
    ) {
        NavigationBarItem(
            selected = tab == Tab.SUBJECTS, onClick = { select(Tab.SUBJECTS) },
            icon = { Text("⌘", fontSize = 21.sp) }, label = { Text("Предметы") },
            colors = tabColors
        )
        NavigationBarItem(
            selected = tab == Tab.MISTAKES, onClick = { select(Tab.MISTAKES) },
            icon = { Text("↻", fontSize = 25.sp) }, label = { Text("Ошибки") },
            colors = tabColors
        )
    }
}

@Composable
private fun Header(
    title: String, xp: Int, streak: StudyStreak, back: (() -> Unit)?, reaction: AnswerOutcome?,
    settings: () -> Unit, openStreak: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(46.dp).background(colors.tertiaryContainer, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Mascot(Modifier.size(44.dp), reaction = reaction)
        }
        if (back != null) {
            IconButton(onClick = back, modifier = Modifier.size(48.dp).semantics { contentDescription = "Назад" }) {
                Text("‹", fontSize = 32.sp, color = colors.primary)
            }
        }
        Text(title, Modifier.weight(1f), color = colors.onSurface, fontSize = 19.sp, fontWeight = FontWeight.Black,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        StreakButton(streak, openStreak)
        Text("✦ $xp XP", Modifier.background(colors.tertiaryContainer, RoundedCornerShape(10.dp)).padding(horizontal = 9.dp, vertical = 7.dp),
            color = colors.onTertiaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        SettingsButton(settings)
    }
}

@Composable
private fun Catalog(subjects: List<Subject>, progress: Progress, open: (Subject) -> Unit, edit: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val totalDone = subjects.sumOf { subject -> subject.lessons.count { it.id in progress.completedLessons } }
    LazyColumn(contentPadding = PaddingValues(20.dp, 10.dp, 20.dp, 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().background(colors.tertiaryContainer, Round).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ОГЭ 2027", color = colors.onTertiaryContainer, fontWeight = FontWeight.Black, letterSpacing = 1.sp, fontSize = 12.sp)
                    Text("Выбери предмет", color = colors.onSurface, fontSize = 27.sp,
                        lineHeight = 31.sp, fontWeight = FontWeight.Black)
                    Text("Пройдено уроков · $totalDone", color = colors.onSurfaceVariant, fontSize = 14.sp)
                }
                Mascot(Modifier.size(92.dp))
            }
        }
        item {
            TextButton(onClick = edit) { Text("Изменить предметы", fontWeight = FontWeight.Bold) }
        }
        itemsIndexed(subjects, key = { _, item -> item.id }) { index, subject ->
            val done = subject.lessons.count { it.id in progress.completedLessons }
            Row(Modifier.fillMaxWidth().clip(Round).background(colors.surface)
                .border(2.dp, colors.outlineVariant, Round).clickable(role = Role.Button) { open(subject) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(46.dp).background(listOf(colors.primaryContainer, colors.tertiaryContainer, colors.secondaryContainer)[index % 3],
                    RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Text(subjectSymbol(subject.id), color = colors.onSurface, fontWeight = FontWeight.Black, fontSize = 19.sp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(subject.title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("$done из ${subject.lessons.size} уроков", color = colors.onSurfaceVariant, fontSize = 13.sp)
                }
                Text("›", color = colors.onSurfaceVariant, fontSize = 26.sp)
            }
        }
    }
}

internal fun subjectSymbol(id: String): String = when (id) {
    "math" -> "π"
    "russian" -> "Я"
    "informatics" -> "01"
    "physics" -> "λ"
    "chemistry" -> "H₂O"
    "biology" -> "✿"
    "history" -> "⌛"
    "geography" -> "◎"
    "social" -> "§"
    "literature" -> "« »"
    "english" -> "EN"
    "german" -> "DE"
    "french" -> "FR"
    "spanish" -> "ES"
    else -> "✓"
}

@Composable
private fun LessonMap(subject: Subject, progress: Progress, unlockAll: Boolean, start: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val done = subject.lessons.count { it.id in progress.completedLessons }
    LazyColumn(contentPadding = PaddingValues(20.dp, 10.dp, 20.dp, 24.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(colors.primaryContainer, Round).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("Дорожка уроков", color = colors.onSurface, fontSize = 27.sp, fontWeight = FontWeight.Black)
                Text("Пройдено $done из ${subject.lessons.size}", color = colors.onSurfaceVariant, fontSize = 14.sp)
                ProgressBar(done, subject.lessons.size, colors.primary, colors.outlineVariant)
            }
            Spacer(Modifier.height(18.dp))
        }
        itemsIndexed(subject.lessons, key = { _, item -> item.id }) { index, lesson ->
            val completed = lesson.id in progress.completedLessons
            val unlocked = lessonAvailable(subject, index, progress, unlockAll)
            val stepColor = when { completed -> colors.secondaryContainer; unlocked -> colors.tertiary; else -> colors.outlineVariant }
            val stepTextColor = when { completed -> colors.onSecondaryContainer; unlocked -> colors.onTertiary; else -> colors.onSurfaceVariant }
            Box(Modifier.fillMaxWidth()) {
                Canvas(Modifier.matchParentSize()) {
                    val center = (if (index % 2 == 0) 35.dp else 59.dp).toPx()
                    val mid = 47.dp.toPx()
                    val end = Offset(center, size.height / 2)
                    if (index > 0) drawLine(colors.outlineVariant, Offset(mid, 0f), end, 3.dp.toPx())
                    if (index < subject.lessons.lastIndex) drawLine(colors.outlineVariant, end, Offset(mid, size.height), 3.dp.toPx())
                }
                Row(Modifier.fillMaxWidth().clip(Round)
                    .clickable(enabled = unlocked, role = Role.Button) { start(index) }
                    .padding(vertical = 7.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.padding(start = if (index % 2 == 0) 3.dp else 27.dp).size(56.dp)
                        .background(stepColor, CircleShape).border(1.dp, colors.outlineVariant, CircleShape),
                        contentAlignment = Alignment.Center) {
                        Text(if (completed) "✓" else (index + 1).toString(), fontSize = 19.sp,
                            color = stepTextColor, fontWeight = FontWeight.Black)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(lesson.title, fontSize = 16.sp, color = if (unlocked) colors.onSurface else colors.onSurfaceVariant,
                            fontWeight = FontWeight.Bold)
                        Text(when { completed -> "Повторить"; unlocked -> "${lesson.questions.size} заданий"; else -> "Закрыт" },
                            color = if (unlocked && !completed) colors.secondary else colors.onSurfaceVariant, fontSize = 13.sp)
                    }
                    if (unlocked) Text("›", color = colors.onSurface, fontSize = 23.sp)
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(value: Int, total: Int, color: Color, track: Color) {
    LinearProgressIndicator(
        progress = { if (total == 0) 0f else value.toFloat() / total },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
        color = color, trackColor = track
    )
}

@Composable
private fun ExerciseScreen(session: Session, update: (Answer) -> Unit, advance: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val question = session.question
    val focus = LocalFocusManager.current
    val ready = question.isReady(session.answer)
    fun next() { focus.clearFocus(); advance() }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ProgressBar(session.index + if (session.checked) 1 else 0, session.questions.size, colors.tertiary, colors.outlineVariant)
        key(question.id) {
            val scroll = rememberScrollState()
            LaunchedEffect(session.checked) { if (session.checked) scroll.animateScrollTo(scroll.maxValue) }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Text("Задание ${session.index + 1} из ${session.questions.size}",
                    color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(question.type.label, color = colors.onSurfaceVariant, fontSize = 14.sp)
                Text(question.prompt, fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.Black)
                when (question.type) {
                    QuestionType.SINGLE, QuestionType.MULTIPLE -> question.choices.forEachIndexed { index, choice ->
                        val selected = index in session.answer.selected
                        Choice(choice, selected, !session.checked,
                            when {
                                session.checked && index in question.correctChoices -> colors.secondaryContainer
                                session.checked && selected -> colors.errorContainer
                                else -> null
                            }) {
                            val next = when {
                                question.type == QuestionType.SINGLE -> setOf(index)
                                selected -> session.answer.selected - index
                                else -> session.answer.selected + index
                            }
                            update(session.answer.copy(selected = next))
                        }
                    }
                    QuestionType.MATCH -> Matching(question, session.answer, !session.checked, update)
                    else -> {
                        OutlinedTextField(value = session.answer.text,
                            onValueChange = { if (it.length <= 200) update(session.answer.copy(text = it)) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, readOnly = session.checked,
                            label = { Text("Твой ответ") }, shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (ready && !session.checked) next() }))
                        if (question.type == QuestionType.NUMBER && session.answer.text.isNotBlank() && !ready)
                            Text("Введи число, например −2 или 1,5.", color = colors.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
                if (session.checked) Feedback(question, question.isCorrect(session.answer))
            }
        }
        ActionButton(if (session.checked) "Продолжить" else "Проверить", session.checked || ready, ::next)
    }
}

@Composable
private fun Choice(text: String, chosen: Boolean, enabled: Boolean, feedback: Color? = null, click: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val fill = feedback ?: if (chosen) colors.primaryContainer else colors.surface
    val outline = when (feedback) {
        colors.secondaryContainer -> colors.secondary
        colors.errorContainer -> colors.error
        else -> if (chosen) colors.primary else colors.outlineVariant
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(shape).background(fill)
        .border(2.dp, outline, shape)
        .semantics { selected = chosen }.clickable(enabled = enabled, role = Role.Button, onClick = click)
        .padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), color = colors.onSurface, fontSize = 16.sp, lineHeight = 21.sp)
        if (chosen) Text("✓", Modifier.padding(start = 7.dp), color = colors.onSurface, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Matching(question: Question, answer: Answer, enabled: Boolean, update: (Answer) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var active by remember { mutableStateOf(0) }
    question.left.forEachIndexed { index, item ->
        val assigned = answer.pairs[index]?.let { question.choices[it] }
        Choice(if (assigned == null) item else "$item → $assigned", active == index && enabled, enabled) { active = index }
    }
    if (enabled) {
        Text("Пара для «${question.left[active]}»", color = colors.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        question.choices.forEachIndexed { index, choice ->
            Choice(choice, answer.pairs[active] == index, true) {
                val next = answer.link(active, index)
                update(next)
                active = question.left.indices.firstOrNull { it !in next.pairs } ?: active
            }
        }
    }
}

@Composable
private fun Feedback(question: Question, correct: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(if (correct) colors.secondaryContainer else colors.errorContainer, RoundedCornerShape(18.dp))
        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (correct) "Верно!" else "Правильный ответ", color = if (correct) colors.secondary else colors.error,
            fontSize = 18.sp, fontWeight = FontWeight.Black)
        if (!correct) Text(question.solution, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(question.explanation, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun ResultScreen(session: Session, xp: Int, close: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 25.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(144.dp).background(colors.tertiaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Mascot(Modifier.size(132.dp), thumbsUp = true)
            }
            Text(if (session.lessonId != null) "Урок завершён" else "Повторение завершено",
                fontSize = 26.sp, fontWeight = FontWeight.Black)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric("${session.correctCount} / ${session.questions.size}", "Верных ответов", Modifier.weight(1f))
                Metric("+${xp - session.startXp} XP", "Очки опыта", Modifier.weight(1f))
            }
            if (session.correctCount < session.questions.size)
                Text("К этим заданиям можно вернуться в разделе «Ошибки».", color = colors.onSurfaceVariant, fontSize = 15.sp)
        }
        ActionButton("Готово", true, close)
    }
}

@Composable
private fun Metric(value: String, label: String, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.background(colors.primaryContainer, Round).padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, color = colors.primary, fontSize = 25.sp, fontWeight = FontWeight.Black)
        Text(label, fontSize = 13.sp, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun MistakesScreen(count: Int, review: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth().background(colors.primaryContainer, Round).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Работа над ошибками", color = colors.onSurface, fontSize = 26.sp, lineHeight = 29.sp,
                    fontWeight = FontWeight.Black)
                Text(if (count == 0) "Здесь появятся задания, в которых ты ошибся." else "Заданий для повторения · $count",
                    color = colors.onSurfaceVariant, fontSize = 14.sp)
            }
            Mascot(Modifier.size(86.dp))
        }
        if (count > 0) ActionButton("Повторить · ${minOf(count, 5)}", true, review)
    }
}

@Composable
internal fun ActionButton(text: String, enabled: Boolean, click: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp)
        .background(if (enabled) colors.tertiary.copy(alpha = .75f) else colors.outlineVariant, shape).padding(bottom = 3.dp)) {
        Button(onClick = click, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            shape = shape, colors = ButtonDefaults.buttonColors(containerColor = colors.tertiary, contentColor = colors.onTertiary,
                disabledContainerColor = colors.surfaceVariant, disabledContentColor = colors.onSurfaceVariant)) {
            Text(text, fontSize = 17.sp, fontWeight = FontWeight.Black)
        }
    }
}
