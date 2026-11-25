package com.heybox.checkin.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heybox.checkin.ui.theme.Ink
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tanh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BarHeight = 64.dp
private val TabWidth = 92.dp
private val BarInset = 4.dp

/** 按下时选中块胀大一点（上游用 78/56，但只有两个页签时那样会盖住整条栏）。 */
private const val PressedScale = 1.16f

/**
 * 底栏拖动的手感，照搬上游 `DampedDragAnimation`：
 *
 * - 位置不是 1:1 跟手，而是用一根很硬的 spring 追手指，所以松手时还会自己收一下；
 * - 拖动速度单独记一份，用来把选中块横着拉开、竖着压扁（速度越快越明显）；
 * - 按下 + 松手各有自己的进度，选中块是「按下才变成玻璃」而不是一直玻璃。
 */
internal class DampedTabDrag(
    private val scope: CoroutineScope,
    private val tabsCount: Int,
    initialIndex: Int,
    private val tabWidthPx: () -> Float,
    private val onSettled: (Int) -> Unit,
) {
    private val valueSpec = spring(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = 0.001f)
    private val velocitySpec = spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 5f)
    private val pressSpec = spring(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = 0.001f)
    private val scaleXSpec = spring(dampingRatio = 0.6f, stiffness = 250f, visibilityThreshold = 0.001f)
    private val scaleYSpec = spring(dampingRatio = 0.7f, stiffness = 250f, visibilityThreshold = 0.001f)
    private val shiftSpec = spring(dampingRatio = 1f, stiffness = 300f, visibilityThreshold = 0.5f)

    val value = Animatable(initialIndex.toFloat(), 0.001f)
    val velocity = Animatable(0f, 5f)
    val press = Animatable(0f, 0.001f)
    val scaleX = Animatable(1f, 0.001f)
    val scaleY = Animatable(1f, 0.001f)
    /** 整条底栏横向让位（px），松手回零。 */
    val shift = Animatable(0f)

    var touch by mutableStateOf(Offset.Unspecified)
        private set

    private val tracker = VelocityTracker()
    private var dragging = false

    val modifier: Modifier = Modifier.pointerInput(tabsCount) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            touch = down.position
            pressIn()
            dragging = true
            var pointer = down
            var cancelled = false
            var moved = 0f
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointer.id }
                if (change == null) {
                    cancelled = true
                    break
                }
                if (change.changedToUpIgnoreConsumed()) break
                // 子元素的点击（页签 clickable）已经把这次事件吃掉了，别再抢
                if (change.isConsumed) {
                    cancelled = true
                    break
                }
                val dx = change.positionChange().x
                if (dx != 0f) {
                    moved += abs(dx)
                    change.consume()
                    touch = change.position
                    dragBy(dx)
                }
                pointer = change
            }
            release(cancelled, moved > 3f * density)
        }
    }

    private fun dragBy(dx: Float) {
        val width = tabWidthPx()
        if (width <= 0f) return
        shiftBy(dx)
        // 追的是「目标位置」而不是当前位置，手指停下时选中块才不会被越推越远
        updateValue(value.targetValue + dx / width)
    }

    private fun shiftBy(dx: Float) {
        scope.launch { shift.snapTo(shift.value + dx) }
    }

    private fun updateValue(target: Float) {
        val clamped = target.coerceIn(0f, (tabsCount - 1).toFloat())
        scope.launch {
            value.animateTo(clamped, valueSpec) { updateVelocity() }
        }
    }

    private fun updateVelocity() {
        // 只有手指还在的时候才更新速度：松手后的吸附动画也会走这里，
        // 那时再记速度，拉伸就会一直留在选中块上
        if (!dragging) return
        tracker.addPosition(SystemClock.uptimeMillis(), Offset(value.value, 0f))
        val target = tracker.calculateVelocity().x / (tabsCount - 1)
        scope.launch { velocity.animateTo(target, velocitySpec) }
    }

    private fun pressIn() {
        tracker.resetTracking()
        scope.launch {
            launch { press.animateTo(1f, pressSpec) }
            launch { scaleX.animateTo(PressedScale, scaleXSpec) }
            launch { scaleY.animateTo(PressedScale, scaleYSpec) }
        }
    }

    private fun release(cancelled: Boolean, moved: Boolean) {
        dragging = false
        scope.launch {
            tracker.resetTracking()
            velocity.animateTo(0f, velocitySpec)
        }
        scope.launch { shift.animateTo(0f, shiftSpec) }
        // 没拖动就是一次点按：位置和页签交给页签自己的 clickable，这里只负责把手感收回去。
        // 否则点「设置」会被这次抬手按旧位置吸附回「今天」。
        if (!moved) {
            scope.launch { fadeOutPress() }
            return
        }
        val target = if (cancelled) {
            value.value.roundToInt().coerceIn(0, tabsCount - 1)
        } else {
            value.targetValue.roundToInt().coerceIn(0, tabsCount - 1)
        }
        val settling = abs(value.value - value.targetValue) > 0.001f
        scope.launch {
            value.animateTo(target.toFloat(), valueSpec)
            onSettled(target)
        }
        scope.launch {
            // 给吸附动画一点时间再缩回去，不然会「先缩、再滑」。
            // 用固定延时而不是等 snapshotFlow：选中块停住后不再变化，flow 不会再有发射。
            if (settling) delay(140)
            fadeOutPress()
        }
    }

    private suspend fun fadeOutPress() {
        kotlinx.coroutines.coroutineScope {
            launch { press.animateTo(0f, pressSpec) }
            launch { scaleX.animateTo(1f, scaleXSpec) }
            launch { scaleY.animateTo(1f, scaleYSpec) }
        }
    }
}

