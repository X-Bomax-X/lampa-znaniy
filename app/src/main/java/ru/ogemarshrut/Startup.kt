package ru.ogemarshrut

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
internal fun SubjectSelection(
    subjects: List<Subject>,
    initial: Set<String>,
    save: (Set<String>) -> Boolean,
    cancel: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val firstLaunch = cancel == null
    var stage by rememberSaveable { mutableIntStateOf(if (firstLaunch) 0 else 3) }
    var selected by rememberSaveable { mutableStateOf(ArrayList(initial)) }
    var saveFailed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(stage) {
        when (stage) {
            0 -> { delay(320); stage = 1 }
            1 -> { delay(1350); stage = 2 }
            2 -> { delay(950); stage = 3 }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        val small = maxHeight < 520.dp
        val largeMascot = minOf(maxHeight * .32f, 172.dp)
        val size by animateDpAsState(
            if (stage < 3) largeMascot else if (small) 68.dp else 104.dp,
            tween(850, easing = FastOutSlowInEasing),
            label = "Размер лампочки"
        )
        val headerOffset by animateDpAsState(
            if (stage < 3) maxOf(12.dp, (maxHeight - largeMascot - 94.dp) / 2) else 10.dp,
            tween(850, easing = FastOutSlowInEasing),
            label = "Подъём приветствия"
        )
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (cancel != null) {
                TextButton(onClick = cancel, modifier = Modifier.align(Alignment.Start)) {
                    Text("‹ Назад", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(headerOffset))
            Mascot(Modifier.size(size), waving = firstLaunch && stage < 2)
            if (stage > 0) {
                Text(
                    text = when {
                        !firstLaunch -> "Изменить предметы"
                        stage == 1 -> "Здравствуйте!"
                        else -> "Выберите предметы, к которым хотите готовиться."
                    },
                    modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth()
                        .background(colors.tertiaryContainer, RoundedCornerShape(20.dp))
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    textAlign = TextAlign.Center,
                    color = colors.onSurface,
                    fontSize = if (small) 16.sp else 19.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            AnimatedVisibility(
                visible = stage == 3,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                enter = slideInVertically(tween(650, delayMillis = 230)) { it } +
                    fadeIn(tween(420, delayMillis = 230))
            ) {
                Column(Modifier.fillMaxSize()) {
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(subjects, key = { it.id }) { subject ->
                            val checked = subject.id in selected
                            val shape = RoundedCornerShape(14.dp)
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(shape)
                                    .background(if (checked) colors.primaryContainer else colors.surface)
                                    .border(1.dp, if (checked) colors.primary else colors.outlineVariant, shape)
                                    .toggleable(checked, role = Role.Checkbox) { value ->
                                        selected = ArrayList(if (value) selected + subject.id else selected - subject.id)
                                        saveFailed = false
                                    }.padding(horizontal = 12.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(34.dp).background(colors.tertiaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                                    Text(subjectSymbol(subject.id), fontWeight = FontWeight.Black, color = colors.onSurface, fontSize = 14.sp)
                                }
                                Text(subject.title, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = colors.onSurface)
                                Checkbox(checked, onCheckedChange = null)
                            }
                        }
                    }
                    if (saveFailed) Text("Не удалось сохранить выбор. Попробуйте ещё раз.", color = colors.error)
                    ActionButton(if (firstLaunch) "Продолжить" else "Сохранить", selected.isNotEmpty()) {
                        saveFailed = !save(selected.toSet())
                    }
                }
            }
        }
    }
}

private const val LoadingMillis = 3000L
private val LoadingTimes = listOf(0L, 430L, 1120L, 1690L, 2520L, LoadingMillis)
private val LoadingValues = listOf(0f, .28f, .38f, .76f, .87f, 1f)

private fun loadingProgress(elapsed: Long): Float {
    val next = LoadingTimes.indexOfFirst { it > elapsed }
    if (next < 0) return 1f
    val previous = next - 1
    val fraction = (elapsed - LoadingTimes[previous]).toFloat() / (LoadingTimes[next] - LoadingTimes[previous])
    return LoadingValues[previous] + fraction * (LoadingValues[next] - LoadingValues[previous])
}

@Composable
internal fun LoadingScreen(startedAt: Long, finish: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val currentFinish by rememberUpdatedState(finish)
    var progress by remember(startedAt) { mutableFloatStateOf(0f) }
    LaunchedEffect(startedAt) {
        while (true) {
            val elapsed = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0)
            progress = loadingProgress(elapsed)
            if (elapsed >= LoadingMillis) break
            withFrameNanos { }
        }
        currentFinish()
    }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Mascot(Modifier.size(160.dp), waiting = true)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_name), color = colors.onSurface, fontWeight = FontWeight.Black, fontSize = 27.sp)
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.widthIn(max = 260.dp).fillMaxWidth().height(10.dp).clip(CircleShape),
            color = colors.tertiary,
            trackColor = colors.tertiaryContainer,
            drawStopIndicator = {}
        )
        Spacer(Modifier.height(10.dp))
        Text("Загрузка…", color = colors.onSurfaceVariant, fontSize = 14.sp)
    }
}
