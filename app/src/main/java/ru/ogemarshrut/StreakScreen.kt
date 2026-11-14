package ru.ogemarshrut

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val Flame = PathParser().parsePathString(
    "M51 5C53 25 79 39 85 57C97 86 78 98 54 99C28 101 11 86 13 63C14 49 24 35 35 28C32 39 37 47 42 47C42 31 54 21 51 5Z"
).toPath()
private val Spark = PathParser().parsePathString(
    "M54 54C59 63 66 68 67 77C69 87 62 93 53 93C44 93 37 87 39 78C40 67 49 65 54 54Z"
).toPath()
private val StreakCard = RoundedCornerShape(22.dp)
private val Russian = Locale.forLanguageTag("ru-RU")

@Composable
private fun FlameIcon(modifier: Modifier, active: Boolean) {
    val colors = MaterialTheme.colorScheme
    Canvas(modifier) {
        withTransform({ scale(size.width / 100f, size.height / 100f, pivot = Offset.Zero) }) {
            drawPath(Flame, if (active) Color(0xFFFF8237) else colors.outline)
            drawPath(Spark, if (active) Color(0xFFFFD351) else colors.surfaceVariant)
        }
    }
}

@Composable
internal fun StreakButton(streak: StudyStreak, open: () -> Unit) {
    TextButton(
        onClick = open,
        modifier = Modifier.widthIn(min = 48.dp).semantics { contentDescription = "Ударный режим" },
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        FlameIcon(Modifier.size(19.dp), streak.practicedToday)
        Text(streak.count.toString(), fontSize = 13.sp, fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
internal fun StreakScreen(
    streak: StudyStreak,
    studyDays: Set<Long>,
    today: Long,
    close: () -> Unit,
    study: () -> Unit,
    settings: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - now % 60_000L)
            now = System.currentTimeMillis()
        }
    }
    Column(Modifier.fillMaxSize().background(colors.background).pointerInput(Unit) { }.safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = close, modifier = Modifier.semantics { contentDescription = "Закрыть ударный режим" }) {
                Text("×", fontSize = 34.sp, color = colors.onSurface)
            }
            Text("Ударный режим", Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 22.sp,
                fontWeight = FontWeight.Black, color = colors.onSurface)
            SettingsButton(settings)
        }
        HorizontalDivider(color = colors.outlineVariant)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().background(colors.tertiaryContainer, StreakCard).padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(streak.count.toString(), fontSize = 64.sp, lineHeight = 70.sp, fontWeight = FontWeight.Black,
                        color = colors.onTertiaryContainer)
                    Text("${dayWord(streak.count)} в ударе!", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        color = colors.onSurface)
                }
                FlameIcon(Modifier.size(94.dp), streak.count > 0)
            }
            Column(
                Modifier.fillMaxWidth().background(colors.surfaceVariant, StreakCard).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    when {
                        streak.practicedToday -> "Сегодняшний день засчитан!"
                        streak.count == 0 -> "Заверши урок, чтобы начать серию."
                        streak.freezesLeft > 0 -> "Позанимайся до полуночи, чтобы сохранить заморозку."
                        else -> "Заверши урок до полуночи, чтобы сохранить серию."
                    },
                    color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp
                )
                if (!streak.practicedToday) {
                    if (streak.count > 0) {
                        val minutes = ((nextMidnight(now) - now + 59_999L) / 60_000L).coerceAtLeast(0)
                        val remaining = if (minutes >= 60) "${minutes / 60} ч ${minutes % 60} мин" else "$minutes мин"
                        Text("До полуночи · $remaining", color = colors.onSurfaceVariant, fontSize = 14.sp)
                    }
                    ActionButton("Начать урок", true, study)
                }
            }
            Text("Календарь", color = colors.onSurface, fontWeight = FontWeight.Black, fontSize = 25.sp)
            StreakCalendar(today, studyDays, streak.frozenDays)
            Row(
                Modifier.fillMaxWidth().background(colors.primaryContainer, StreakCard).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("❄", color = colors.primary, fontSize = 27.sp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Заморозки · ${streak.freezesLeft} из $MonthlyFreezes", color = colors.onPrimaryContainer,
                        fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Одна заморозка сохраняет серию за один пропуск. Первого числа запас пополняется.",
                        color = colors.onSurfaceVariant, fontSize = 14.sp)
                }
            }
        }
    }
}