/**
 * 底部两页签的玻璃栏。按下时选中块变成会折射的玻璃珠，横拖跟手、松手带速度吸附，
 * 整条底栏还会跟着手指让几个 dp——这些细节都对齐上游 LiquidBottomTabs。
 */
@Composable
internal fun LiquidBottomBar(
    modifier: Modifier = Modifier,
    backdrop: Backdrop,
    tab: HomeTab,
    darkTheme: Boolean,
    onTab: (HomeTab) -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val pill = RoundedCornerShape(percent = 50)
    val barWidth = TabWidth * 2 + BarInset * 2
    val tabWidthPx = with(density) { TabWidth.toPx() }

    val currentTab by rememberUpdatedState(tab)
    val onTabNow by rememberUpdatedState(onTab)

    val drag = remember {
        DampedTabDrag(
            scope = scope,
            tabsCount = 2,
            initialIndex = if (tab == HomeTab.Today) 0 else 1,
            tabWidthPx = { tabWidthPx },
            onSettled = { index ->
                val settled = if (index == 0) HomeTab.Today else HomeTab.Settings
                if (settled != currentTab) onTabNow(settled)
            },
        )
    }

    // 点页签、代码切页时，选中块滑到新位置
    LaunchedEffect(tab) {
        drag.value.animateTo(
            targetValue = if (tab == HomeTab.Today) 0f else 1f,
            animationSpec = spring(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = 0.001f),
        )
    }

    val press = drag.press.value
    // 静止时保留之前那层浅玻璃（比上游的 10% 黑更贴合这套配色），按下再淡出
    val surface = Ink.surface.copy(alpha = 0.62f)
    val pressedShade = (if (darkTheme) Color.White else Color.Black).copy(alpha = 0.03f * press)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(barWidth)
                .height(BarHeight)
                .graphicsLayer {
                    // 整条底栏跟着手指让一点点位，松手弹回
                    translationX = 4.dp.toPx() * tanh(drag.shift.value / (size.width / 2f)).toFloat()
                }
                .then(drag.modifier),
        ) {
            // 玻璃本体只负责折射，不能把选中块包在里面：
            // drawBackdrop 会按胶囊裁剪内容，选中块放大时就被裁在栏里了（上游也是把两者拆开的）
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { pill },
                        effects = {
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(24.dp.toPx(), 24.dp.toPx())
                        },
                        highlight = { Highlight.Default.copy(alpha = 0.12f) },
                        shadow = { Shadow.Default },
                    ),
            )

            Box(Modifier.matchParentSize().padding(BarInset)) {
                // 选中块：静止时只是一层淡影，按下去才长成会折射的玻璃珠
                Box(
                    modifier = Modifier
                        .width(TabWidth)
                        .fillMaxHeight()
                        .offset { IntOffset((drag.value.value * tabWidthPx).roundToInt(), 0) }
                        .graphicsLayer {
                            // 缩放放在最外面这一层：drawBackdrop 内部的 layerBlock 会把玻璃裁在自己的
                            // 原始高度里，按下放大时上沿会出现一条直边
                            val v = drag.velocity.value / 10f
                            scaleX = drag.scaleX.value / (1f - (v * 0.75f).coerceIn(-0.12f, 0.12f))
                            scaleY = drag.scaleY.value * (1f - (v * 0.25f).coerceIn(-0.10f, 0.10f))
                        }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { pill },
                            effects = {
                                lens(
                                    10.dp.toPx() * press,
                                    14.dp.toPx() * press,
                                    chromaticAberration = true,
                                )
                            },
                            highlight = { Highlight.Default.copy(alpha = 0.72f * press) },
                            shadow = { Shadow(alpha = press) },
                            innerShadow = { InnerShadow(radius = 8.dp * press, alpha = press) },
                                onDrawSurface = {
                                drawRect(surface, alpha = 1f - press)
                                drawRect(pressedShade)
                            },
                        )
                        .border(
                            1.dp,
                            // 一圈椭圆反光：静止时也在，按下更亮
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.30f + 0.35f * press),
                                    Color.White.copy(alpha = 0.10f + 0.15f * press),
                                ),
                            ),
                            pill,
                        ),
                )
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TabItem("今天", tab == HomeTab.Today, TabIcon.Today, press) { onTabNow(HomeTab.Today) }
                    TabItem("设置", tab == HomeTab.Settings, TabIcon.Settings, press) { onTabNow(HomeTab.Settings) }
                }
            }

            // 跟手高光画在最上层：按下时整条栏加一层 5% 的白，再叠一团跟着手指的柔光
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithContent {
                        drawContent()
                        val p = drag.press.value
                        if (p > 0.01f && drag.touch.isSpecified) {
                            val r = size.height / 2f
                            val path = Path().apply {
                                addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r, r)))
                            }
                            clipPath(path) {
                                drawRect(Color.White.copy(alpha = 0.035f * p), blendMode = BlendMode.Plus)
                                val glow = size.minDimension * 1.5f
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color.White.copy(alpha = 0.08f * p), Color.Transparent),
                                        center = drag.touch,
                                        radius = glow,
                                    ),
                                    radius = glow,
                                    center = drag.touch,
                                    blendMode = BlendMode.Plus,
                                )
                            }
                        }
                    },
            )
        }
    }
}

