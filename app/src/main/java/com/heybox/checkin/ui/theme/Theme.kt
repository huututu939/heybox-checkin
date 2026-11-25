package com.heybox.checkin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 视觉方向：安静的工具。
 *
 * 一套结构，两套明暗：中性画布 + 纯色卡片，靠底色和间距分层，不用描边；
 * 强调色只留给「当前状态」和「开启中的开关」，主操作改用墨色；
 * 数字用等宽对齐，标题用无衬线，界面只用两种字形。
 */
data class Palette(
    val canvas: Color,
    val surface: Color,
    val raised: Color,
    val line: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val onInk: Color,
    val success: Color,
    val error: Color,
)

val DarkPalette = Palette(
    canvas = Color(0xFF0E1012),
    surface = Color(0xFF171A1C),
    raised = Color(0xFF202427),
    line = Color(0xFF2B3034),
    ink = Color(0xFFECEEF0),
    muted = Color(0xFF9AA1A7),
    accent = Color(0xFFEFAE55),
    onAccent = Color(0xFF1A1206),
    onInk = Color(0xFF0E1012),
    success = Color(0xFF74C08D),
    error = Color(0xFFE08D6C),
)

/**
 * 白天版：画布是带一点暖的中性灰，卡片纯白——对比来自明度差而不是线条；
 * 强调色压到青铜 #A05A12，浅底上做小字号仍能过 5:1。
 */
val LightPalette = Palette(
    canvas = Color(0xFFF4F3F0),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFECEAE5),
    line = Color(0xFFE4E1DA),
    ink = Color(0xFF15181A),
    muted = Color(0xFF5F666B),
    accent = Color(0xFFA05A12),
    onAccent = Color(0xFFFFF7EC),
    onInk = Color(0xFFF4F3F0),
    success = Color(0xFF2F7D57),
    error = Color(0xFFA8452B),
)

private val LocalPalette = staticCompositionLocalOf { DarkPalette }

/** 取当前主题的颜色。写法与原来的 `Ink.accent` 一致，只是会随明暗切换。 */
object Ink {
    val canvas: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.canvas
    val surface: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surface
    val raised: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.raised
    val line: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.line
    val ink: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ink
    val muted: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.muted
    val accent: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accent
    val onAccent: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.onAccent
    val onInk: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.onInk
    val success: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.success
    val error: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.error
}

private val Ui = FontFamily.SansSerif
private val Numerals = FontFamily.Monospace

private val HeyboxTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Ui, fontWeight = FontWeight.Bold,
        fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Ui, fontWeight = FontWeight.Bold,
        fontSize = 27.sp, lineHeight = 32.sp, letterSpacing = (-0.6).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Ui, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Ui, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, lineHeight = 23.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Ui, fontWeight = FontWeight.Medium,
        fontSize = 15.sp, lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Ui, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Ui, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Ui, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp,
    ),
)

/** 数字专用：等宽，保证「+90」和「+300」这类数值上下对齐。 */
val NumberStyle = TextStyle(
    fontFamily = Numerals, fontWeight = FontWeight.Medium,
    fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.6).sp,
)

val NumberSmall = TextStyle(
    fontFamily = Numerals, fontWeight = FontWeight.Normal,
    fontSize = 15.sp, lineHeight = 20.sp,
)

val NumberTiny = TextStyle(
    fontFamily = Numerals, fontWeight = FontWeight.Normal,
    fontSize = 11.sp, lineHeight = 15.sp,
)

private val HeyboxShapes = Shapes(
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
)

private fun schemeOf(p: Palette, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = p.ink,
        onPrimary = p.onInk,
        secondary = p.accent,
        onSecondary = p.onAccent,
        background = p.canvas,
        onBackground = p.ink,
        surface = p.surface,
        onSurface = p.ink,
        surfaceVariant = p.raised,
        onSurfaceVariant = p.muted,
        outline = p.line,
        error = p.error,
    )
} else {
    lightColorScheme(
        primary = p.ink,
        onPrimary = p.onInk,
        secondary = p.accent,
        onSecondary = p.onAccent,
        background = p.canvas,
        onBackground = p.ink,
        surface = p.surface,
        onSurface = p.ink,
        surfaceVariant = p.raised,
        onSurfaceVariant = p.muted,
        outline = p.line,
        error = p.error,
    )
}

@Composable
fun HeyboxTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    androidx.compose.runtime.CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = schemeOf(palette, darkTheme),
            typography = HeyboxTypography,
            shapes = HeyboxShapes,
            content = content,
        )
    }
}
