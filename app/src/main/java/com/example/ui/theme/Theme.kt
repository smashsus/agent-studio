package com.example.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode(val label: String, val swatchColor: Color) {
    WHITE("Pure White", WhitePrimaryAccent),
    LINEN("Warm Paper", LinenPrimaryAccent),
    SAGE("Soft Sage", SagePrimaryAccent),
    SKY("Pale Mist", SkyPrimaryAccent),
    LAVENDER("Soft Lilac", LavenderPrimaryAccent);

    companion object {
        fun fromStored(raw: String?): ThemeMode {
            if (raw.isNullOrBlank()) return WHITE
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: WHITE
        }
    }
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
    val isDark: Boolean = false
)

object SwissGeometry {
    val screenPadding = 16.dp
    val minTouchTarget = 48.dp
    val spacing = 8.dp
    val sectionSpacing = 20.dp
    val cardPadding = 16.dp
    val radiusSmall = 10.dp
    val radiusStandard = 14.dp
    val radiusCard = 16.dp
    val radiusComposer = 22.dp
    val radiusLarge = 18.dp
    val radiusDialog = 22.dp
    val borderWidth = 0.85.dp
    val iconSize = 21.dp
    val bottomNavHeight = 74.dp
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

private val WhiteTokens = SwissTokens(
    background = WhiteBackground,
    surface1 = WhiteSurface1,
    surface2 = WhiteSurface2,
    surface3 = WhiteSurface3,
    border = WhiteBorder,
    primaryText = WhitePrimaryText,
    secondaryText = WhiteSecondaryText,
    mutedText = WhiteMutedText,
    primaryAccent = WhitePrimaryAccent,
    secondaryAccent = WhiteSecondaryAccent,
    success = SoftSuccess,
    warning = SoftWarning,
    error = SoftError
)

private val LinenTokens = SwissTokens(
    background = LinenBackground,
    surface1 = LinenSurface1,
    surface2 = LinenSurface2,
    surface3 = LinenSurface3,
    border = LinenBorder,
    primaryText = LinenPrimaryText,
    secondaryText = LinenSecondaryText,
    mutedText = LinenMutedText,
    primaryAccent = LinenPrimaryAccent,
    secondaryAccent = LinenSecondaryAccent,
    success = SoftSuccess,
    warning = SoftWarning,
    error = SoftError
)

private val SageTokens = SwissTokens(
    background = SageBackground,
    surface1 = SageSurface1,
    surface2 = SageSurface2,
    surface3 = SageSurface3,
    border = SageBorder,
    primaryText = SagePrimaryText,
    secondaryText = SageSecondaryText,
    mutedText = SageMutedText,
    primaryAccent = SagePrimaryAccent,
    secondaryAccent = SageSecondaryAccent,
    success = SoftSuccess,
    warning = SoftWarning,
    error = SoftError
)

private val SkyTokens = SwissTokens(
    background = SkyBackground,
    surface1 = SkySurface1,
    surface2 = SkySurface2,
    surface3 = SkySurface3,
    border = SkyBorder,
    primaryText = SkyPrimaryText,
    secondaryText = SkySecondaryText,
    mutedText = SkyMutedText,
    primaryAccent = SkyPrimaryAccent,
    secondaryAccent = SkySecondaryAccent,
    success = SoftSuccess,
    warning = SoftWarning,
    error = SoftError
)

private val LavenderTokens = SwissTokens(
    background = LavenderBackground,
    surface1 = LavenderSurface1,
    surface2 = LavenderSurface2,
    surface3 = LavenderSurface3,
    border = LavenderBorder,
    primaryText = LavenderPrimaryText,
    secondaryText = LavenderSecondaryText,
    mutedText = LavenderMutedText,
    primaryAccent = LavenderPrimaryAccent,
    secondaryAccent = LavenderSecondaryAccent,
    success = SoftSuccess,
    warning = SoftWarning,
    error = SoftError
)

fun tokensForTheme(mode: ThemeMode): SwissTokens = when (mode) {
    ThemeMode.WHITE -> WhiteTokens
    ThemeMode.LINEN -> LinenTokens
    ThemeMode.SAGE -> SageTokens
    ThemeMode.SKY -> SkyTokens
    ThemeMode.LAVENDER -> LavenderTokens
}

private fun buildLightColorScheme(t: SwissTokens): ColorScheme = lightColorScheme(
    primary = t.primaryAccent,
    onPrimary = Color.White,
    primaryContainer = t.surface2,
    onPrimaryContainer = t.primaryText,
    secondary = t.secondaryAccent,
    onSecondary = Color.White,
    secondaryContainer = t.surface3,
    onSecondaryContainer = t.primaryText,
    tertiary = t.success,
    onTertiary = Color.White,
    background = t.background,
    onBackground = t.primaryText,
    surface = t.surface1,
    onSurface = t.primaryText,
    surfaceVariant = t.surface2,
    onSurfaceVariant = t.secondaryText,
    surfaceContainerLowest = t.surface1,
    surfaceContainerLow = t.surface1,
    surfaceContainer = t.surface2,
    surfaceContainerHigh = t.surface3,
    surfaceContainerHighest = t.surface3,
    outline = t.border,
    outlineVariant = t.border,
    error = t.error,
    onError = Color.White,
    errorContainer = t.surface2,
    onErrorContainer = t.error
)

val LocalSwissTokens = staticCompositionLocalOf { WhiteTokens }

val SwissShapes = Shapes(
    small = RoundedCornerShape(SwissGeometry.radiusSmall),
    medium = RoundedCornerShape(SwissGeometry.radiusStandard),
    large = RoundedCornerShape(SwissGeometry.radiusLarge),
    extraLarge = RoundedCornerShape(SwissGeometry.radiusDialog)
)

@Composable
fun AgentStudioTheme(
    themeMode: ThemeMode = ThemeMode.WHITE,
    content: @Composable () -> Unit
) {
    val tokens = tokensForTheme(themeMode)
    val colorScheme = buildLightColorScheme(tokens)

    CompositionLocalProvider(LocalSwissTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SwissTypography,
            shapes = SwissShapes,
            content = content
        )
    }
}
