package com.heybox.checkin.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.heybox.checkin.core.AppInfo
import com.heybox.checkin.core.DeviceInfo
import com.heybox.checkin.core.HeyboxConfig
import com.heybox.checkin.core.SessionInfo
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 登录态落盘。pkey 与设备令牌等价于账号凭据，用 Android Keystore 的 AES-GCM 加密后存 SharedPreferences。
 * 不引入 androidx.security，减少依赖面。
 */
class SessionStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(config: HeyboxConfig) {
        val editor = prefs.edit()
        editor.putString(KEY_HEYBOX_ID, encrypt(config.session.heyboxId))
        editor.putString(KEY_PKEY, encrypt(config.session.pkey))
        editor.putString(KEY_DEVICE_ID, encrypt(config.session.deviceId))
        editor.putString(KEY_TOKEN, encrypt(config.session.tokenId))
        editor.putString(KEY_MODEL, config.device.model)
        editor.putString(KEY_OS, config.device.osVersion)
        editor.putBoolean(KEY_EXPIRED, false)
        editor.apply()
    }

    fun load(): HeyboxConfig? {
        val heyboxId = decrypt(prefs.getString(KEY_HEYBOX_ID, null)) ?: return null
        val pkey = decrypt(prefs.getString(KEY_PKEY, null)) ?: return null
        return HeyboxConfig(
            session = SessionInfo(
                heyboxId = heyboxId,
                pkey = pkey,
                deviceId = decrypt(prefs.getString(KEY_DEVICE_ID, null)) ?: "",
                tokenId = decrypt(prefs.getString(KEY_TOKEN, null)) ?: "",
            ),
            device = DeviceInfo(
                model = prefs.getString(KEY_MODEL, null) ?: "",
                osVersion = prefs.getString(KEY_OS, null) ?: "",
            ),
            app = AppInfo(),
        )
    }

    fun clear() = prefs.edit().clear().apply()

    /** 只清账号凭据，保留定时、外观等偏好：登录过期后重登不用重新设置一遍。 */
    fun clearCredentials() = prefs.edit()
        .remove(KEY_HEYBOX_ID)
        .remove(KEY_PKEY)
        .remove(KEY_DEVICE_ID)
        .remove(KEY_TOKEN)
        .apply()

    fun markSessionExpired() = prefs.edit().putBoolean(KEY_EXPIRED, true).apply()

    fun sessionExpired(): Boolean = prefs.getBoolean(KEY_EXPIRED, false)

    fun clearSessionExpired() = prefs.edit().putBoolean(KEY_EXPIRED, false).apply()

    /** 只判断凭据是否存在，不触发 Keystore 解密；开机重排闹钟时用。 */
    fun hasCredentials(): Boolean =
        prefs.contains(KEY_HEYBOX_ID) && prefs.contains(KEY_PKEY)

    fun lastResult(): String? = prefs.getString(KEY_LAST_RESULT, null)

    fun setLastResult(value: String) = prefs.edit().putString(KEY_LAST_RESULT, value).apply()

    fun saveRun(record: RunRecord) {
        prefs.edit().putString(KEY_RUN_RECORD, record.toJsonObject().toString()).apply()
        appendHistory(record)
    }

    fun loadRun(): RunRecord? = runCatching {
        val raw = prefs.getString(KEY_RUN_RECORD, null) ?: return null
        runRecordFromJson(org.json.JSONObject(raw))
    }.getOrNull()

    /** 最近 N 天的记录，按时间倒序；同一天只保留最后一次写入。 */
    fun history(): List<RunRecord> = runCatching {
        val raw = prefs.getString(KEY_RUN_HISTORY, null) ?: return emptyList()
        val array = org.json.JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { runRecordFromJson(it) }
        }
    }.getOrDefault(emptyList())

    private fun appendHistory(record: RunRecord) {
        val day = dayKey(record.at)
        val kept = history().filter { dayKey(it.at) != day }
        val list = (listOf(record) + kept).take(HISTORY_DAYS)
        val array = org.json.JSONArray()
        list.forEach { array.put(it.toJsonObject()) }
        prefs.edit().putString(KEY_RUN_HISTORY, array.toString()).apply()
    }

    /** 备份恢复：同一天以备份为准，其余保留。返回合并后的天数。 */
    fun mergeHistory(records: List<RunRecord>): Int {
        if (records.isEmpty()) return 0
        val byDay = history().associateBy { dayKey(it.at) }.toMutableMap()
        records.forEach { byDay[dayKey(it.at)] = it }
        val merged = byDay.values.sortedByDescending { it.at }.take(HISTORY_DAYS)
        val array = org.json.JSONArray()
        merged.forEach { array.put(it.toJsonObject()) }
        prefs.edit().putString(KEY_RUN_HISTORY, array.toString()).apply()
        return merged.size
    }

    private fun dayKey(millis: Long): Int {
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = millis }
        return calendar.get(java.util.Calendar.YEAR) * 10000 +
            (calendar.get(java.util.Calendar.MONTH) + 1) * 100 +
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
    }

    fun scheduledEnabled(): Boolean = prefs.getBoolean(KEY_SCHEDULED, false)

    fun setScheduled(enabled: Boolean) = prefs.edit().putBoolean(KEY_SCHEDULED, enabled).apply()

    /** 外观：auto（跟随系统）/ light / dark。 */
    fun appearance(): String = prefs.getString(KEY_APPEARANCE, "auto") ?: "auto"

    fun setAppearance(mode: String) = prefs.edit().putString(KEY_APPEARANCE, mode).apply()

    /**
     * 最近任务隐藏：离开界面时结束任务，多任务里不留卡片。
     * 默认关闭；开启前会提醒用户先在最近任务里锁定卡片（EMUI 等 ROM 的清后台行为）。
     */
    fun hideFromRecents(): Boolean = prefs.getBoolean(KEY_HIDE_RECENT, false)

    /** 首次进入首页时的权限提醒只弹一次。 */
    fun permissionHelpSeen(): Boolean = prefs.getBoolean(KEY_PERMISSION_HELP, false)

    fun markPermissionHelpSeen() = prefs.edit().putBoolean(KEY_PERMISSION_HELP, true).apply()

    /** 用户点过「跳过这个版本」的 tag；同一个版本不再自动弹窗。 */
    fun skippedVersion(): String? = prefs.getString(KEY_SKIPPED_VERSION, null)

    fun skipVersion(tag: String) = prefs.edit().putString(KEY_SKIPPED_VERSION, tag).apply()

    /**
     * 上次自动查更新的时刻。GitHub 未认证接口按 IP 限流 60 次/小时，
     * 而首页每次冷启动都会查一次——反复开关 App 会把自己查进 403。
     */
    fun updateCheckedAt(): Long = prefs.getLong(KEY_UPDATE_CHECKED_AT, 0L)

    /** 上次自动检查是否成功：失败只等一小时就重试，成功等十二小时。 */
    fun updateCheckOk(): Boolean = prefs.getBoolean(KEY_UPDATE_CHECK_OK, true)

    fun markUpdateChecked(
        at: Long = System.currentTimeMillis(),
        ok: Boolean = true,
    ) = prefs.edit()
        .putLong(KEY_UPDATE_CHECKED_AT, at)
        .putBoolean(KEY_UPDATE_CHECK_OK, ok)
        .apply()

    fun setHideFromRecents(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_HIDE_RECENT, enabled).apply()

    /** 首启风险提示是否已确认。存版本号，提示文案改版后可重新弹一次。 */
    fun acceptedNotice(): Int = prefs.getInt(KEY_NOTICE_VERSION, 0)

    fun acceptNotice(version: Int) = prefs.edit().putInt(KEY_NOTICE_VERSION, version).apply()

    /** 随机窗口，单位小时，默认 08:00–20:00。 */
    fun windowStart(): Int = prefs.getInt(KEY_WINDOW_START, DEFAULT_START_HOUR)
    fun windowEnd(): Int = prefs.getInt(KEY_WINDOW_END, DEFAULT_END_HOUR)

    fun setWindow(startHour: Int, endHour: Int) = prefs.edit()
        .putInt(KEY_WINDOW_START, startHour)
        .putInt(KEY_WINDOW_END, endHour)
        .apply()

    /** 签到成功后是否发通知；失败与连续漏签提醒不受这个开关影响。 */
    fun successNotify(): Boolean = prefs.getBoolean(KEY_SUCCESS_NOTIFY, true)

    fun setSuccessNotify(enabled: Boolean) = prefs.edit().putBoolean(KEY_SUCCESS_NOTIFY, enabled).apply()

    /** 夜间静音（[QUIET_START_HOUR] 点到次日 [QUIET_END_HOUR] 点）：成功通知不弹，失败照常。 */
    fun nightQuiet(): Boolean = prefs.getBoolean(KEY_NIGHT_QUIET, false)

    fun setNightQuiet(enabled: Boolean) = prefs.edit().putBoolean(KEY_NIGHT_QUIET, enabled).apply()

    /**
     * 分享任务开关。签到始终执行，三个分享任务默认关闭：
     * 签到是领自己的奖励，分享是给平台造互动数据，两件事性质不同，默认不该替用户决定。
     */
    fun shareTaskEnabled(id: String): Boolean = prefs.getBoolean(keyTask(id), false)

    fun enabledShares(): Set<String> = SHARE_TASK_IDS.filter { shareTaskEnabled(it) }.toSet()

    fun setShareTaskEnabled(id: String, enabled: Boolean) =
        prefs.edit().putBoolean(keyTask(id), enabled).apply()

    /** 已排定的下一次执行时间，只用于界面展示。 */
    fun nextTrigger(): Long = prefs.getLong(KEY_NEXT_TRIGGER, 0L)

    fun setNextTrigger(ms: Long) = prefs.edit().putLong(KEY_NEXT_TRIGGER, ms).apply()

    /** 今天是否已经跑过（成功或确认无需再跑）。用于避免同一天排第二次、以及在窗口过期时补跑。 */
    fun ranToday(): Boolean = prefs.getInt(KEY_LAST_RUN_DAY, 0) == todayKey()

    fun markRanToday() = prefs.edit().putInt(KEY_LAST_RUN_DAY, todayKey()).apply()

    /** 当天失败后的重试预算，避免夜里失败后每两分钟重试一次、把失败打成高频请求。 */
    fun takeRetry(): Boolean = takeDailyBudget(KEY_RETRY_DAY, KEY_RETRY_COUNT, MAX_RETRIES)

    fun resetRetry() = prefs.edit().putInt(KEY_RETRY_COUNT, 0).apply()

    /**
     * 当晚最后一次补签的预算：窗口结束后才允许用，一天一次。
     * 和 [takeRetry] 分开，是因为它专门给「网络恢复了，但常规重试已经用完」这种情况。
     */
    fun takeLateRetry(): Boolean = takeDailyBudget(KEY_LATE_RETRY_DAY, KEY_LATE_RETRY_COUNT, 1)

    /** 按天计数的预算：跨天自动归零，到上限后返回 false。 */
    private fun takeDailyBudget(dayKey: String, countKey: String, limit: Int): Boolean {
        val today = todayKey()
        val used = if (prefs.getInt(dayKey, 0) == today) prefs.getInt(countKey, 0) else 0
        if (used >= limit) return false
        prefs.edit().putInt(dayKey, today).putInt(countKey, used + 1).apply()
        return true
    }

    private fun todayKey(): Int {
        val calendar = java.util.Calendar.getInstance()
        return calendar.get(java.util.Calendar.YEAR) * 10000 +
            (calendar.get(java.util.Calendar.MONTH) + 1) * 100 +
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
    }

    private fun encrypt(value: String): String? = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val body = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        base64(iv) + ":" + base64(body)
    }.getOrNull()

    private fun decrypt(value: String?): String? = runCatching {
        if (value == null) return null
        val parts = value.split(":")
        if (parts.size != 2) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)),
        )
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun base64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    companion object {
        internal const val PREFS_NAME = "heybox_session"
        private const val ALIAS = "heybox_session_key"
        internal const val KEY_HEYBOX_ID = "heybox_id"
        private const val KEY_PKEY = "pkey"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_TOKEN = "token"
        private const val KEY_MODEL = "model"
        private const val KEY_OS = "os"
        private const val KEY_LAST_RESULT = "last_result"
        internal const val KEY_RUN_RECORD = "run_record"
        private const val KEY_RUN_HISTORY = "run_history"
        internal const val KEY_EXPIRED = "session_expired"
        internal const val KEY_SCHEDULED = "scheduled"
        private const val KEY_WINDOW_START = "window_start"
        private const val KEY_WINDOW_END = "window_end"
        private const val KEY_SUCCESS_NOTIFY = "success_notify"
        private const val KEY_NIGHT_QUIET = "night_quiet"
        internal const val KEY_NEXT_TRIGGER = "next_trigger"
        private const val KEY_LAST_RUN_DAY = "last_run_day"
        private const val KEY_RETRY_DAY = "retry_day"
        private const val KEY_RETRY_COUNT = "retry_count"
        private const val KEY_LATE_RETRY_DAY = "late_retry_day"
        private const val KEY_LATE_RETRY_COUNT = "late_retry_count"
        private const val KEY_APPEARANCE = "appearance"
        private const val KEY_HIDE_RECENT = "hide_recent"
        private const val KEY_NOTICE_VERSION = "notice_version"

        private const val KEY_SKIPPED_VERSION = "skipped_version"
        private const val KEY_PERMISSION_HELP = "permission_help_seen"
        private const val KEY_UPDATE_CHECKED_AT = "update_checked_at"
        private const val KEY_UPDATE_CHECK_OK = "update_check_ok"

        const val MAX_RETRIES = 2

        /** 历史保留天数。一天一条 JSON，一年也就一百多 KB；年度回顾靠它撑住。 */
        const val HISTORY_DAYS = 365
        const val DEFAULT_START_HOUR = 8
        const val DEFAULT_END_HOUR = 20

        /** 夜间静音窗口：22 点到次日 8 点。 */
        const val QUIET_START_HOUR = 22
        const val QUIET_END_HOUR = 8

        /** 静音窗口判定，纯函数方便单测。 */
        fun inQuietHours(nowMillis: Long): Boolean {
            val calendar = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
            val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            return hour >= QUIET_START_HOUR || hour < QUIET_END_HOUR
        }

        val SHARE_TASK_IDS = listOf("1", "19", "31")

        /** 分享任务开关的 prefs key；桌面小组件也要读同一批键。 */
        fun shareTaskKey(id: String) = "task_$id"

        private fun keyTask(id: String) = shareTaskKey(id)

        fun deviceInfo(): DeviceInfo = DeviceInfo(
            model = android.os.Build.MODEL ?: "unknown",
            osVersion = android.os.Build.VERSION.RELEASE ?: "unknown",
        )
    }
}
