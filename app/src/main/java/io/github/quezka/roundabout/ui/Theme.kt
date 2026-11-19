package io.github.quezka.roundabout.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.quezka.roundabout.R

// neutral tones ; accent = ambient colour -> m3 primary
object Dash {
    val bg = Color(0xFF0F1012)
    val text = Color(0xFFF2F3F5)
    val muted = Color(0xFFADB2BB)
}

val Manrope = FontFamily(
    Font(R.font.manrope_extralight, FontWeight.ExtraLight),
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

fun dashScheme(accent: Color): ColorScheme {
    val high = Color(0xFF25282D)
    return darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF121212),
        primaryContainer = lerp(high, accent, 0.30f),
        onPrimaryContainer = Dash.text,
        secondary = lerp(Color(0xFFC9CCD1), accent, 0.25f),
        onSecondary = Color(0xFF121212),
        secondaryContainer = lerp(Color(0xFF1E2024), accent, 0.10f),
        onSecondaryContainer = Dash.text,
        background = Dash.bg,
        onBackground = Dash.text,
        surface = Dash.bg,
        onSurface = Dash.text,
        surfaceVariant = Color(0xFF2E3137),
        onSurfaceVariant = Dash.muted,
        surfaceContainerLowest = Color(0xFF0A0B0C),
        surfaceContainerLow = Color(0xFF15171A),
        surfaceContainer = Color(0xFF1B1D21),
        surfaceContainerHigh = high,
        surfaceContainerHighest = Color(0xFF2F3238),
        outline = Color(0xFF5A5F68),
        outlineVariant = Color(0xFF34373D),
        error = Color(0xFFFF6B5E),
    )
}

@Composable
fun RoundaboutTheme(accent: Color = Color(0xFFFF7A1A), content: @Composable () -> Unit) {
    val typography = remember {
        val base = Typography()
        fun TextStyle.m() = copy(fontFamily = Manrope)
        Typography(
            displayLarge = base.displayLarge.m(), displayMedium = base.displayMedium.m(), displaySmall = base.displaySmall.m(),
            headlineLarge = base.headlineLarge.m(), headlineMedium = base.headlineMedium.m(), headlineSmall = base.headlineSmall.m(),
            titleLarge = base.titleLarge.m(), titleMedium = base.titleMedium.m(), titleSmall = base.titleSmall.m(),
            bodyLarge = base.bodyLarge.m(), bodyMedium = base.bodyMedium.m(), bodySmall = base.bodySmall.m(),
            labelLarge = base.labelLarge.m(), labelMedium = base.labelMedium.m(), labelSmall = base.labelSmall.m(),
        )
    }
    MaterialTheme(colorScheme = dashScheme(accent), typography = typography, content = content)
}
