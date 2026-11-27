package com.heybox.checkin.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把签到历史导出成 CSV。
 *
 * - 开头带 UTF-8 BOM，Excel 直接双击打开不会乱码
 * - 按时间正序，一天一行
 * - 字段里有逗号、引号或换行时按 RFC 4180 加引号
 */
fun historyCsv(history: List<RunRecord>): String {
    val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    val header = listOf(
        "日期时间",
        "状态",
        "经验增量",
        "H币增量",
        "电池增量",
        "H币余额",
        "等级",
        "等级内经验",
        "失败原因",
        "任务状态",
    )
    val rows = history.sortedBy { it.at }.map { record ->
        listOf(
            time.format(Date(record.at)),
            stateText(record),
            record.expDelta?.toString().orEmpty(),
            record.coinDelta.toString(),
            record.batteryDelta?.toString().orEmpty(),
            record.coin.toString(),
            record.level?.toString().orEmpty(),
            record.exp.takeIf { it > 0 }?.toString().orEmpty(),
            record.error.orEmpty(),
            record.taskStates.entries.joinToString(" ") { "${it.key}:${it.value}" },
        )
    }
    return buildString {
        append('\uFEFF')
        append(header.joinToString(",") { csvField(it) })
        append('\n')
        rows.forEach { row ->
            append(row.joinToString(",") { csvField(it) })
            append('\n')
        }
    }
}

private fun stateText(record: RunRecord): String = when {
    !record.executed -> "其它入口"
    record.signed -> "完成"
    else -> "失败"
}

internal fun csvField(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else {
        value
    }