private enum class TabIcon { Today, Settings }

@Composable
private fun TabItem(
    label: String,
    selected: Boolean,
    icon: TabIcon,
    press: Float,
    onClick: () -> Unit,
) {
    // 按下时选中的页签往强调色偏一点，跟高光一起「亮起来」
    val color = if (selected) lerp(Ink.ink, Ink.accent, 0.35f * press) else Ink.muted
    val canvasColor = Ink.canvas
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(TabWidth)
            .fillMaxHeight()
            .graphicsLayer {
                val scale = 1f + 0.12f * press
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(percent = 50))
            .clickable(interactionSource = null, indication = null, role = Role.Tab, onClick = onClick),
    ) {
        Canvas(Modifier.size(19.dp)) {
            val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = size.width
            when (icon) {
                TabIcon.Today -> {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(w * 0.14f, w * 0.2f),
                        size = androidx.compose.ui.geometry.Size(w * 0.72f, w * 0.66f),
                        cornerRadius = CornerRadius(w * 0.18f),
                        style = stroke,
                    )
                    drawLine(color, Offset(w * 0.33f, w * 0.09f), Offset(w * 0.33f, w * 0.3f), 1.5.dp.toPx(), StrokeCap.Round)
                    drawLine(color, Offset(w * 0.67f, w * 0.09f), Offset(w * 0.67f, w * 0.3f), 1.5.dp.toPx(), StrokeCap.Round)
                    val path = Path().apply {
                        moveTo(w * 0.33f, w * 0.55f)
                        lineTo(w * 0.46f, w * 0.68f)
                        lineTo(w * 0.7f, w * 0.42f)
                    }
                    drawPath(path, color, style = stroke)
                }

                TabIcon.Settings -> {
                    drawLine(color, Offset(w * 0.12f, w * 0.34f), Offset(w * 0.88f, w * 0.34f), 1.5.dp.toPx(), StrokeCap.Round)
                    drawLine(color, Offset(w * 0.12f, w * 0.68f), Offset(w * 0.88f, w * 0.68f), 1.5.dp.toPx(), StrokeCap.Round)
                    drawCircle(color = canvasColor, radius = w * 0.11f, center = Offset(w * 0.38f, w * 0.34f))
                    drawCircle(color = color, radius = w * 0.11f, center = Offset(w * 0.38f, w * 0.34f), style = stroke)
                    drawCircle(color = canvasColor, radius = w * 0.11f, center = Offset(w * 0.64f, w * 0.68f))
                    drawCircle(color = color, radius = w * 0.11f, center = Offset(w * 0.64f, w * 0.68f), style = stroke)
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontSize = 10.5.sp,
            lineHeight = 12.sp,
            letterSpacing = 0.sp,
            maxLines = 1,
        )
    }
}
