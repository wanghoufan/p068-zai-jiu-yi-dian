package com.wanghoufan.zaijiuyidian.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

@Immutable
data class AppPalette(
    val bg: Color,
    val card: Color,
    val ink: Color,
    val sub: Color,
    val hair: Color,
    val accent: Color,
    val accent2: Color,
    val accentDark: Color,
    val pale: Color,
    val red: Color,
    val amber: Color,
    val field: Color,
    val onAccent: Color
)

val LightPalette = AppPalette(
    bg = Color(0xFFF6F7F5),
    card = Color(0xFFFFFFFF),
    ink = Color(0xFF152523),
    sub = Color(0xFF71817C),
    hair = Color(0xFFE4EBE7),
    accent = Color(0xFF147E70),
    accent2 = Color(0xFFDCF2EA),
    accentDark = Color(0xFF0D5E53),
    pale = Color(0xFFEFF7F2),
    red = Color(0xFFC14F45),
    amber = Color(0xFFBB7F24),
    field = Color(0xFFF3F6F3),
    onAccent = Color.White
)

val DarkPalette = AppPalette(
    bg = Color(0xFF111A19),
    card = Color(0xFF1D2826),
    ink = Color(0xFFEDF6F1),
    sub = Color(0xFF9AA9A3),
    hair = Color(0xFF33403C),
    accent = Color(0xFF65C8AA),
    accent2 = Color(0xFF203E35),
    accentDark = Color(0xFF83DABF),
    pale = Color(0xFF23352E),
    red = Color(0xFFF1988C),
    amber = Color(0xFFDFB470),
    field = Color(0xFF25322E),
    onAccent = Color(0xFF06201B)
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

object AppTheme {
    val palette: AppPalette
        @Composable get() = LocalPalette.current
}

private val AppTypography = Typography(
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 11.sp, lineHeight = 17.sp),
    titleLarge = TextStyle(fontSize = 25.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    titleMedium = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
)

@Composable
fun ZaiJiuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, fontScale) {
        Density(baseDensity.density, baseDensity.fontScale * fontScale)
    }
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color(0xFF06201B),
            secondary = palette.accentDark,
            background = palette.bg,
            onBackground = palette.ink,
            surface = palette.card,
            onSurface = palette.ink,
            surfaceVariant = palette.field,
            onSurfaceVariant = palette.sub,
            outline = palette.hair,
            error = palette.red
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            secondary = palette.accentDark,
            background = palette.bg,
            onBackground = palette.ink,
            surface = palette.card,
            onSurface = palette.ink,
            surfaceVariant = palette.field,
            onSurfaceVariant = palette.sub,
            outline = palette.hair,
            error = palette.red
        )
    }
    CompositionLocalProvider(LocalPalette provides palette, LocalDensity provides scaledDensity) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
