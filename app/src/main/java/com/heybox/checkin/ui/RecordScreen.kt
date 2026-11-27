package com.heybox.checkin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heybox.checkin.data.RunRecord
import com.heybox.checkin.data.SessionStore
import com.heybox.checkin.data.signStreak
import com.heybox.checkin.ui.theme.Ink
import com.heybox.checkin.ui.theme.NumberTiny
import com.heybox.checkin.ui.theme.glass

private val WeekdayHeader = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * 记录页：一个月一格的日历热力图，点哪格看哪天的明细。
 *
 * 之前是 30 行列表——扫一眼看不出「这个月断在哪」，而日历天然承担这件事：
 * 颜色就是状态，空白就是没记录。文字只留选中那天的结果。
 */
@Composable
fun RecordScreen(history: List<RunRecord>, onBack: () -> Unit) {
    BackHandler { onBack() }
    val now = System.currentTimeMillis()
    val cells = weekCells(history, now, SessionStore.HISTORY_DAYS)
    val doneAll = cells.count { it.state == DayState.DONE }
    val missedAll = cells.count { it.state == DayState.NONE || it.state == DayState.FAILED }
    val months = remember(history) { monthGrids(history, now, SessionStore.HISTORY_DAYS) }
    val streak = remember(history) { signStreak(history, now) }
    val share = remember(history) { shareSummary(history, now) }
    val thisMonth = remember(months) { months.firstOrNull()?.let { monthSummary(it.days) } }
    // 默认选当前月份；今天还没记录就选本月最近一个有记录的日子，保证选中态落在可见日历里
    val newestMonth = months.firstOrNull()
    val newestKey = newestMonth?.monthKey
    var selected by remember(history) { mutableStateOf(defaultDay(cells, newestKey)) }
    // 90 天会有四个月，一次只展示选中的那个月
    var selectedMonth by remember(months) { mutableStateOf(newestKey) }
    val grid = months.firstOrNull { it.monthKey == selectedMonth } ?: newestMonth

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onBack,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Text("← 返回", style = MaterialTheme.typography.bodySmall, color = Ink.muted)
        }
        Spacer(Modifier.height(10.dp))
        Text("记录", style = MaterialTheme.typography.displayLarge, color = Ink.ink)
        Spacer(Modifier.height(8.dp))
        Text(
            buildString {
                append("最近 ${SessionStore.HISTORY_DAYS} 天 · 完成 $doneAll 天")
                if (missedAll > 0) append(" · 缺 $missedAll 天")
            },
            style = MaterialTheme.typography.bodySmall,
            color = Ink.muted,
        )

        Spacer(Modifier.height(12.dp))
        RecapCard(streak = streak, month = thisMonth, share = share)

        Spacer(Modifier.height(14.dp))
        MonthChips(
            months = months,
            selected = selectedMonth,
            onSelect = { month ->
                selectedMonth = month.monthKey
                // 切月时选中该月最近一天，明细不会停在别的月份
                selected = month.days.lastOrNull { it.state != DayState.NONE } ?: month.days.lastOrNull()
            },
        )

        Spacer(Modifier.height(10.dp))
        Legend()

        grid?.let {
            Spacer(Modifier.height(12.dp))
            MonthCard(
                grid = it,
                selected = selected,
                onSelect = { day -> selected = day },
            )
        }

        BalanceTrend(history)

        Spacer(Modifier.height(32.dp))
    }
}

/** 月份切换：90 天有四个月，横向一排比纵向堆四张卡片更好扫。 */
@Composable
private fun MonthChips(
    months: List<MonthGrid>,
    selected: Int?,
    onSelect: (MonthGrid) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        months.forEach { grid ->
            val active = grid.monthKey == selected
            Text(
                grid.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) Ink.ink else Ink.muted,
                fontSize = 12.sp,
                letterSpacing = 0.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (active) Ink.raised else Color.Transparent)
                    .border(1.dp, Ink.line, RoundedCornerShape(99.dp))
                    .clickable { onSelect(grid) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendItem(Ink.success, "完成")
        // 灰点 = 那天由别的入口签好了，本程序没有重复请求
        LegendItem(Ink.muted.copy(alpha = 0.35f), "其它入口")
        LegendItem(Ink.error, "失败")
    }
}

/**
 * 顶部回顾：连续签到是用户最想知道的数，放在最显眼的位置；
 * 本月完成和分享成功率用来回答「这个月到底怎么样」。
 */
