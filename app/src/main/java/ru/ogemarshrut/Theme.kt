package ru.ogemarshrut

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1654AF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F3FF),
    onPrimaryContainer = Color(0xFF252C40),
    secondary = Color(0xFF1A6533),
    secondaryContainer = Color(0xFFE5F7E6),
    onSecondaryContainer = Color(0xFF252C40),
    tertiary = Color(0xFFFFCF46),
    onTertiary = Color(0xFF252C40),
    tertiaryContainer = Color(0xFFFFF3C9),
    onTertiaryContainer = Color(0xFF715006),
    background = Color.White,
    onBackground = Color(0xFF252C40),
    surface = Color.White,
    onSurface = Color(0xFF252C40),
    surfaceVariant = Color(0xFFEDF0F5),
    onSurfaceVariant = Color(0xFF596174),
    surfaceContainerHigh = Color(0xFFF4F6F9),
    surfaceContainerHighest = Color(0xFFEDF0F5),
    outline = Color(0xFF777E8D),
    outlineVariant = Color(0xFFE5E9F0),
    error = Color(0xFF8B331E),
    errorContainer = Color(0xFFFFF0EA)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9CCFF),
    onPrimary = Color(0xFF072957),
    primaryContainer = Color(0xFF243C60),
    onPrimaryContainer = Color(0xFFECF3FF),
    secondary = Color(0xFFA0DFAD),
    secondaryContainer = Color(0xFF233F32),
    onSecondaryContainer = Color(0xFFE0F4E4),
    tertiary = Color(0xFFFFCF46),
    onTertiary = Color(0xFF252C40),
    tertiaryContainer = Color(0xFF453616),
    onTertiaryContainer = Color(0xFFFFDC83),
    background = Color(0xFF141924),
    onBackground = Color(0xFFF0F2FA),
    surface = Color(0xFF1E2634),
    onSurface = Color(0xFFF0F2FA),
    surfaceVariant = Color(0xFF283346),
    onSurfaceVariant = Color(0xFFB7C0D2),
    surfaceContainerHigh = Color(0xFF242D3D),
    surfaceContainerHighest = Color(0xFF303C50),
    outline = Color(0xFF8794A8),
    outlineVariant = Color(0xFF3B485E),
    error = Color(0xFFFFB4A2),
    errorContainer = Color(0xFF502B29)
)

internal fun studyColors(dark: Boolean) = if (dark) DarkColors else LightColors
