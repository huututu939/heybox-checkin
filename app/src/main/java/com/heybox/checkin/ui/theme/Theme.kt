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
 * 视觉方向：打卡台。
 *
 * 两套色板共用一套结构：四槽（canvas / surface / raised 与 line）+ 墨色 + 次要字 + 单一强调色。
 * 夜间是墨绿黑 + 亮琥珀，白天是米白纸 + 青铜琥珀——同样的「账本」气质，只是光不同。
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
    val error: Color,
)

val DarkPalette = Palette(
    canvas = Color(0xFF0D1312),
    surface = Color(0xFF151D1B),
    raised = Color(0xFF1C2724),
    line = Color(0xFF26332F),
    ink = Color(0xFFE9EFEC),
    muted = Color(0xFF8DA29B),
    accent = Color(0xFFF0B44C),
    onAccent = Color(0xFF20160A),
    error = Color(0xFFD98B6A),
)

/**
 * 白天版：纸不是纯白（#F5F1E8 带一点暖），卡片抬升一档而不是靠阴影；
 * 强调色退到青铜（#A3671A），这样在浅底上做小字号文字仍有 5:1 的对比度。
 */
val LightPalette = Palette(
    canvas = Color(0xFFF5F1E8),
    surface = Color(0xFFFCF9F2),
    raised = Color(0xFFEDE7DA),
    line = Color(0xFFD3C9B5),
    ink = Color(0xFF1B2320),
    muted = Color(0xFF5E6C64),
    accent = Color(0xFFA3671A),
    onAccent = Color(0xFFFFF8EC),
    error = Color(0xFFA84A2E),
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
}

private val Display = FontFamily.Serif
private val Body = FontFamily.SansSerif
private val Numerals = FontFamily.Monospace

private val HeyboxTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = 1.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 26.sp, lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Medium,
        fontSize = 15.sp, lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp,
    ),
)

/** 数字专用：等宽，保证「+90」和「+300」上下对齐。 */
val NumberStyle = TextStyle(
    fontFamily = Numerals, fontWeight = FontWeight.Medium,
    fontSize = 28.sp, lineHeight = 32.sp,
)

val NumberSmall = TextStyle(
    fontFamily = Numerals, fontWeight = FontWeight.Normal,
    fontSize = 15.sp, lineHeight = 20.sp,
)

private val HeyboxShapes = Shapes(
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
)

private fun schemeOf(p: Palette, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = p.accent,
        onPrimary = p.onAccent,
        secondary = p.muted,
        onSecondary = p.canvas,
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
        primary = p.accent,
        onPrimary = p.onAccent,
        secondary = p.muted,
        onSecondary = p.canvas,
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
