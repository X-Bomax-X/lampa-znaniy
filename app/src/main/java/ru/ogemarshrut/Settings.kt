package ru.ogemarshrut

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
internal fun SettingsButton(open: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    IconButton(onClick = open, modifier = Modifier.semantics { contentDescription = "Настройки" }) {
        Canvas(Modifier.size(22.dp)) {
            val radius = size.minDimension
            val lineWidth = 1.8.dp.toPx()
            drawCircle(colors.onSurface, radius * .30f, style = Stroke(lineWidth))
            drawCircle(colors.onSurface, radius * .11f, style = Stroke(lineWidth))
            repeat(8) { tooth ->
                rotate(tooth * 45f) {
                    drawLine(colors.onSurface, Offset(center.x, center.y - radius * .46f),
                        Offset(center.x, center.y - radius * .34f), lineWidth, StrokeCap.Round)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheet(
    model: StudyModel,
    dismiss: () -> Unit,
    stopAnswer: () -> Unit,
    reset: () -> Boolean
) {
    val colors = MaterialTheme.colorScheme
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var saveError by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface
    ) {
        val sheetWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            sheetWindow?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars =
                    !model.darkTheme
            }
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Настройки", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            SettingSwitch("Звук ответов", "Сигналы при проверке заданий", model.soundEnabled) { enabled ->
                saveError = !model.setSound(enabled)
                if (!saveError && !enabled) stopAnswer()
            }
            SettingSwitch("Тёмная тема", null, model.darkTheme) { enabled ->
                saveError = !model.setDarkTheme(enabled)
            }
            ReminderSettings(model) { saveError = it }
            HorizontalDivider()
            Text("Настройки разработчика", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            SettingSwitch("Разблокировать все уроки", "Любой урок можно открыть с карты предмета.", model.unlockAll) {
                saveError = !model.setUnlockAll(it)
            }
            TextButton(onClick = { confirmReset = true }) {
                Text("Сбросить приложение", color = colors.error, fontWeight = FontWeight.Bold)
            }
            if (saveError) Text("Не удалось сохранить изменения. Попробуйте ещё раз.", color = colors.error)
            HorizontalDivider()
            TextButton(onClick = { showAbout = true }) { Text("О приложении") }
        }
        if (showAbout) {
            AlertDialog(
                onDismissRequest = { showAbout = false },
                title = { Text("О приложении") },
                text = {
                    Text(stringResource(R.string.about_app, stringResource(R.string.app_name)))
                },
                confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Закрыть") } }
            )
        }
        if (confirmReset) {
            AlertDialog(
                onDismissRequest = { confirmReset = false },
                title = { Text("Сбросить приложение?") },
                text = { Text("Удалятся весь прогресс, очки, ошибки, выбранные предметы и настройки. Приложение вернётся к приветствию.") },
                confirmButton = {
                    TextButton(onClick = {
                        val saved = reset()
                        confirmReset = false
                        saveError = !saved
                    }) { Text("Сбросить", color = colors.error, fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Отмена") } }
            )
        }
    }
}

@Composable
private fun ReminderSettings(model: StudyModel, onSaveError: (Boolean) -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? ComponentActivity
    var notificationAccess by remember { mutableStateOf(allowsStudyNotifications(context)) }
    var exactAccess by remember { mutableStateOf(allowsExactReminders(context)) }
    DisposableEffect(activity, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationAccess = allowsStudyNotifications(context)
                exactAccess = allowsExactReminders(context)
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationAccess = allowsStudyNotifications(context)
        refreshReminders(context)
    }
    val packageUri = Uri.fromParts("package", context.packageName, null)
    SettingSwitch(
        "Напоминания о занятиях",
        null,
        model.remindersEnabled
    ) { enabled ->
        val saved = model.setReminders(enabled)
        onSaveError(!saved)
        if (saved && enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            ProgressStore(context).markNotificationPromptShown()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    if (model.remindersEnabled && !notificationAccess) {
        TextButton(onClick = {
            val settings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
            }
            runCatching { context.startActivity(settings) }
        }) { Text("Разрешить уведомления Android") }
    }
    if (model.remindersEnabled && notificationAccess && !exactAccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        TextButton(onClick = {
            runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)) }
        }) { Text("Разрешить точные напоминания") }
    }
}

@Composable
private fun SettingSwitch(title: String, caption: String?, checked: Boolean, change: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, role = Role.Switch, onValueChange = change)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            if (caption != null) Text(caption, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange = null)
    }
}
