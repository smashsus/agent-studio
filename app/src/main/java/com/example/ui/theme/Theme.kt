package com.example.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("System")
}

@Immutable
data class SwissTokens(
    val background: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val border: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val mutedText: Color,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val isDark: Boolean
)

object SwissGeometry {
    val screenPadding = 16.dp
    val minTouchTarget = 48.dp
    val spacing = 8.dp
    val sectionSpacing = 20.dp
    val cardPadding = 16.dp
    val radiusSmall = 8.dp
    val radiusStandard = 12.dp
    val radiusComposer = 16.dp
    val radiusLarge = 18.dp
    val radiusDialog = 20.dp
    val borderWidth = 1.dp
    val iconSize = 22.dp
    val bottomNavHeight = 76.dp
}

object SwissOpacity {
    const val primaryText = 1.00f
    const val secondaryText = 0.72f
    const val metadata = 0.55f
    const val disabled = 0.38f
    const val placeholder = 0.50f
    const val divider = 0.55f
    const val pressOverlay = 0.08f
    const val modalBackdrop = 0.55f
}

object SwissMotion {
    val standardEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    const val pressDurationMs = 120
    const val fadeDurationMs = 160
    const val expandDurationMs = 200
    const val screenDurationMs = 220
    const val dialogDurationMs = 180
}

private val DarkTokens = SwissTokens(
    background = DarkBackground,
    surface1 = DarkSurface1,
    surface2 = DarkSurface2,
    surface3 = DarkSurface3,
    border = DarkBorder,
    primaryText = DarkPrimaryText,
    secondaryText = DarkSecondaryText,
    mutedText = DarkMutedText,
    primaryAccent = DarkPrimaryAccent,
    secondaryAccent = DarkSecondaryAccent,
    success = DarkSuccess,
    warning = DarkWarning,
    error = DarkError,
    isDark = true
)

private val LightTokens = SwissTokens(
    background = LightBackground,
    surface1 = LightSurface1,
    surface2 = LightSurface2,
    surface3 = LightSurface3,
    border = LightBorder,
    primaryText = LightPrimaryText,
    secondaryText = LightSecondaryText,
    mutedText = LightMutedText,
    primaryAccent = LightPrimaryAccent,
    secondaryAccent = LightSecondaryAccent,
    success = LightSuccess,
    warning = LightWarning,
    error = LightError,
    isDark = false
)

val LocalSwissTokens = staticCompositionLocalOf { DarkTokens }

private val SwissDarkScheme = darkColorScheme(
    primary = DarkPrimaryAccent,
    onPrimary = DarkBackground,
    primaryContainer = DarkSurface2,
    onPrimaryContainer = DarkPrimaryText,
    secondary = DarkSecondaryAccent,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSurface3,
    onSecondaryContainer = DarkPrimaryText,
    tertiary = DarkSuccess,
    onTertiary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkPrimaryText,
    surface = DarkSurface1,
    onSurface = DarkPrimaryText,
    surfaceVariant = DarkSurface2,
    onSurfaceVariant = DarkSecondaryText,
    surfaceContainerLow = DarkSurface1,
    surfaceContainer = DarkSurface2,
    surfaceContainerHigh = DarkSurface3,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = DarkError,
    onError = DarkBackground,
    errorContainer = DarkSurface2,
    onErrorContainer = DarkError
)

private val SwissLightScheme = lightColorScheme(
    primary = LightPrimaryAccent,
    onPrimary = Color.White,
    primaryContainer = LightSurface2,
    onPrimaryContainer = LightPrimaryText,
    secondary = LightSecondaryAccent,
    onSecondary = Color.White,
    secondaryContainer = LightSurface3,
    onSecondaryContainer = LightPrimaryText,
    tertiary = LightSuccess,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightPrimaryText,
    surface = LightSurface1,
    onSurface = LightPrimaryText,
    surfaceVariant = LightSurface2,
    onSurfaceVariant = LightSecondaryText,
    surfaceContainerLow = LightSurface1,
    surfaceContainer = LightSurface2,
    surfaceContainerHigh = LightSurface3,
    outline = LightBorder,
    outlineVariant = LightBorder,
    error = LightError,
    onError = Color.White,
    errorContainer = LightSurface2,
    onErrorContainer = LightError
)

val SwissShapes = Shapes(
    small = RoundedCornerShape(SwissGeometry.radiusSmall),
    medium = RoundedCornerShape(SwissGeometry.radiusStandard),
    large = RoundedCornerShape(SwissGeometry.radiusLarge),
    extraLarge = RoundedCornerShape(SwissGeometry.radiusDialog)
)

@Composable
fun DeepSeekAgentTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val tokens = if (useDark) DarkTokens else LightTokens
    val colorScheme = if (useDark) SwissDarkScheme else SwissLightScheme

    CompositionLocalProvider(LocalSwissTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SwissTypography,
            shapes = SwissShapes,
            content = content
        )
    }
}
