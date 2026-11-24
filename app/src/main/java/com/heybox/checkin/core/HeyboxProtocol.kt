package com.heybox.checkin.core

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** 登录用 RSA 公钥（对应 APP NDKTools.getrsakey）。分享上报复用同一把。 */
private const val PUBLIC_KEY = """-----BEGIN PUBLIC KEY-----
MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDZgjVwAiKTjZ55nG+mW6r3TSU4
ECvNYqDMIS/bhCj2QaH5GI/KZb2TBp+CBvUj9SLFnmJQ0kzHzHoGZCQ88VevCffF
7JePGF9cmKQqotlfTKbV4oxV5iLz7JSG6b/Vg7AXtrTolNtWsa8HiB0tI0YClYaQ
lOXm4UxLeSxQwSFETwIDAQAB
-----END PUBLIC KEY-----"""

private const val AES_IV = "abcdefghijklmnop"

data class AppInfo(
    val version: String = HeyboxSign.APP_VERSION,
    val build: String = HeyboxSign.APP_BUILD,
    val shaderVersion: String = HeyboxSign.SHADER_VERSION,
)

data class DeviceInfo(val model: String, val osVersion: String)

data class SessionInfo(
    val heyboxId: String,
    val pkey: String,
    val deviceId: String,
    val tokenId: String = "",
)

data class HeyboxConfig(
    val session: SessionInfo,
    val device: DeviceInfo,
    val app: AppInfo = AppInfo(),
)

data class PreparedRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: String? = null,
)

object HeyboxCrypto {

    val random = SecureRandom()

    /** 与 APP 一致：每 76 字符换行，末尾补一个换行。 */
    fun androidBase64(bytes: ByteArray): String {
        val b64 = java.util.Base64.getEncoder().encodeToString(bytes)
        if (b64.isEmpty()) return ""
        val sb = StringBuilder()
        var i = 0
        while (i < b64.length) {
            sb.append(b64, i, minOf(i + 76, b64.length))
            sb.append('\n')
            i += 76
        }
        return sb.toString()
    }

    fun rsaEncryptBase64(publicKeyPem: String, plain: ByteArray): String {
        val der = publicKeyPem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace(Regex("\\s"), "")
        val spec = java.security.spec.X509EncodedKeySpec(java.util.Base64.getDecoder().decode(der))
        val key = java.security.KeyFactory.getInstance("RSA").generatePublic(spec)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return androidBase64(cipher.doFinal(plain))
    }

    fun encryptPhone(phone: String): String =
        rsaEncryptBase64(PUBLIC_KEY, phone.toByteArray(Charsets.UTF_8))

    data class ReportPayload(val data: String, val key: String, val sid: String, val time: String)

    /**
     * 上报体：8 字节随机 key（hex 当 AES 密钥）→ gzip → AES-128-CBC → RSA 包裹 key。
     * 顺序必须是先压缩再加密。
     */
    fun encryptReport(eventsJson: String, time: String = (System.currentTimeMillis() / 1000).toString()): ReportPayload {
        val aesKey = ByteArray(8).also { random.nextBytes(it) }.toHexLower()
        return encryptReportWithKey(eventsJson, aesKey, time)
    }

    fun encryptReportWithKey(eventsJson: String, aesKey: String, time: String): ReportPayload {
        val gzipped = gzip(eventsJson.toByteArray(Charsets.UTF_8))
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(aesKey.toByteArray(Charsets.US_ASCII), "AES"),
            IvParameterSpec(AES_IV.toByteArray(Charsets.US_ASCII)),
        )
        val data = androidBase64(cipher.doFinal(gzipped))
        val key = rsaEncryptBase64(PUBLIC_KEY, aesKey.toByteArray(Charsets.US_ASCII))
        return ReportPayload(data = data, key = key, sid = md5Hex(key + time) + md5Hex(data), time = time)
    }

    fun decryptReportForTest(dataB64: String, aesKeyHex: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(aesKeyHex.toByteArray(Charsets.US_ASCII), "AES"),
            IvParameterSpec(AES_IV.toByteArray(Charsets.US_ASCII)),
        )
        val plain = cipher.doFinal(java.util.Base64.getMimeDecoder().decode(dataB64))
        return GZIPInputStream(plain.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun gzip(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(input) }
        return out.toByteArray()
    }

    fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8)).toHexLower()

    private fun ByteArray.toHexLower(): String = joinToString("") { "%02x".format(it) }
}

interface Transport {
    fun execute(request: PreparedRequest): String
}

