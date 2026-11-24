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

    private val prefs = context.applicationContext.getSharedPreferences("heybox_session", Context.MODE_PRIVATE)

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

    fun lastResult(): String? = prefs.getString(KEY_LAST_RESULT, null)

    fun setLastResult(value: String) = prefs.edit().putString(KEY_LAST_RESULT, value).apply()

    fun saveRun(record: RunRecord) {
        prefs.edit().putString(KEY_RUN_RECORD, record.toJson().toString()).apply()
        appendHistory(record)
    }

    fun loadRun(): RunRecord? = runCatching {
        val raw = prefs.getString(KEY_RUN_RECORD, null) ?: return null
        recordFrom(org.json.JSONObject(raw))
    }.getOrNull()

    /** 最近 N 天的记录，按时间倒序；同一天只保留最后一次写入。 */
    fun history(): List<RunRecord> = runCatching {
        val raw = prefs.getString(KEY_RUN_HISTORY, null) ?: return emptyList()
        val array = org.json.JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { recordFrom(it) }
        }
    }.getOrDefault(emptyList())

    private fun appendHistory(record: RunRecord) {
        val day = dayKey(record.at)
        val kept = history().filter { dayKey(it.at) != day }
        val list = (listOf(record) + kept).take(8)
        val array = org.json.JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_RUN_HISTORY, array.toString()).apply()
    }

    private fun RunRecord.toJson(): org.json.JSONObject {
        val tasks = org.json.JSONObject()
        for ((id, state) in taskStates) tasks.put(id, state)
        return org.json.JSONObject().apply {
            put("at", at)
            put("executed", executed)
            put("exp_delta", expDelta)
            put("coin_delta", coinDelta)
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

    private fun recordFrom(json: org.json.JSONObject): RunRecord {
        val tasksJson = json.optJSONObject("tasks")
        val states = buildMap {
            tasksJson?.keys()?.forEach { key -> put(key, tasksJson.optString(key)) }
        }
        return RunRecord(
            at = json.optLong("at"),
            executed = json.optBoolean("executed"),
            expDelta = json.optLong("exp_delta"),
            coinDelta = json.optLong("coin_delta"),
            batteryDelta = if (json.has("battery_delta")) json.optLong("battery_delta") else null,
            exp = json.optLong("exp"),
            coin = json.optLong("coin"),
            battery = if (json.has("battery")) json.optLong("battery") else null,
            level = if (json.has("level")) json.optLong("level") else null,
            maxExp = if (json.has("max_exp")) json.optLong("max_exp") else null,
            signed = json.optBoolean("signed"),
            taskStates = states,
        )
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
    fun takeRetry(): Boolean {
        val today = todayKey()
        val used = if (prefs.getInt(KEY_RETRY_DAY, 0) == today) prefs.getInt(KEY_RETRY_COUNT, 0) else 0
        if (used >= MAX_RETRIES) return false
        prefs.edit().putInt(KEY_RETRY_DAY, today).putInt(KEY_RETRY_COUNT, used + 1).apply()
        return true
    }

    fun resetRetry() = prefs.edit().putInt(KEY_RETRY_COUNT, 0).apply()

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
        private const val ALIAS = "heybox_session_key"
        private const val KEY_HEYBOX_ID = "heybox_id"
        private const val KEY_PKEY = "pkey"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_TOKEN = "token"
        private const val KEY_MODEL = "model"
        private const val KEY_OS = "os"
        private const val KEY_LAST_RESULT = "last_result"
        private const val KEY_RUN_RECORD = "run_record"
        private const val KEY_RUN_HISTORY = "run_history"
        private const val KEY_EXPIRED = "session_expired"
        private const val KEY_SCHEDULED = "scheduled"
        private const val KEY_WINDOW_START = "window_start"
        private const val KEY_WINDOW_END = "window_end"
        private const val KEY_NEXT_TRIGGER = "next_trigger"
        private const val KEY_LAST_RUN_DAY = "last_run_day"
        private const val KEY_RETRY_DAY = "retry_day"
        private const val KEY_RETRY_COUNT = "retry_count"
        private const val KEY_APPEARANCE = "appearance"
        private const val KEY_NOTICE_VERSION = "notice_version"

        const val MAX_RETRIES = 2
        const val DEFAULT_START_HOUR = 8
        const val DEFAULT_END_HOUR = 20

        val SHARE_TASK_IDS = listOf("1", "19", "31")

        private fun keyTask(id: String) = "task_$id"

        fun deviceInfo(): DeviceInfo = DeviceInfo(
            model = android.os.Build.MODEL ?: "unknown",
            osVersion = android.os.Build.VERSION.RELEASE ?: "unknown",
        )
    }
}