private fun dayWord(count: Int) = when {
    count % 100 in 11..14 -> "дней"
    count % 10 == 1 -> "день"
    count % 10 in 2..4 -> "дня"
    else -> "дней"
}

@Composable
private fun StreakCalendar(today: Long, studyDays: Set<Long>, frozenDays: Set<Long>) {
    val colors = MaterialTheme.colorScheme
    val currentMonth = remember(today) {
        calendarForDay(today).apply { set(Calendar.DAY_OF_MONTH, 1) }
    }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val month = remember(currentMonth, offset) {
        (currentMonth.clone() as Calendar).apply { add(Calendar.MONTH, offset) }
    }
    val date = remember(month) {
        SimpleDateFormat("LLLL yyyy", Russian).apply { timeZone = month.timeZone }.format(month.time)
    }
    val firstDay = month.timeInMillis / DayMillis
    val firstColumn = (month.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val days = month.getActualMaximum(Calendar.DAY_OF_MONTH)
    Column(
        Modifier.fillMaxWidth().border(1.dp, colors.outlineVariant, StreakCard).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { offset-- }, modifier = Modifier.semantics { contentDescription = "Предыдущий месяц" }) {
                Text("‹", fontSize = 30.sp, color = colors.primary)
            }
            Text(date, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 18.sp,
                textAlign = TextAlign.Center, color = colors.onSurface)
            IconButton(onClick = { offset++ }, enabled = offset < 0,
                modifier = Modifier.semantics { contentDescription = "Следующий месяц" }) {
                Text("›", fontSize = 30.sp, color = if (offset < 0) colors.primary else colors.outlineVariant)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            for (weekday in listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")) {
                Text(weekday, Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold,
                    fontSize = 13.sp, color = colors.onSurfaceVariant)
            }
        }
        repeat((firstColumn + days + 6) / 7) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val number = row * 7 + column - firstColumn + 1
                    val day = firstDay + number - 1
                    val practiced = day in studyDays && day <= today
                    val frozen = day in frozenDays
                    val fill = when {
                        practiced -> colors.tertiary
                        frozen -> colors.primaryContainer
                        else -> Color.Transparent
                    }
                    val ink = when {
                        practiced -> colors.onTertiary
                        frozen -> colors.onPrimaryContainer
                        else -> colors.onSurfaceVariant
                    }
                    val previousPracticed = column > 0 && number > 1 && day - 1 in studyDays
                    val nextPracticed = column < 6 && number < days && day < today && day + 1 in studyDays
                    val segment = RoundedCornerShape(
                        topStart = if (previousPracticed) 0.dp else 16.dp,
                        bottomStart = if (previousPracticed) 0.dp else 16.dp,
                        topEnd = if (nextPracticed) 0.dp else 16.dp,
                        bottomEnd = if (nextPracticed) 0.dp else 16.dp
                    )
                    Box(Modifier.weight(1f).height(34.dp), contentAlignment = Alignment.Center) {
                        if (number in 1..days) {
                            if (practiced) {
                                Box(Modifier.fillMaxWidth().height(32.dp).background(fill, segment))
                            }
                            Box(
                                Modifier.size(32.dp).background(if (practiced) Color.Transparent else fill, CircleShape)
                                    .then(if (day == today) Modifier.border(2.dp, colors.primary, CircleShape) else Modifier)
                                    .semantics {
                                        contentDescription = when {
                                            practiced -> "$number: занятие"
                                            frozen -> "$number: заморозка"
                                            day == today -> "$number: сегодня"
                                            else -> number.toString()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(number.toString(), color = ink, fontSize = 13.sp,
                                    fontWeight = if (day == today || practiced || frozen) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(start = 6.dp, top = 5.dp, bottom = 5.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("● Занятие", color = colors.onTertiaryContainer, fontSize = 12.sp)
            Text("❄ Заморозка", color = colors.primary, fontSize = 12.sp)
        }
    }
}