object HttpTransport : Transport {
    override fun execute(request: PreparedRequest): String {
        val conn = URL(request.url).openConnection() as HttpURLConnection
        conn.requestMethod = if (request.body == null) "GET" else "POST"
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        for ((k, v) in request.headers) conn.setRequestProperty(k, v)
        if (request.body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
        conn.disconnect()
        if (code !in 200..299) throw HeyboxHttpException(code, text.take(200))
        return text
    }
}

class HeyboxHttpException(val status: Int, val body: String) : Exception("HTTP $status: $body")

/** 接口返回 status 不是 ok；captcha 单独标记，便于 UI 给出明确提示。 */
class HeyboxApiException(
    val endpoint: String,
    val status: String,
    val upstreamMessage: String,
) : Exception(messageFor(status, upstreamMessage)) {
    val captchaRequired: Boolean get() = status == "show_captcha"
    val rateLimited: Boolean get() = Regex("频繁|次数.*限制").containsMatchIn(upstreamMessage)

    companion object {
        private fun messageFor(status: String, detail: String): String = when {
            status == "show_captcha" -> "小黑盒要求完成人机验证后才能继续登录"
            detail.contains("验证码") && (detail.contains("过期") || detail.contains("失效")) -> "验证码已过期，请重新获取"
            Regex("频繁|次数.*限制").containsMatchIn(detail) -> "请求过于频繁，请稍后再试"
            detail.contains("验证码") && (detail.contains("错误") || detail.contains("不正确")) -> "验证码不正确，请重新输入"
            else -> "接口未成功（$status）"
        }
    }
}

class SessionExpiredException : Exception("登录已过期，请重新登录")
class DeviceTokenInvalidException : Exception("设备令牌无效，请重新登录")

class HeyboxClient(private val config: HeyboxConfig, private val transport: Transport = HttpTransport) {

    fun prepareNative(
        endpoint: String,
        extra: Map<String, String> = emptyMap(),
        body: Map<String, String>? = null,
    ): PreparedRequest {
        val sign = HeyboxSign.nativeSignature(
            path = HeyboxSign.normalizePath(endpoint),
            timeSeconds = System.currentTimeMillis() / 1000,
            debugInfo = config.session.deviceId + config.session.heyboxId,
            osVersion = config.device.osVersion,
            version = config.app.version,
        )
        val params = linkedMapOf(
            "heybox_id" to config.session.heyboxId,
            "imei" to config.session.deviceId,
            "device_info" to config.device.model,
            "os_type" to "Android",
            "x_os_type" to "Android",
            "x_client_type" to "mobile",
            "os_version" to config.device.osVersion,
            "version" to config.app.version,
            "build" to config.app.build,
            "dw" to "360",
            "channel" to "heybox",
            "x_app" to "heybox",
            "time_zone" to "Asia/Shanghai",
        )
        params.putAll(extra)
        params["hkey"] = sign.hkey
        params["_time"] = sign.time
        params["nonce"] = sign.nonce
        params["_rnd"] = sign.rnd

        val headers = mutableMapOf(
            "User-Agent" to "Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2272.118 Safari/537.36 ApiMaxJia/1.0",
            "Accept" to "application/json",
            "Referer" to "http://api.maxjia.com/",
            "Cookie" to "pkey=${config.session.pkey}; x_xhh_tokenid=${config.session.tokenId}",
        )
        if (body != null) headers["Content-Type"] = "application/x-www-form-urlencoded"
        return PreparedRequest("https://api.xiaoheihe.cn$endpoint?${encode(params)}", headers, body?.let { encode(it) })
    }

    fun prepareWeb(
        endpoint: String,
        extra: Map<String, String> = emptyMap(),
        host: String = "api.xiaoheihe.cn",
        body: Map<String, String>? = null,
    ): PreparedRequest {
        val sign = HeyboxSign.webSignature(endpoint)
        val params = linkedMapOf(
            "app" to "heybox",
            "os_type" to "web",
            "x_app" to "heybox",
            "x_client_type" to "web",
            "x_os_type" to "Android",
            "x_client_version" to config.app.version,
            "version" to "999.0.4",
            "heybox_id" to config.session.heyboxId,
        )
        params.putAll(extra)
        params["hkey"] = sign.hkey
        params["_time"] = sign.time
        params["nonce"] = sign.nonce

        val headers = mutableMapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android ${config.device.osVersion}; ${config.device.model}; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/143.0.7499.192 Mobile Safari/537.36",
            "Accept" to "application/json, text/plain, */*",
            "Origin" to "https://web.xiaoheihe.cn",
            "Referer" to "https://web.xiaoheihe.cn/",
            "X-Requested-With" to "com.max.xiaoheihe",
            "Cookie" to "pkey=${config.session.pkey};x_xhh_tokenid=${config.session.tokenId};x_pkey=${config.session.pkey};x_heybox_id=${config.session.heyboxId}",
        )
        if (body != null) headers["Content-Type"] = "application/x-www-form-urlencoded;charset=UTF-8"
        return PreparedRequest("https://$host$endpoint?${encode(params)}", headers, body?.let { encode(it) })
    }

