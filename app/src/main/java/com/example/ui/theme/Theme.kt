package com.example.ui.theme

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
    DARK("DeepSlate Dark"),
    LIGHT("Clean Daylight"),
    SYSTEM("Follow System")
}

@Immutable
data class AgentSemanticColors(
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val telemetry: Color,
    val telemetryContainer: Color,
    val danger: Color,
    val dangerContainer: Color
)

private val DarkSemanticColors = AgentSemanticColors(
    success = EmeraldSignal,
    successContainer = EmeraldContainer,
    warning = AmberCaution,
    warningContainer = AmberContainer,
    telemetry = CyanTelemetry,
    telemetryContainer = CyanContainer,
    danger = CrimsonHalt,
    dangerContainer = CrimsonContainer
)

private val LightSemanticColors = AgentSemanticColors(
    success = Color(0xFF059669),
    successContainer = Color(0xFFD1FAE5),
    warning = Color(0xFFD97706),
    warningContainer = Color(0xFFFEF3C7),
    telemetry = TealCyanLight,
    telemetryContainer = CyanLightContainer,
    danger = Color(0xFFDC2626),
    dangerContainer = Color(0xFFFEE2E2)
)

val LocalAgentColors = staticCompositionLocalOf { DarkSemanticColors }

private val DeepSlateDarkScheme = darkColorScheme(
    primary = ElectricCobalt,
    onPrimary = Color.White,
    primaryContainer = CobaltContainer,
    onPrimaryContainer = CobaltOnContainer,
    secondary = CyanTelemetry,
    onSecondary = ObsidianSlate,
    secondaryContainer = CyanContainer,
    onSecondaryContainer = CyanOnContainer,
    tertiary = EmeraldSignal,
    onTertiary = ObsidianSlate,
    background = ObsidianSlate,
    onBackground = SlateInk,
    surface = ElevatedSlate,
    onSurface = SlateInk,
    surfaceVariant = TacticalCard,
    onSurfaceVariant = SlateMutedInk,
    surfaceContainerLow = ElevatedSlate,
    surfaceContainer = TacticalCard,
    surfaceContainerHigh = TacticalCardHigh,
    outline = CrispDivider,
    outlineVariant = CrispDivider,
    error = CrimsonHalt,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = Color(0xFFFECACA)
)

private val DeepSlateLightScheme = lightColorScheme(
    primary = DeepCobaltLight,
    onPrimary = Color.White,
    primaryContainer = CobaltLightContainer,
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = TealCyanLight,
    onSecondary = Color.White,
    secondaryContainer = CyanLightContainer,
    onSecondaryContainer = Color(0xFF164E63),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    background = MineralWhite,
    onBackground = NavyInk,
    surface = CardWhite,
    onSurface = NavyInk,
    surfaceVariant = SoftSlateSurface,
    onSurfaceVariant = NavyMutedInk,
    surfaceContainerLow = CardWhite,
    surfaceContainer = SoftSlateSurface,
    surfaceContainerHigh = Color(0xFFE2E8F0),
    outline = LightDivider,
    outlineVariant = LightDivider,
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

val DeepSlateShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
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
    val colorScheme = if (useDark) DeepSlateDarkScheme else DeepSlateLightScheme
    val semantic = if (useDark) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(LocalAgentColors provides semantic) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DeepSlateTypography,
            shapes = DeepSlateShapes,
            content = content
        )
    }
}
