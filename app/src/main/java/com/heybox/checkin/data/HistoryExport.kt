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

/* ------------------------------------------------------------------ JSON 备份 */

/** 单条记录的 JSON 映射，落库（[SessionStore]）与备份共用同一套字段。 */
internal fun RunRecord.toJsonObject(): org.json.JSONObject {
    val tasks = org.json.JSONObject()
    for ((id, state) in taskStates) tasks.put(id, state)
    return org.json.JSONObject().apply {
        put("at", at)
        put("executed", executed)
        // 增量未知时不要写成 0：0 会被界面当成「这次确实没加经验」。
        expDelta?.let { put("exp_delta", it) }
        put("coin_delta", coinDelta)
        error?.let { put("error", it) }
        errorKind?.let { put("error_kind", it) }
        batteryDelta?.let { put("battery_delta", it) }
        put("exp", exp)
        put("coin", coin)
        battery?.let { put("battery", it) }
        level?.let { put("level", it) }
        maxExp?.let { put("max_exp", it) }
        put("signed", signed)
        put("tasks", tasks)
    }
}

internal fun runRecordFromJson(json: org.json.JSONObject): RunRecord {
    val tasksJson = json.optJSONObject("tasks")
    val states = buildMap {
        tasksJson?.keys()?.forEach { key -> put(key, tasksJson.optString(key)) }
    }
    return RunRecord(
        at = json.optLong("at"),
        executed = json.optBoolean("executed"),
        expDelta = if (json.has("exp_delta")) json.optLong("exp_delta") else null,
        coinDelta = json.optLong("coin_delta"),
        batteryDelta = if (json.has("battery_delta")) json.optLong("battery_delta") else null,
        exp = json.optLong("exp"),
        coin = json.optLong("coin"),
        battery = if (json.has("battery")) json.optLong("battery") else null,
        level = if (json.has("level")) json.optLong("level") else null,
        maxExp = if (json.has("max_exp")) json.optLong("max_exp") else null,
        signed = json.optBoolean("signed"),
        taskStates = states,
        error = json.optString("error").takeIf { it.isNotEmpty() },
        errorKind = json.optString("error_kind").takeIf { it.isNotEmpty() },
    )
}

/** 整份历史导出成 JSON：机器可读，换机恢复用。CSV 仍然是人看的版本。 */
fun historyJson(history: List<RunRecord>): String = org.json.JSONObject().apply {
    put("app", "heybox-checkin")
    put("schema", 1)
    put("exported_at", System.currentTimeMillis())
    val array = org.json.JSONArray()
    history.sortedBy { it.at }.forEach { array.put(it.toJsonObject()) }
    put("records", array)
}.toString(2)

/**
 * 解析备份文本。不是盒签备份、或 JSON 坏掉时返回 null；
 * 单条记录缺字段退回默认值，坏记录跳过，能恢复多少算多少。
 */
fun recordsFromJson(text: String): List<RunRecord>? = runCatching {
    val root = org.json.JSONObject(text)
    if (root.optString("app") != "heybox-checkin") return null
    val array = root.optJSONArray("records") ?: return null
    (0 until array.length()).mapNotNull { index ->
        array.optJSONObject(index)?.let { runRecordFromJson(it) }
    }
}.getOrNull()