@Composable
private fun RecapCard(streak: Int, month: MonthSummary?, share: ShareSummary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Stat(
                label = "连续签到",
                value = if (streak > 0) "$streak 天" else "—",
                accent = streak > 0,
            )
            Stat(
                label = "本月完成",
                value = month?.let { "${it.done} 天" } ?: "—",
                alignEnd = true,
            )
        }
        if (share.attempts > 0) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Ink.line, thickness = 1.dp)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "分享任务 · 30 天",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink.muted,
                    fontSize = 10.5.sp,
                    letterSpacing = 0.sp,
                )
                Text(
                    "${share.finished}/${share.attempts} 成功",
                    style = NumberTiny,
                    color = if (share.finished == share.attempts) Ink.success else Ink.ink,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, accent: Boolean = false, alignEnd: Boolean = false) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Ink.muted,
            fontSize = 10.5.sp,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = NumberTiny,
            color = if (accent) Ink.accent else Ink.ink,
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Ink.muted,
            fontSize = 10.sp,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun MonthCard(grid: MonthGrid, selected: WeekCell?, onSelect: (WeekCell) -> Unit) {
    val summary = monthSummary(grid.days)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp))
            .padding(horizontal = 14.dp, vertical = 16.dp),
    ) {
        Text(
            grid.label,
            style = MaterialTheme.typography.titleMedium,
            color = Ink.ink,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            buildString {
                append("完成 ${summary.done}")
                if (summary.skipped > 0) append(" · 跳过 ${summary.skipped}")
                if (summary.failed > 0) append(" · 失败 ${summary.failed}")
                summary.coinDelta?.let {
                    append(" · H 币 ")
                    append(if (it >= 0) "+$it" else "$it")
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = Ink.muted,
            fontSize = 10.5.sp,
            letterSpacing = 0.sp,
        )

        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            WeekdayHeader.forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink.muted,
                    fontSize = 10.sp,
                    letterSpacing = 0.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        grid.cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    if (cell == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        DayCell(
                            cell = cell,
                            selected = selected != null &&
                                selected.year == cell.year &&
                                selected.month == cell.month &&
                                selected.day == cell.day,
                            onClick = { onSelect(cell) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                // 最后一周不满七格时补空位，否则格子会被拉宽
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        val inThisMonth = selected != null && selected.year == grid.year && selected.month == grid.month
        if (inThisMonth && selected != null) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Ink.line, thickness = 1.dp)
            Spacer(Modifier.height(10.dp))
            DayDetail(selected)
        }
    }
}

/** 一格里只放日期：颜色表示状态，今天多一个小点。 */
@Composable
private fun DayCell(
    cell: WeekCell,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill = when (cell.state) {
        DayState.DONE -> Ink.success.copy(alpha = 0.18f)
        DayState.SKIPPED -> Ink.accent.copy(alpha = 0.16f)
        DayState.FAILED -> Ink.error.copy(alpha = 0.16f)
        DayState.NONE -> Color.Transparent
    }
    val numberColor = when (cell.state) {
        DayState.DONE -> Ink.success
        DayState.SKIPPED -> Ink.ink.copy(alpha = 0.55f)
        DayState.FAILED -> Ink.error
        DayState.NONE -> Ink.muted.copy(alpha = 0.45f)
    }
    val outline = if (selected) {
        Modifier.border(1.5.dp, Ink.ink.copy(alpha = 0.42f), RoundedCornerShape(9.dp))
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .heightIn(min = 34.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(fill)
            .then(outline)
            .clickable(enabled = cell.state != DayState.NONE, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            cell.day.toString(),
            style = NumberTiny,
            color = numberColor,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .size(3.dp)
                .clip(CircleShape)
                .background(if (cell.today) Ink.accent else Color.Transparent),
        )
    }
}

@Composable
private fun DayDetail(cell: WeekCell) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${cell.month}/${cell.day} 周${cell.weekday}",
            style = NumberTiny,
            color = Ink.ink,
            fontSize = 12.sp,
        )
        cell.time?.let {
            Spacer(Modifier.width(8.dp))
            Text(
                it,
                style = NumberTiny,
                color = Ink.muted,
                fontSize = 11.sp,
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    Text(
        detailText(cell),
        style = MaterialTheme.typography.bodySmall,
        color = when (cell.state) {
            DayState.FAILED -> Ink.error
            DayState.NONE -> Ink.muted
            else -> Ink.ink
        },
        fontSize = 12.sp,
    )
}

internal fun detailText(cell: WeekCell): String = when (cell.state) {
    DayState.DONE -> buildString {
        append("经验 ")
        append(cell.expDelta?.let { if (it >= 0) "+$it" else "$it" } ?: "—")
        append(" · H 币 +${cell.coinDelta}")
        cell.batteryDelta?.let { append(" · 电池 +$it") }
    }

    DayState.SKIPPED -> "已在其它入口完成，没有重复请求"
    // 失败文案本身已经说清是哪一类（「连接超时」「要求人机验证」），
    // 再缀一个分类标签只会变成「服务：小黑盒服务器 500」这种重复
    DayState.FAILED -> cell.error ?: "没有留下失败原因"

    DayState.NONE -> "那天没有记录"
}

/**
 * 30 天 H 币余额走势。
 *
 * 用余额而不是「每日经验」：经验是等级内进度，升级会归零，画出来是锯齿；
 * 余额是单调的，断签一眼就能看出来。
 */
@Composable
private fun BalanceTrend(history: List<RunRecord>) {
    val points = remember(history) {
        history.filter { it.coin > 0 }.sortedBy { it.at }
    }
    if (points.size < 2) return

    val first = points.first().coin
    val last = points.last().coin
    val delta = last - first

    Spacer(Modifier.height(16.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text("H 币余额", style = MaterialTheme.typography.labelSmall, color = Ink.muted)
                Spacer(Modifier.height(4.dp))
                Text(
                    last.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Ink.ink,
                )
            }
            Text(
                if (delta >= 0) "+$delta" else "$delta",
                style = NumberTiny,
                color = if (delta >= 0) Ink.success else Ink.error,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        Sparkline(values = points.map { it.coin })
    }
}

@Composable
private fun Sparkline(values: List<Long>) {
    val line = Ink.accent
    val fillTop = Ink.accent.copy(alpha = 0.26f)
    val min = values.minOrNull() ?: 0L
    val max = values.maxOrNull() ?: 0L
    val span = (max - min).coerceAtLeast(1L)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        // 上下各留 3dp，避免圆角笔触被裁掉
        val inset = 3.dp.toPx()
        val usable = (size.height - inset * 2).coerceAtLeast(1f)
        val points = values.mapIndexed { index, value ->
            val ratio = (value - min).toFloat() / span
            Offset(
                x = stepX * index,
                y = inset + usable * (1f - ratio),
            )
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val area = Path().apply {
            addPath(path)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(fillTop, Color.Transparent)))
        drawPath(
            path,
            color = line,
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(line, radius = 2.6.dp.toPx(), center = points.last())
    }
}