    fun native(endpoint: String, extra: Map<String, String> = emptyMap(), body: Map<String, String>? = null): String =
        transport.execute(prepareNative(endpoint, extra, body))

    fun web(
        endpoint: String,
        extra: Map<String, String> = emptyMap(),
        host: String = "api.xiaoheihe.cn",
        body: Map<String, String>? = null,
    ): String = transport.execute(prepareWeb(endpoint, extra, host, body))

    fun tasks(): String = web("/task/list_v2/")
    fun signState(): String = native("/task/sign_v4/get_sign_state")
    fun sign(): String = native("/task/sign_v3/sign")

    fun report(sessionId: String, eventsJson: String): String {
        val payload = HeyboxCrypto.encryptReport(eventsJson)
        return web(
            "/account/data_report/",
            mapOf("type" to "104", "time_" to payload.time, "session_id" to sessionId),
            host = "data.xiaoheihe.cn",
            body = mapOf("data" to payload.data, "key" to payload.key, "sid" to payload.sid),
        )
    }

    /** 解析统一响应体，status 不 ok 时抛异常。 */
    fun unwrap(endpoint: String, raw: String): org.json.JSONObject {
        val root = org.json.JSONObject(raw)
        val status = root.optString("status", "unknown")
        when (status) {
            "ok" -> return root.optJSONObject("result") ?: org.json.JSONObject()
            "relogin" -> throw SessionExpiredException()
            "lack_token" -> throw DeviceTokenInvalidException()
            else -> throw HeyboxApiException(endpoint, status, root.optString("msg", ""))
        }
    }

    fun tasksResult(): org.json.JSONObject = unwrap("/task/list_v2/", tasks())
    fun signStateResult(): org.json.JSONObject = unwrap("/task/sign_v4/get_sign_state", signState())
    fun signResult(): org.json.JSONObject = unwrap("/task/sign_v3/sign", sign())
    fun webResult(endpoint: String, extra: Map<String, String> = emptyMap()): org.json.JSONObject =
        unwrap(endpoint, web(endpoint, extra))
    fun reportResult(sessionId: String, eventsJson: String): org.json.JSONObject =
        unwrap("/account/data_report/", report(sessionId, eventsJson))

    private fun encode(params: Map<String, String>): String =
        params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
}

object LoginApi {

    fun pendingConfig(
        deviceModel: String,
        osVersion: String,
        token: String,
        deviceId: String = java.util.UUID.randomUUID().toString(),
    ): HeyboxConfig = HeyboxConfig(
        session = SessionInfo(heyboxId = "-1", pkey = "", deviceId = deviceId, tokenId = token),
        device = DeviceInfo(model = deviceModel, osVersion = osVersion),
    )

    fun normalizePhone(phone: String): String {
        val trimmed = phone.trim()
        if (Regex("^1[3-9]\\d{9}$").matches(trimmed)) return "+86$trimmed"
        if (Regex("^\\+[1-9]\\d{7,14}$").matches(trimmed)) return trimmed
        throw IllegalArgumentException("手机号格式不正确，海外号码请包含 + 和国家区号")
    }

    fun sendCode(
        client: HeyboxClient,
        phone: String,
        captchaTicket: String = "",
        captchaRandstr: String = "",
    ): String {
        val extra = if (captchaTicket.isEmpty()) emptyMap() else mapOf(
            "captcha_type" to "confirm",
            "ticket" to captchaTicket,
            "randstr" to captchaRandstr,
        )
        return client.native(
            "/account/get_login_code/",
            extra,
            mapOf("phone_num" to HeyboxCrypto.encryptPhone(normalizePhone(phone))),
        )
    }

    fun verifyCode(
        client: HeyboxClient,
        phone: String,
        code: String,
        captchaTicket: String = "",
        captchaRandstr: String = "",
    ): String {
        require(Regex("^\\d{4,8}$").matches(code)) { "验证码应为 4 至 8 位数字" }
        val extra = linkedMapOf("code" to code, "is_new_device" to "1")
        if (captchaTicket.isNotEmpty()) {
            extra["captcha_type"] = "confirm"
            extra["ticket"] = captchaTicket
            extra["randstr"] = captchaRandstr
        }
        return client.native(
            "/account/login_code/",
            extra,
            mapOf("phone_num" to HeyboxCrypto.encryptPhone(normalizePhone(phone))),
        )
    }
}
