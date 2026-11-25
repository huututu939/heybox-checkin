package com.heybox.checkin.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 液态玻璃用的那一层「背景」。
 *
 * 玻璃效果要成立，背后必须有可看的东西：这里铺三团柔和的暖/冷光斑，
 * 卡片与底栏用半透明层叠在上面，边缘加一道斜向高光，就是玻璃的锐角反光。
 * 光斑本身是渐变、没有噪点，所以不需要真的做后景模糊，视觉上已经是「毛玻璃」。
 */
@Immutable
data class GlassPalette(
    val base: Color,
    val warm: Color,
    val cool: Color,
    val accent: Color,
    val glassTop: Color,
    val glassBottom: Color,
    val borderTop: Color,
    val borderBottom: Color,
    val highlight: Color,
    val shadow: Color,
)

val LightGlass = GlassPalette(
    base = Color(0xFFF1EFEA),
    warm = Color(0xFFF3C07B),
    cool = Color(0xFFAFC3D4),
    accent = Color(0xFFE8A87C),
    glassTop = Color(0xD1FFFFFF),
    glassBottom = Color(0x94FFFFFF),
    borderTop = Color(0xE6FFFFFF),
    borderBottom = Color(0x33FFFFFF),
    highlight = Color(0x26FFFFFF),
    shadow = Color(0x1A1A1712),
)

val DarkGlass = GlassPalette(
    base = Color(0xFF0A0C0E),
    warm = Color(0xFF6B4A22),
    cool = Color(0xFF22323D),
    accent = Color(0xFF4A2A1E),
    glassTop = Color(0x29FFFFFF),
    glassBottom = Color(0x12FFFFFF),
    borderTop = Color(0x3DFFFFFF),
    borderBottom = Color(0x0FFFFFFF),
    highlight = Color(0x1AFFFFFF),
    shadow = Color(0x66000000),
)

private val LocalGlass = staticCompositionLocalOf { LightGlass }

/** 跟随明暗切换玻璃参数。 */
@Composable
fun ProvideGlass(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalGlass provides (if (dark) DarkGlass else LightGlass),
        content = content,
    )
}

object Glass {
    val palette: GlassPalette @Composable @ReadOnlyComposable get() = LocalGlass.current
}

/** 铺满整屏的光斑背景。放在内容最底层，只画一次。 */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier) {
    val g = Glass.palette
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(g.base)
            val w = size.width
            val h = size.height

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(g.warm.copy(alpha = 0.55f), g.warm.copy(alpha = 0f)),
                    center = Offset(w * 0.98f, h * -0.04f),
                    radius = w * 0.62f,
                ),
                radius = w * 0.62f,
                center = Offset(w * 0.98f, h * -0.04f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(g.cool.copy(alpha = 0.5f), g.cool.copy(alpha = 0f)),
                    center = Offset(w * -0.06f, h * 0.46f),
                    radius = w * 0.72f,
                ),
                radius = w * 0.72f,
                center = Offset(w * -0.06f, h * 0.46f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(g.accent.copy(alpha = 0.4f), g.accent.copy(alpha = 0f)),
                    center = Offset(w * 1.0f, h * 0.92f),
                    radius = w * 0.62f,
                ),
                radius = w * 0.62f,
                center = Offset(w * 1.0f, h * 0.92f),
            )
        }
    }
}

/**
 * 一块玻璃：阴影 + 半透明渐变 + 高光描边 + 一层斜向反光。
 * 描边用渐变而不是纯色，左上亮、右下淡，边缘才像被光擦过。
 */
@Composable
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(24.dp),
    raised: Boolean = true,
): Modifier {
    val g = Glass.palette
    return this
        .shadow(
            elevation = if (raised) 14.dp else 6.dp,
            shape = shape,
            clip = false,
            ambientColor = g.shadow,
            spotColor = g.shadow,
        )
        .clip(shape)
        .background(
            Brush.verticalGradient(colors = listOf(g.glassTop, g.glassBottom)),
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(colors = listOf(g.borderTop, g.borderBottom)),
            shape = shape,
        )
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(g.highlight, Color.Transparent),
                    start = Offset(0f, 0f),
                    end = Offset(size.width * 0.9f, size.height * 0.7f),
                ),
            )
        }
}
