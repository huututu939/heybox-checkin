package com.heybox.checkin.ui.theme

import android.os.Build
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
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
    // 表面色相：斜向铺一层，浅色用暖→紫，深色用蓝→紫，别让深色玻璃发脏
    val tintA: Color,
    val tintB: Color,
)

val LightGlass = GlassPalette(
    base = Color(0xFFF3EDE3),
    warm = Color(0xFFF7A64C),
    cool = Color(0xFF7FA8C9),
    accent = Color(0xFFB9A0D8),
    glassTop = Color(0x7AFFFFFF),
    glassBottom = Color(0x45FFFFFF),
    borderTop = Color(0xF0FFFFFF),
    borderBottom = Color(0x1FFFFFFF),
    highlight = Color(0x33FFFFFF),
    shadow = Color(0x291A1712),
    tintA = Color(0xFFF7A64C),
    tintB = Color(0xFFB9A0D8),
)

val DarkGlass = GlassPalette(
    base = Color(0xFF0A0C0E),
    warm = Color(0xFF6B4A22),
    cool = Color(0xFF22323D),
    accent = Color(0xFF4A2A1E),
    glassTop = Color(0x24FFFFFF),
    glassBottom = Color(0x0DFFFFFF),
    borderTop = Color(0x47FFFFFF),
    borderBottom = Color(0x0FFFFFFF),
    highlight = Color(0x1FFFFFFF),
    shadow = Color(0x80000000),
    tintA = Color(0xFF7FA8C9),
    tintB = Color(0xFF9C86BC),
)

private val LocalGlass = staticCompositionLocalOf { LightGlass }

/** 背景层的后景数据，卡片用它做折射；没有时退回纯半透明。 */
val LocalBackdrop = staticCompositionLocalOf<com.kyant.backdrop.Backdrop?> { null }

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
                    colors = listOf(g.warm.copy(alpha = 0.95f), g.warm.copy(alpha = 0f)),
                    center = Offset(w * 0.90f, h * 0.12f),
                    radius = w * 1.05f,
                ),
                radius = w * 1.05f,
                center = Offset(w * 0.90f, h * 0.12f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(g.cool.copy(alpha = 0.85f), g.cool.copy(alpha = 0f)),
                    center = Offset(w * 0.08f, h * 0.58f),
                    radius = w * 1.02f,
                ),
                radius = w * 1.02f,
                center = Offset(w * 0.08f, h * 0.58f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(g.accent.copy(alpha = 0.72f), g.accent.copy(alpha = 0f)),
                    center = Offset(w * 0.94f, h * 0.92f),
                    radius = w * 0.92f,
                ),
                radius = w * 0.92f,
                center = Offset(w * 0.94f, h * 0.92f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFB6D8C8).copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(w * 0.22f, h * 0.16f),
                    radius = w * 0.55f,
                ),
                radius = w * 0.55f,
                center = Offset(w * 0.22f, h * 0.16f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFC9BCE0).copy(alpha = 0.42f), Color.Transparent),
                    center = Offset(w * 0.62f, h * 0.74f),
                    radius = w * 0.6f,
                ),
                radius = w * 0.6f,
                center = Offset(w * 0.62f, h * 0.74f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF8FB8D8).copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(w * 0.08f, h * 0.98f),
                    radius = w * 0.7f,
                ),
                radius = w * 0.7f,
                center = Offset(w * 0.08f, h * 0.98f),
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
    blurRadius: androidx.compose.ui.unit.Dp = 14.dp,
    lensRadius: androidx.compose.ui.unit.Dp = 30.dp,
    lensDepth: androidx.compose.ui.unit.Dp = 72.dp,
    tintAlpha: Float = 0.16f,
): Modifier {
    val g = Glass.palette
    val backdrop = LocalBackdrop.current
    return this
        .shadow(
            elevation = if (raised) 14.dp else 6.dp,
            shape = shape,
            clip = false,
            ambientColor = g.shadow,
            spotColor = g.shadow,
        )
        .clip(shape)
        .then(
            if (backdrop != null) {
                drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(blurRadius.toPx())
                        lens(lensRadius.toPx(), lensDepth.toPx())
                    },
                    highlight = { com.kyant.backdrop.highlight.Highlight.Default.copy(alpha = 0.85f) },
                    shadow = { com.kyant.backdrop.shadow.Shadow.Default },
                )
            } else {
                Modifier
            },
        )
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    g.glassTop.copy(alpha = g.glassTop.alpha * tintAlpha + 0.10f),
                    g.glassBottom.copy(alpha = g.glassBottom.alpha * tintAlpha + 0.04f),
                ),
            ),
        )
        // 表面色相：斜着铺一层极淡的暖 → 紫，玻璃才带颜色，而不是一层灰白
        .background(
            Brush.linearGradient(
                colors = listOf(
                    g.tintA.copy(alpha = 0.07f),
                    Color.Transparent,
                    g.tintB.copy(alpha = 0.07f),
                ),
            ),
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(colors = listOf(g.borderTop, g.borderBottom)),
            shape = shape,
        )
        .drawWithContent {
            drawContent()
            // 高光也带一点暖色相，纯白会显得塑料
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(g.highlight, g.tintA.copy(alpha = 0.05f), Color.Transparent),
                    start = Offset(0f, 0f),
                    end = Offset(size.width * 0.9f, size.height * 0.7f),
                ),
            )
        }
}

/**
 * 弹窗卡片。弹窗在独立窗口里，采不到页面后景，所以不做折射，
 * 只保留和卡片一致的那几层：表面色相、描边、上沿高光。
 */
@Composable
fun Modifier.dialogSurface(shape: Shape = RoundedCornerShape(26.dp)): Modifier {
    val g = Glass.palette
    return this
        .shadow(24.dp, shape, clip = false, ambientColor = g.shadow, spotColor = g.shadow)
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(Ink.surface.copy(alpha = 0.96f), Ink.surface.copy(alpha = 0.92f)),
            ),
        )
        .background(
            Brush.linearGradient(
                listOf(g.tintA.copy(alpha = 0.07f), Color.Transparent, g.tintB.copy(alpha = 0.07f)),
            ),
        )
        .border(1.dp, Brush.verticalGradient(listOf(g.borderTop, g.borderBottom)), shape)
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
