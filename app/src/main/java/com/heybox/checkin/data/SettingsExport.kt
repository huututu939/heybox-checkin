package com.heybox.checkin.data

/**
 * 可以在设备之间搬走的偏好设置：定时窗口、分享开关、最近任务隐藏、外观。
 *
 * 刻意不含 heybox_id / pkey / 设备标识——那些换了机器本来就要重新登录，
 * 也不该出现在剪贴板里。
 */
data class SharedSettings(
    val scheduled: Boolean,
    val startHour: Int,
    val endHour: Int,
    val shares: Set<String>,
    val hideFromRecents: Boolean,
    val appearance: String,
    val successNotify: Boolean = true,
    val nightQuiet: Boolean = false,
)

object SettingsExport {

    private const val HEADER = "盒签配置 v1"
    private val SHARE_IDS = listOf("1", "19", "31")
    private val APPEARANCES = setOf("auto", "light", "dark")

    fun read(store: SessionStore): SharedSettings = SharedSettings(
        scheduled = store.scheduledEnabled(),
        startHour = store.windowStart(),
        endHour = store.windowEnd(),
        shares = store.enabledShares(),
        hideFromRecents = store.hideFromRecents(),
        appearance = store.appearance(),
        successNotify = store.successNotify(),
        nightQuiet = store.nightQuiet(),
    )

    /** 纯文本、一行一项，方便微信里发给自己再从另一台机器粘贴。 */
    fun encode(settings: SharedSettings): String = buildString {
        appendLine(HEADER)
        appendLine("daily=${if (settings.scheduled) "on" else "off"}")
        appendLine("window=${"%02d:00".format(settings.startHour)}-${"%02d:00".format(settings.endHour)}")
        appendLine("shares=${settings.shares.sorted().joinToString(",")}")
        appendLine("hideRecent=${if (settings.hideFromRecents) "on" else "off"}")
        appendLine("successNotify=${if (settings.successNotify) "on" else "off"}")
        appendLine("nightQuiet=${if (settings.nightQuiet) "on" else "off"}")
        append("appearance=${settings.appearance}")
    }

    /** 解析失败返回 null；行内容缺省时退回默认值，能导入一部分也算成功。 */
    fun decode(text: String): SharedSettings? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.firstOrNull() != HEADER) return null
        val map = lines.drop(1)
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.substring(0, index).trim() to line.substring(index + 1).trim()
            }
            .toMap()

        val window = map["window"]?.split('-')?.mapNotNull { it.substringBefore(':').toIntOrNull() }
        val start = (window?.getOrNull(0) ?: 8).coerceIn(0, 22)
        val end = (window?.getOrNull(1) ?: 20).coerceIn(start + 1, 23)
        return SharedSettings(
            scheduled = map["daily"] == "on",
            startHour = start,
            endHour = end,
            shares = map["shares"].orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it in SHARE_IDS }
                .toSet(),
            hideFromRecents = map["hideRecent"] == "on",
            appearance = map["appearance"]?.takeIf { it in APPEARANCES } ?: "auto",
            successNotify = map["successNotify"] != "off",
            nightQuiet = map["nightQuiet"] == "on",
        )
    }

    fun save(store: SessionStore, settings: SharedSettings) {
        store.setScheduled(settings.scheduled)
        store.setWindow(settings.startHour, settings.endHour)
        SHARE_IDS.forEach { id -> store.setShareTaskEnabled(id, id in settings.shares) }
        store.setHideFromRecents(settings.hideFromRecents)
        store.setSuccessNotify(settings.successNotify)
        store.setNightQuiet(settings.nightQuiet)
        store.setAppearance(settings.appearance)
    }
}
